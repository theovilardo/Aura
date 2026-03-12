package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.MemorySlot
import com.theveloper.aura.domain.repository.MemoryRepository
import com.theveloper.aura.engine.dsl.TaskDSLValidator
import com.theveloper.aura.engine.llm.LLMServiceFactory
import com.theveloper.aura.engine.memory.MemoryContextBuilder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskClassifier @Inject constructor(
    private val entityExtractorService: EntityExtractorService,
    private val intentClassifier: IntentClassifier,
    private val onDeviceTaskDslService: OnDeviceTaskDslService,
    private val llmServiceFactory: LLMServiceFactory,
    private val aiExecutionModeStore: AiExecutionModeStore,
    private val completenessValidator: CompletenessValidator,
    private val memoryRepository: MemoryRepository,
    private val memoryContextBuilder: MemoryContextBuilder
) {

    suspend fun classify(
        input: String,
        allowClarification: Boolean = true
    ): TaskGenerationResult {
        val normalizedInput = input.trim()
        require(normalizedInput.isNotBlank()) { "El texto de la tarea no puede estar vacio." }

        val extractedEntities = entityExtractorService.extract(normalizedInput)
        val intentResult = intentClassifier.classify(normalizedInput)
        val memorySlots = memoryRepository.getSlots()
        val context = LLMClassificationContext(
            intentHint = intentResult.taskType,
            intentConfidence = intentResult.confidence,
            extractedDates = extractedEntities.dateTimes,
            extractedNumbers = extractedEntities.numbers,
            extractedLocations = extractedEntities.locations,
            memoryContext = memoryContextBuilder.buildContextForClassifier(
                detectedType = intentResult.taskType,
                memorySlots = memorySlots
            )
        )
        val executionMode = aiExecutionModeStore.getMode()
        val warnings = mutableListOf<String>()
        val route = llmServiceFactory.resolvePrimaryService(executionMode)

        if (route.reason.isNotBlank() && route.source == TaskGenerationSource.RULES) {
            warnings += route.reason
        }

        buildRouteResult(
            input = normalizedInput,
            context = context,
            executionMode = executionMode,
            route = route,
            intentResult = intentResult,
            extractedEntities = extractedEntities,
            warnings = warnings,
            memorySlots = memorySlots,
            allowClarification = allowClarification
        )?.let { return it }

        return buildFallbackResult(
            input = normalizedInput,
            context = context,
            executionMode = executionMode,
            intentResult = intentResult,
            extractedEntities = extractedEntities,
            warnings = warnings,
            memorySlots = memorySlots,
            allowClarification = allowClarification
        )
    }

    private suspend fun buildRouteResult(
        input: String,
        context: LLMClassificationContext,
        executionMode: AiExecutionMode,
        route: com.theveloper.aura.engine.llm.ResolvedLLMRoute,
        intentResult: IntentResult,
        extractedEntities: ExtractedEntities,
        warnings: MutableList<String>,
        memorySlots: List<MemorySlot>,
        allowClarification: Boolean
    ): TaskGenerationResult? {
        val routedDsl = runCatching {
            route.service.classify(input, context)
        }.getOrElse {
            warnings += when (route.source) {
                TaskGenerationSource.GROQ_API -> "Groq no respondió bien. Se usó la ruta local."
                TaskGenerationSource.LOCAL_AI -> "El modelo local no respondió bien. Se usó la ruta heurística."
                TaskGenerationSource.RULES,
                TaskGenerationSource.MANUAL -> "La ruta configurada falló. Se usó la composición local."
            }
            return null
        }

        return when (val validation = TaskDSLValidator.validate(routedDsl)) {
            TaskDSLValidator.ValidationResult.Valid -> {
                postProcess(
                    result = TaskGenerationResult(
                        dsl = routedDsl,
                        source = route.source,
                        executionMode = executionMode,
                        intentConfidence = intentResult.confidence,
                        localConfidence = route.source.confidenceHint(intentResult.confidence),
                        warnings = warnings.distinct()
                    ),
                    input = input,
                    memorySlots = memorySlots,
                    allowClarification = allowClarification
                )
            }

            is TaskDSLValidator.ValidationResult.Invalid -> {
                warnings += "La capa de inteligencia devolvió una estructura inválida. Se usó la composición local."
                buildFallbackResult(
                    input = input,
                    context = context,
                    executionMode = executionMode,
                    intentResult = intentResult,
                    extractedEntities = extractedEntities,
                    warnings = warnings,
                    memorySlots = memorySlots,
                    allowClarification = allowClarification
                )
            }
        }
    }

    private suspend fun buildFallbackResult(
        input: String,
        context: LLMClassificationContext,
        executionMode: AiExecutionMode,
        intentResult: IntentResult,
        extractedEntities: ExtractedEntities,
        warnings: MutableList<String>,
        memorySlots: List<MemorySlot>,
        allowClarification: Boolean
    ): TaskGenerationResult {
        val onDeviceResult = runCatching {
            onDeviceTaskDslService.compose(
                OnDeviceTaskDslRequest(
                    input = input,
                    intentResult = intentResult,
                    extractedEntities = extractedEntities,
                    llmContext = context
                )
            )
        }.getOrElse {
            warnings += "La composición heurística falló. Se usó el preset determinístico."
            OnDeviceTaskDslResult(
                dsl = TaskDSLBuilder.buildDeterministic(input, intentResult, extractedEntities),
                confidence = intentResult.confidence.coerceAtLeast(0.42f),
                source = TaskGenerationSource.RULES
            )
        }

        val fallbackDsl = when (TaskDSLValidator.validate(onDeviceResult.dsl)) {
            TaskDSLValidator.ValidationResult.Valid -> {
                onDeviceResult.dsl
            }

            is TaskDSLValidator.ValidationResult.Invalid -> {
                warnings += "La composición heurística no fue válida. Se usó el preset determinístico."
                TaskDSLBuilder.buildDeterministic(input, intentResult, extractedEntities)
            }
        }

        return postProcess(
            result = TaskGenerationResult(
                dsl = fallbackDsl,
                source = if (fallbackDsl == onDeviceResult.dsl) onDeviceResult.source else TaskGenerationSource.RULES,
                executionMode = executionMode,
                intentConfidence = intentResult.confidence,
                localConfidence = onDeviceResult.confidence,
                warnings = warnings.distinct()
            ),
            input = input,
            memorySlots = memorySlots,
            allowClarification = allowClarification
        )
    }

    private fun postProcess(
        result: TaskGenerationResult,
        input: String,
        memorySlots: List<MemorySlot>,
        allowClarification: Boolean
    ): TaskGenerationResult {
        val completeness = completenessValidator.enrich(
            input = input,
            dsl = result.dsl,
            memorySlots = memorySlots
        )
        val nonBlockerWarnings = completeness.check.missingFields
            .filterNot { it.isBlocker }
            .map { it.question }
        val fallbackWarning = if (!allowClarification && completeness.clarification != null) {
            listOf("Se creo la estructura con algunos campos sin definir.")
        } else {
            emptyList()
        }

        return result.copy(
            dsl = completeness.dsl,
            warnings = (result.warnings + nonBlockerWarnings + fallbackWarning).distinct(),
            completenessCheck = completeness.check,
            clarification = completeness.clarification.takeIf { allowClarification }
        )
    }

    private fun TaskGenerationSource.confidenceHint(intentConfidence: Float): Float {
        return when (this) {
            TaskGenerationSource.GROQ_API -> 0.92f
            TaskGenerationSource.LOCAL_AI -> 0.86f
            TaskGenerationSource.RULES -> intentConfidence.coerceAtLeast(0.64f)
            TaskGenerationSource.MANUAL -> 1f
        }
    }
}

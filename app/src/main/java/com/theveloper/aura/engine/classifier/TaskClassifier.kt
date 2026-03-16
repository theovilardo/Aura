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
        require(normalizedInput.isNotBlank()) { "Task text cannot be empty." }

        val extractedEntities = entityExtractorService.extract(normalizedInput)
        val intentResult = intentClassifier.classify(normalizedInput)
        val memorySlots = memoryRepository.getSlots()
        val context = LLMClassificationContext(
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

        // Primary LLM failed — try the advanced model before falling to heuristics.
        // This gives the larger model (e.g. Qwen 2.5 1.5B) a chance to handle inputs
        // that the smaller primary model (e.g. Qwen 3 0.6B) could not parse.
        val advancedRoute = llmServiceFactory.resolveAdvancedService(executionMode)
        if (advancedRoute.service !== route.service && advancedRoute.source != TaskGenerationSource.RULES) {
            buildRouteResult(
                input = normalizedInput,
                context = context,
                executionMode = executionMode,
                route = advancedRoute,
                intentResult = intentResult,
                extractedEntities = extractedEntities,
                warnings = warnings,
                memorySlots = memorySlots,
                allowClarification = allowClarification
            )?.let { return it }
        }

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
                TaskGenerationSource.GROQ_API -> "Groq did not respond correctly. Local route was used."
                TaskGenerationSource.LOCAL_AI -> "Local model did not respond correctly. Heuristic route was used."
                TaskGenerationSource.RULES,
                TaskGenerationSource.MANUAL -> "Configured route failed. Local composition was used."
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
                warnings += "The intelligence layer returned an invalid structure. Local composition was used."
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
            warnings += "Heuristic composition failed. Deterministic preset was used."
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
                warnings += "Heuristic composition was invalid. Deterministic preset was used."
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
        val fallbackWarning = if (!allowClarification && completeness.clarifications.isNotEmpty()) {
            listOf("Some fields were left undefined. You can complete them later.")
        } else {
            emptyList()
        }

        return result.copy(
            dsl = completeness.dsl,
            warnings = (result.warnings + nonBlockerWarnings + fallbackWarning).distinct(),
            completenessCheck = completeness.check,
            clarifications = if (allowClarification) completeness.clarifications else emptyList()
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

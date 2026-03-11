package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.TaskDSLValidator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskClassifier @Inject constructor(
    private val entityExtractorService: EntityExtractorService,
    private val intentClassifier: IntentClassifier,
    private val onDeviceTaskDslService: OnDeviceTaskDslService,
    private val llmService: LLMService,
    private val aiExecutionModeStore: AiExecutionModeStore
) {

    suspend fun classify(input: String): TaskGenerationResult {
        val normalizedInput = input.trim()
        require(normalizedInput.isNotBlank()) { "El texto de la tarea no puede estar vacio." }

        val extractedEntities = entityExtractorService.extract(normalizedInput)
        val intentResult = intentClassifier.classify(normalizedInput)
        val context = LLMClassificationContext(
            intentHint = intentResult.taskType,
            intentConfidence = intentResult.confidence,
            extractedDates = extractedEntities.dateTimes,
            extractedNumbers = extractedEntities.numbers,
            extractedLocations = extractedEntities.locations
        )
        val executionMode = aiExecutionModeStore.getMode()
        val warnings = mutableListOf<String>()
        val cloudAvailable = llmService.isAvailable()
        val cloudEnabled = executionMode != AiExecutionMode.LOCAL_ONLY

        val localResult = buildLocalResult(
            input = normalizedInput,
            intentResult = intentResult,
            extractedEntities = extractedEntities,
            context = context,
            executionMode = executionMode,
            warnings = warnings
        )

        if (executionMode == AiExecutionMode.CLOUD_FIRST) {
            if (cloudAvailable) {
                buildCloudResult(
                    input = normalizedInput,
                    context = context,
                    executionMode = executionMode,
                    intentConfidence = intentResult.confidence,
                    localConfidence = localResult?.localConfidence ?: 0f,
                    warnings = warnings
                )?.let { return it }
            } else {
                warnings += CLOUD_NOT_CONFIGURED_WARNING
            }
        }

        val shouldEscalate = cloudEnabled && cloudAvailable && shouldEscalateToCloud(
            input = normalizedInput,
            intentResult = intentResult,
            localResult = localResult
        )

        if (shouldEscalate) {
            buildCloudResult(
                input = normalizedInput,
                context = context,
                executionMode = executionMode,
                intentConfidence = intentResult.confidence,
                localConfidence = localResult?.localConfidence ?: 0f,
                warnings = warnings
            )?.let { return it }
        } else if (cloudEnabled && !cloudAvailable) {
            warnings += CLOUD_NOT_CONFIGURED_WARNING
        }

        localResult?.let { result ->
            if (warnings.isEmpty()) {
                return result
            }
            return result.copy(warnings = (result.warnings + warnings).distinct())
        }

        error("No pudimos generar una UI valida para esta tarea.")
    }

    private suspend fun buildLocalResult(
        input: String,
        intentResult: IntentResult,
        extractedEntities: ExtractedEntities,
        context: LLMClassificationContext,
        executionMode: AiExecutionMode,
        warnings: MutableList<String>
    ): TaskGenerationResult? {
        val onDeviceResult = runCatching {
            onDeviceTaskDslService.compose(
                OnDeviceTaskDslRequest(
                    input = input,
                    intentResult = intentResult,
                    extractedEntities = extractedEntities,
                    llmContext = context
                )
            )
        }.getOrElse { error ->
            warnings += "La IA local tuvo un problema. Se uso el preset deterministico."
            OnDeviceTaskDslResult(
                dsl = TaskDSLBuilder.buildDeterministic(input, intentResult, extractedEntities),
                confidence = intentResult.confidence.coerceAtLeast(0.42f),
                source = TaskGenerationSource.RULES
            )
        }

        return when (val validation = TaskDSLValidator.validate(onDeviceResult.dsl)) {
            TaskDSLValidator.ValidationResult.Valid -> {
                TaskGenerationResult(
                    dsl = onDeviceResult.dsl,
                    source = onDeviceResult.source,
                    executionMode = executionMode,
                    intentConfidence = intentResult.confidence,
                    localConfidence = onDeviceResult.confidence,
                    warnings = emptyList()
                )
            }
            is TaskDSLValidator.ValidationResult.Invalid -> {
                warnings += "La composicion local no fue valida. Se intento una ruta mas segura."
                val deterministic = TaskDSLBuilder.buildDeterministic(input, intentResult, extractedEntities)
                when (TaskDSLValidator.validate(deterministic)) {
                    TaskDSLValidator.ValidationResult.Valid -> {
                        TaskGenerationResult(
                            dsl = deterministic,
                            source = TaskGenerationSource.RULES,
                            executionMode = executionMode,
                            intentConfidence = intentResult.confidence,
                            localConfidence = onDeviceResult.confidence,
                            warnings = emptyList()
                        )
                    }
                    is TaskDSLValidator.ValidationResult.Invalid -> null
                }
            }
        }
    }

    private suspend fun buildCloudResult(
        input: String,
        context: LLMClassificationContext,
        executionMode: AiExecutionMode,
        intentConfidence: Float,
        localConfidence: Float,
        warnings: MutableList<String>
    ): TaskGenerationResult? {
        val remoteDsl = runCatching { llmService.classify(input, context) }
            .getOrElse {
                warnings += "Groq no respondio bien. Se mantuvo la IA local."
                return null
            }

        return when (val validation = TaskDSLValidator.validate(remoteDsl)) {
            TaskDSLValidator.ValidationResult.Valid -> {
                TaskGenerationResult(
                    dsl = remoteDsl,
                    source = TaskGenerationSource.GROQ_API,
                    executionMode = executionMode,
                    intentConfidence = intentConfidence,
                    localConfidence = localConfidence,
                    warnings = warnings.distinct()
                )
            }
            is TaskDSLValidator.ValidationResult.Invalid -> {
                warnings += "Groq devolvio una estructura invalida. Se mantuvo la IA local."
                null
            }
        }
    }

    private fun shouldEscalateToCloud(
        input: String,
        intentResult: IntentResult,
        localResult: TaskGenerationResult?
    ): Boolean {
        if (localResult == null) {
            return true
        }
        if (localResult.source == TaskGenerationSource.GROQ_API) {
            return false
        }

        val normalized = input.lowercase()
        val complexPrompt = COMPLEX_PROMPT_REGEX.containsMatchIn(normalized)
        val localDsl = localResult.dsl
        val localLooksThin = localDsl.components.size <= 1
        val genericThinResult = localDsl.type == TaskType.GENERAL && localLooksThin

        return intentResult.confidence < INTENT_CONFIDENCE_THRESHOLD ||
            localResult.localConfidence < LOCAL_CONFIDENCE_THRESHOLD ||
            (complexPrompt && localLooksThin) ||
            genericThinResult
    }

    private companion object {
        const val INTENT_CONFIDENCE_THRESHOLD = 0.58f
        const val LOCAL_CONFIDENCE_THRESHOLD = 0.62f
        const val CLOUD_NOT_CONFIGURED_WARNING = "Groq no esta configurado. Se uso la IA local."
        val COMPLEX_PROMPT_REGEX = Regex(
            "\\bdashboard\\b|\\bflujo\\b|\\bcomparar\\b|\\bestrategia\\b|\\bitinerario\\b|\\bpresupuesto\\b|\\bplan\\s+completo\\b|\\broadmap\\b",
            RegexOption.IGNORE_CASE
        )
    }
}

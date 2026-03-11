package com.theveloper.aura.engine.classifier

import com.theveloper.aura.engine.dsl.TaskDSLValidator
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskClassifier @Inject constructor(
    private val entityExtractorService: EntityExtractorService,
    private val intentClassifier: IntentClassifier,
    private val llmService: LLMService
) {

    suspend fun classify(input: String): TaskDSLOutput {
        val normalizedInput = input.trim()
        require(normalizedInput.isNotBlank()) { "El texto de la tarea no puede estar vacío." }

        val extractedEntities = entityExtractorService.extract(normalizedInput)
        val intentResult = intentClassifier.classify(normalizedInput)
        val context = LLMClassificationContext(
            intentHint = intentResult.taskType,
            intentConfidence = intentResult.confidence,
            extractedDates = extractedEntities.dateTimes,
            extractedNumbers = extractedEntities.numbers,
            extractedLocations = extractedEntities.locations
        )

        val initialResult = if (intentResult.confidence >= CONFIDENCE_THRESHOLD) {
            TaskDSLBuilder.buildDeterministic(normalizedInput, intentResult, extractedEntities)
        } else {
            llmService.classify(normalizedInput, context)
        }

        return when (val validation = TaskDSLValidator.validate(initialResult)) {
            TaskDSLValidator.ValidationResult.Valid -> initialResult
            is TaskDSLValidator.ValidationResult.Invalid -> {
                if (intentResult.confidence >= CONFIDENCE_THRESHOLD) {
                    val fallbackResult = llmService.classify(normalizedInput, context)
                    when (val fallbackValidation = TaskDSLValidator.validate(fallbackResult)) {
                        TaskDSLValidator.ValidationResult.Valid -> fallbackResult
                        is TaskDSLValidator.ValidationResult.Invalid -> {
                            error(fallbackValidation.reason)
                        }
                    }
                } else {
                    error(validation.reason)
                }
            }
        }
    }

    private companion object {
        const val CONFIDENCE_THRESHOLD = 0.85f
    }
}

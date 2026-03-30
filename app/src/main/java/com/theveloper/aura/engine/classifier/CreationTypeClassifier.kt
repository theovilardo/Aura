package com.theveloper.aura.engine.classifier

import android.util.Log
import com.theveloper.aura.domain.model.CreationType
import com.theveloper.aura.engine.dsl.CreationDSLResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Top-level orchestrator that routes to the correct sub-classifier
 * based on the [CreationType] chosen in the bottom bar.
 *
 * - [CreationType.TASK] delegates to the existing [TaskClassifier] (untouched).
 * - [CreationType.REMINDER] / [CreationType.AUTOMATION] / [CreationType.EVENT]
 *   each have their own dedicated classifier.
 * - [CreationType.SYSTEM] opens the SystemPanel UI — no classification needed.
 */
@Singleton
class CreationTypeClassifier @Inject constructor(
    private val taskClassifier: TaskClassifier,
    private val reminderClassifier: ReminderClassifier,
    private val automationClassifier: AutomationClassifier,
    private val eventClassifier: EventClassifier
) {

    /**
     * Classify [input] according to the given [creationType].
     *
     * @return a [CreationClassificationResult] wrapping the typed DSL output,
     *         generation source, and optional clarifications.
     */
    suspend fun classify(
        input: String,
        creationType: CreationType,
        allowClarification: Boolean = true
    ): CreationClassificationResult {
        Log.d(TAG, "classify(type=$creationType, input=${input.take(60)}…)")

        return when (creationType) {
            CreationType.TASK -> {
                val result = taskClassifier.classify(input, allowClarification)
                CreationClassificationResult(
                    dslResult = CreationDSLResult.TaskResult(result.dsl),
                    source = result.source,
                    warnings = result.warnings,
                    clarifications = result.clarifications
                )
            }

            CreationType.REMINDER -> {
                val result = reminderClassifier.classify(input)
                CreationClassificationResult(
                    dslResult = CreationDSLResult.ReminderResult(result.dsl),
                    source = result.source,
                    warnings = result.warnings
                )
            }

            CreationType.AUTOMATION -> {
                val result = automationClassifier.classify(input)
                CreationClassificationResult(
                    dslResult = CreationDSLResult.AutomationResult(result.dsl),
                    source = result.source,
                    warnings = result.warnings
                )
            }

            CreationType.EVENT -> {
                val result = eventClassifier.classify(input)
                CreationClassificationResult(
                    dslResult = CreationDSLResult.EventResult(result.dsl),
                    source = result.source,
                    warnings = result.warnings
                )
            }

            CreationType.SYSTEM -> {
                // System panel — no classification. This should not be called.
                throw IllegalArgumentException("SYSTEM type does not require classification")
            }
        }
    }

    companion object {
        private const val TAG = "CreationTypeClassifier"
    }
}

/**
 * Unified result returned by [CreationTypeClassifier.classify].
 */
data class CreationClassificationResult(
    val dslResult: CreationDSLResult,
    val source: TaskGenerationSource,
    val warnings: List<String> = emptyList(),
    val clarifications: List<ClarificationRequest> = emptyList()
)

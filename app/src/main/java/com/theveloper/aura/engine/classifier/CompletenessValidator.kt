package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.MemorySlot
import com.theveloper.aura.engine.dsl.ChecklistDslItems
import com.theveloper.aura.engine.dsl.ChecklistItemDSL
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class CompletenessCheck(
    val isComplete: Boolean,
    val missingFields: List<MissingField> = emptyList()
)

data class MissingField(
    val componentType: ComponentType,
    val fieldName: String,
    val question: String,
    val isBlocker: Boolean
)

data class ClarificationRequest(
    val componentType: ComponentType,
    val fieldName: String,
    val question: String,
    val skipLabel: String = ""
)

data class CompletenessValidationResult(
    val dsl: TaskDSLOutput,
    val check: CompletenessCheck,
    val clarifications: List<ClarificationRequest> = emptyList()
) {
    /** Convenience accessor for the first pending question (backward compat). */
    val clarification: ClarificationRequest? get() = clarifications.firstOrNull()
}

@Singleton
class CompletenessValidator @Inject constructor() {

    fun enrich(
        input: String,
        dsl: TaskDSLOutput,
        memorySlots: List<MemorySlot> = emptyList()
    ): CompletenessValidationResult {
        val missingFields = mutableListOf<MissingField>()
        val updatedComponents = dsl.components.map { component ->
            when (component.type) {
                ComponentType.CHECKLIST     -> enrichChecklist(component, input, missingFields)
                ComponentType.COUNTDOWN     -> enrichCountdown(component, missingFields)
                ComponentType.METRIC_TRACKER -> enrichMetricTracker(component, missingFields)
                ComponentType.HABIT_RING    -> enrichHabitRing(component, missingFields)
                else -> component.copy(needsClarification = false)
            }
        }

        val resolvedTargetDate = updatedComponents
            .firstOrNull { it.type == ComponentType.COUNTDOWN }
            ?.config
            ?.get("targetDate")
            ?.jsonPrimitive
            ?.longOrNull
            ?.takeIf { it > 0L }
            ?: dsl.targetDateMs

        val updatedDsl = dsl.copy(
            targetDateMs = resolvedTargetDate,
            components = updatedComponents
        )
        val check = CompletenessCheck(
            isComplete = missingFields.none { it.isBlocker },
            missingFields = missingFields
        )
        val clarifications = missingFields
            .filter { it.isBlocker }
            .map { field ->
                ClarificationRequest(
                    componentType = field.componentType,
                    fieldName = field.fieldName,
                    question = field.question
                )
            }

        return CompletenessValidationResult(
            dsl = updatedDsl,
            check = check,
            clarifications = clarifications
        )
    }

    private fun enrichChecklist(
        component: ComponentDSL,
        input: String,
        missingFields: MutableList<MissingField>
    ): ComponentDSL {
        val currentItems = ChecklistContentQuality.sanitizeDslItems(
            items = ChecklistDslItems.parse(component.config),
            taskTitle = input
        )

        // Semantic layer already populated items — nothing to do
        if (component.populatedFromInput && currentItems.isNotEmpty()) {
            return component.copy(
                config = ChecklistDslItems.withItems(component.config, currentItems),
                needsClarification = false
            )
        }

        val explicitItems = extractExplicitChecklistItems(input)
        val resolvedItems = when {
            explicitItems.isNotEmpty() -> explicitItems.map { ChecklistItemDSL(label = it) }
            currentItems.isNotEmpty() && !shouldReplaceGenericItems(currentItems) -> currentItems
            else -> emptyList()
        }

        val needsClarification = resolvedItems.isEmpty()
        if (needsClarification) {
            missingFields += MissingField(
                componentType = ComponentType.CHECKLIST,
                fieldName = "items",
                question = "What items should be included?",
                isBlocker = true
            )
        }

        return component.copy(
            config = ChecklistDslItems.withItems(component.config, resolvedItems),
            populatedFromInput = explicitItems.isNotEmpty() || component.populatedFromInput,
            needsClarification = needsClarification
        )
    }

    private fun enrichCountdown(
        component: ComponentDSL,
        missingFields: MutableList<MissingField>
    ): ComponentDSL {
        val targetDate = component.config["targetDate"]?.jsonPrimitive?.longOrNull ?: 0L
        val needsClarification = targetDate <= 0L
        if (needsClarification) {
            missingFields += MissingField(
                componentType = ComponentType.COUNTDOWN,
                fieldName = "targetDate",
                question = "When is this due?",
                isBlocker = true
            )
        }
        return component.copy(needsClarification = needsClarification)
    }

    private fun enrichMetricTracker(
        component: ComponentDSL,
        missingFields: MutableList<MissingField>
    ): ComponentDSL {
        val goal = component.config["goal"]?.jsonPrimitive?.contentOrNull
        if (goal.isNullOrBlank()) {
            missingFields += MissingField(
                componentType = ComponentType.METRIC_TRACKER,
                fieldName = "goal",
                question = "Do you have a numeric target? You can leave this undefined.",
                isBlocker = false
            )
        }
        return component.copy(needsClarification = false)
    }

    private fun enrichHabitRing(
        component: ComponentDSL,
        missingFields: MutableList<MissingField>
    ): ComponentDSL {
        val targetCount = component.config["targetCount"]?.jsonPrimitive?.intOrNull
        if (targetCount == null) {
            missingFields += MissingField(
                componentType = ComponentType.HABIT_RING,
                fieldName = "targetCount",
                question = "How many times per day or week?",
                isBlocker = false
            )
        }
        return component.copy(needsClarification = false)
    }

    /**
     * Extracts items from the input using language-agnostic structural signals:
     *   1. Lines previously submitted as a user clarification answer
     *   2. Bullet-point lines (-, *, •) — work in any language
     *   3. Inline comma/semicolon-separated atomic items already present in the prompt
     */
    private fun extractExplicitChecklistItems(rawInput: String): List<String> {
        return ChecklistInputExtraction.extract(rawInput)
    }

    private fun shouldReplaceGenericItems(items: List<ChecklistItemDSL>): Boolean {
        return ChecklistContentQuality.sanitizeDslItems(items).isEmpty()
    }
}

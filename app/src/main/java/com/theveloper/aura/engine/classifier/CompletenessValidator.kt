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
    val skipLabel: String = "Skip for now"
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
        val cleanInput = sanitizeInput(input)
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
        val currentItems = ChecklistDslItems.parse(component.config)

        // Semantic layer already populated items — nothing to do
        if (component.populatedFromInput && currentItems.isNotEmpty()) {
            return component.copy(needsClarification = false)
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

    private fun sanitizeInput(input: String): String {
        return input
            .lineSequence()
            .filterNot { line ->
                line.startsWith("Preferred title:", ignoreCase = true) ||
                    line.startsWith("Task type hint:", ignoreCase = true) ||
                    line.startsWith(CLARIFICATION_ANSWER_PREFIX, ignoreCase = true)
            }
            .joinToString("\n")
            .trim()
    }

    /**
     * Extracts items from the input using language-agnostic structural signals:
     *   1. Lines previously submitted as a user clarification answer
     *   2. Bullet-point lines (-, *, •) — work in any language
     */
    private fun extractExplicitChecklistItems(rawInput: String): List<String> {
        // 1. Extract from clarification answer prefix (language-neutral protocol)
        rawInput.lineSequence()
            .filter { it.startsWith(CLARIFICATION_ANSWER_PREFIX, ignoreCase = true) }
            .map { it.removePrefix(CLARIFICATION_ANSWER_PREFIX).trim() }
            .filter { it.isNotBlank() }
            .flatMap(::splitItems)
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        // 2. Bullet-point structure (language-agnostic)
        val bulletItems = rawInput.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("-") || it.startsWith("*") || it.startsWith("•") }
            .map { it.removePrefix("-").removePrefix("*").removePrefix("•").trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (bulletItems.size >= 2) return bulletItems

        return emptyList()
    }

    private fun shouldReplaceGenericItems(items: List<ChecklistItemDSL>): Boolean {
        return items.isNotEmpty() && items.all { item ->
            item.label.trim().lowercase() in GENERIC_PLACEHOLDER_LABELS
        }
    }

    private fun splitItems(raw: String): List<String> {
        val parts = if (raw.contains(",") || raw.contains(";") || raw.contains("\n")) {
            raw.split(ITEM_SEPARATOR_REGEX)
        } else {
            raw.split(AND_SEPARATOR_REGEX)
        }

        val normalized = parts
            .map { it.trim().trim('-', '*', '•', '.', ':').replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() && it.length > 1 }
            .distinct()

        return expandTrailingCoordinatedItem(normalized)
    }

    private fun expandTrailingCoordinatedItem(items: List<String>): List<String> {
        if (items.size < 3) return items
        val lastItem = items.last()
        val match = TRAILING_AND_REGEX.matchEntire(lastItem) ?: return items
        val left = match.groupValues[1].trim()
        val right = match.groupValues[2].trim()
        if (!looksLikeAtomicItem(left) || !looksLikeAtomicItem(right)) return items
        val prev = items.dropLast(1)
        if (prev.count(::looksLikeAtomicItem) < 2) return items
        return prev + listOf(left, right)
    }

    private fun looksLikeAtomicItem(item: String): Boolean {
        val tokens = item.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return tokens.size in 1..3 && !TRAILING_AND_REGEX.containsMatchIn(item)
    }

    private companion object {
        const val CLARIFICATION_ANSWER_PREFIX = "User clarification: "

        /** Comma, semicolon, or newline — universal separators. */
        val ITEM_SEPARATOR_REGEX = Regex("[,;\\n]+")

        /** "and" or "&" — widely used in English and international contexts; no language-specific conjunctions. */
        val AND_SEPARATOR_REGEX = Regex("\\s+(?:and|&)\\s+", RegexOption.IGNORE_CASE)

        /** Trailing "X and Y" pattern for expanding the last item. */
        val TRAILING_AND_REGEX = Regex(
            pattern = "(.+?)\\s+(?:and|&)\\s+(.+)",
            option = RegexOption.IGNORE_CASE
        )

        /**
         * Generic placeholder labels that were formerly injected by the catalog as scaffolding.
         * If a checklist consists entirely of these, it should be replaced.
         */
        val GENERIC_PLACEHOLDER_LABELS = setOf(
            "define next step",
            "execute",
            "review result"
        )
    }
}

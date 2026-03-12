package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.MemoryCategory
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
    val skipLabel: String = "Crear con lo que hay"
)

data class CompletenessValidationResult(
    val dsl: TaskDSLOutput,
    val check: CompletenessCheck,
    val clarification: ClarificationRequest? = null
)

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
                ComponentType.CHECKLIST -> enrichChecklist(component, cleanInput, memorySlots, missingFields)
                ComponentType.COUNTDOWN -> enrichCountdown(component, missingFields)
                ComponentType.METRIC_TRACKER -> enrichMetricTracker(component, missingFields)
                ComponentType.HABIT_RING -> enrichHabitRing(component, missingFields)
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
        val firstBlocker = missingFields.firstOrNull { it.isBlocker }

        return CompletenessValidationResult(
            dsl = updatedDsl,
            check = check,
            clarification = firstBlocker?.let {
                ClarificationRequest(
                    componentType = it.componentType,
                    fieldName = it.fieldName,
                    question = it.question
                )
            }
        )
    }

    private fun enrichChecklist(
        component: ComponentDSL,
        input: String,
        memorySlots: List<MemorySlot>,
        missingFields: MutableList<MissingField>
    ): ComponentDSL {
        val currentItems = ChecklistDslItems.parse(component.config)
        val explicitItems = extractExplicitChecklistItems(input)
        val inferredItems = inferChecklistItems(input, memorySlots)
        val shouldReplaceGeneric = shouldReplaceGenericChecklist(input, currentItems)

        val resolvedItems = when {
            explicitItems.isNotEmpty() -> explicitItems.map { ChecklistItemDSL(label = it) }
            currentItems.isNotEmpty() && !shouldReplaceGeneric -> currentItems
            inferredItems.isNotEmpty() -> inferredItems
            currentItems.isNotEmpty() && !requiresSpecificChecklistContent(input) -> currentItems
            else -> emptyList()
        }

        val needsClarification = resolvedItems.isEmpty()
        if (needsClarification) {
            missingFields += MissingField(
                componentType = ComponentType.CHECKLIST,
                fieldName = "items",
                question = "¿Qué ítems querés incluir?",
                isBlocker = true
            )
        }

        return component.copy(
            config = ChecklistDslItems.withItems(component.config, resolvedItems),
            populatedFromInput = explicitItems.isNotEmpty() || inferredItems.isNotEmpty() || component.populatedFromInput,
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
                question = "¿Para cuándo es?",
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
                question = "¿Tenés un objetivo? Podés dejarlo sin definir.",
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
                question = "¿Cuántas veces por día o por semana?",
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
                    line.startsWith("Task type hint:", ignoreCase = true)
            }
            .joinToString("\n")
            .trim()
    }

    private fun extractExplicitChecklistItems(input: String): List<String> {
        extractClarificationAnswer(input)?.let { answer ->
            splitItems(answer).takeIf { it.isNotEmpty() }?.let { return it }
        }

        val bulletItems = input.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("-") || it.startsWith("*") }
            .map { it.removePrefix("-").removePrefix("*").trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (bulletItems.size >= 2) {
            return bulletItems
        }

        val colonMatch = LIST_WITH_DETAILS_REGEX.find(input)
        if (colonMatch != null) {
            return splitItems(colonMatch.groupValues[1])
        }

        val shoppingTail = SHOPPING_WITH_ITEMS_REGEX.find(input)?.groupValues?.getOrNull(1).orEmpty()
        if (shoppingTail.isNotBlank()) {
            return splitItems(shoppingTail)
        }

        return emptyList()
    }

    private fun inferChecklistItems(
        input: String,
        memorySlots: List<MemorySlot>
    ): List<ChecklistItemDSL> {
        when {
            ASADO_REGEX.containsMatchIn(input) -> {
                return listOf(
                    ChecklistItemDSL("Carne", isSuggested = true),
                    ChecklistItemDSL("Carbon", isSuggested = true),
                    ChecklistItemDSL("Chimichurri", isSuggested = true),
                    ChecklistItemDSL("Pan", isSuggested = true)
                )
            }

            SHOPPING_CONTEXT_REGEX.containsMatchIn(input) -> {
                val memoryItems = extractShoppingMemoryItems(memorySlots)
                if (memoryItems.isNotEmpty()) {
                    return memoryItems.map { ChecklistItemDSL(it, isSuggested = true) }
                }
            }
        }

        return emptyList()
    }

    private fun extractShoppingMemoryItems(memorySlots: List<MemorySlot>): List<String> {
        val relevantSlots = memorySlots.filter { slot ->
            slot.category == MemoryCategory.VOCABULARY || slot.category == MemoryCategory.TASK_PREFERENCES
        }

        val fromLabeledLine = relevantSlots
            .flatMap { slot ->
                slot.content.lineSequence().map(String::trim).toList()
            }
            .mapNotNull { line ->
                SHOPPING_MEMORY_LINE_REGEX.find(line)?.groupValues?.getOrNull(1)
            }
            .flatMap(::splitItems)
            .distinct()
        if (fromLabeledLine.isNotEmpty()) {
            return fromLabeledLine.take(6)
        }

        return emptyList()
    }

    private fun shouldReplaceGenericChecklist(
        input: String,
        currentItems: List<ChecklistItemDSL>
    ): Boolean {
        if (!requiresSpecificChecklistContent(input)) {
            return false
        }
        if (currentItems.isEmpty()) {
            return true
        }
        return currentItems.all { item ->
            item.label.lowercase() in GENERIC_PLACEHOLDER_ITEMS
        }
    }

    private fun requiresSpecificChecklistContent(input: String): Boolean {
        return SHOPPING_CONTEXT_REGEX.containsMatchIn(input) ||
            ASADO_REGEX.containsMatchIn(input)
    }

    private fun extractClarificationAnswer(input: String): String? {
        return CLARIFICATION_ANSWER_REGEX.find(input)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun splitItems(raw: String): List<String> {
        val separators = if (raw.contains(",") || raw.contains(";") || raw.contains("\n")) {
            raw.split(ITEM_SEPARATOR_REGEX)
        } else {
            raw.split(AND_SEPARATOR_REGEX)
        }

        return separators
            .map { item ->
                item.trim()
                    .trim('-', '*', '.', ':')
                    .replace(Regex("\\s+"), " ")
            }
            .filter { item ->
                item.isNotBlank() &&
                    item.length > 1 &&
                    !item.startsWith("Preferred title:", ignoreCase = true) &&
                    !item.startsWith("Task type hint:", ignoreCase = true)
            }
            .distinct()
    }

    private companion object {
        val LIST_WITH_DETAILS_REGEX = Regex("(?i)(?:lista|items?|ítems?|compras?|ingredientes?)\\s*[:\\-]\\s*(.+)")
        val SHOPPING_WITH_ITEMS_REGEX = Regex("(?i)(?:comprar|compras?)\\s+(.+)")
        val SHOPPING_CONTEXT_REGEX = Regex("(?i)\\b(lista\\s+de\\s+compras?|compras?|super|supermercado|mercado|ingredientes?)\\b")
        val ASADO_REGEX = Regex("(?i)\\basado\\b")
        val SHOPPING_MEMORY_LINE_REGEX = Regex("(?i)(?:compras\\s+frecuentes|items\\s+frecuentes)\\s*:\\s*(.+)")
        val CLARIFICATION_ANSWER_REGEX = Regex("(?i)aclaracion\\s+del\\s+usuario\\s*:\\s*(.+)")
        val ITEM_SEPARATOR_REGEX = Regex("[,;\\n]+")
        val AND_SEPARATOR_REGEX = Regex("\\s+y\\s+")
        val GENERIC_PLACEHOLDER_ITEMS = setOf(
            "define next step",
            "execute",
            "review result"
        )
    }
}

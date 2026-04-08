package com.theveloper.aura.engine.classifier

import android.content.Context
import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.engine.dsl.ChecklistDslItems
import com.theveloper.aura.engine.dsl.ChecklistItemDSL
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.llm.LLMService
import com.theveloper.aura.engine.llm.decodeTaskDslOrNull
import com.theveloper.aura.engine.llm.extractLikelyJsonBlock
import com.theveloper.aura.engine.llm.stripCodeFences
import com.theveloper.aura.engine.skill.SkillPromptCards
import com.theveloper.aura.engine.skill.UiSkillRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class TaskDraftRescueService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun rescue(
        service: LLMService,
        input: String,
        llmContext: LLMClassificationContext,
        draft: TaskDSLOutput
    ): TaskDSLOutput? {
        val refinedInitialChecklist = refineExpandableChecklistItems(
            service = service,
            input = input,
            llmContext = llmContext,
            draft = draft
        )
        val rebalancedNarrativeDraft = rebalanceChecklistNarrativeContent(
            service = service,
            input = input,
            llmContext = llmContext,
            draft = refinedInitialChecklist ?: draft
        )
        val baselineDraft = rebalancedNarrativeDraft ?: refinedInitialChecklist ?: draft
        if (!needsRescue(baselineDraft)) return rebalancedNarrativeDraft ?: refinedInitialChecklist

        val firstAttempt = completeRescuePrompt(
            service = service,
            prompt = buildPrompt(input, llmContext, baselineDraft),
            input = input,
            llmContext = llmContext,
            fallbackDraft = baselineDraft
        )
        if (firstAttempt != null && !needsFurtherRepair(firstAttempt, input)) {
            return firstAttempt
        }

        val focusedBaseDraft = firstAttempt ?: baselineDraft
        val focusedAttempt = completeRescuePrompt(
            service = service,
            prompt = buildFocusedRepairPrompt(input, llmContext, focusedBaseDraft),
            input = input,
            llmContext = llmContext,
            fallbackDraft = focusedBaseDraft
        )

        val bestStructuredAttempt = focusedAttempt ?: firstAttempt ?: refinedInitialChecklist
        val refinedStructuredChecklist = bestStructuredAttempt?.let { structured ->
            refineExpandableChecklistItems(
                service = service,
                input = input,
                llmContext = llmContext,
                draft = structured
            )
        }
        val bestChecklistAwareAttempt = refinedStructuredChecklist ?: bestStructuredAttempt
        if (bestChecklistAwareAttempt != null && !needsFurtherRepair(bestChecklistAwareAttempt, input)) {
            return bestChecklistAwareAttempt
        }

        val checklistPatched = repairHollowChecklistWithItems(
            service = service,
            input = input,
            llmContext = llmContext,
            draft = bestChecklistAwareAttempt ?: baselineDraft
        )
        if (checklistPatched != null && !needsFurtherRepair(checklistPatched, input)) {
            return checklistPatched
        }

        val contentPatched = repairHollowNotesWithMarkdown(
            service = service,
            input = input,
            llmContext = llmContext,
            draft = checklistPatched ?: bestChecklistAwareAttempt ?: baselineDraft
        )

        return contentPatched ?: checklistPatched ?: bestChecklistAwareAttempt ?: refinedInitialChecklist
    }

    fun needsRescue(draft: TaskDSLOutput): Boolean {
        if (draft.components.isEmpty()) return true
        if (draftLikelyNeedsChecklist(draft.title) &&
            draft.components.none { component ->
                component.type == ComponentType.CHECKLIST && isMeaningfulChecklist(component, draft.title)
            }
        ) {
            return true
        }

        return draft.components.none { component -> isMeaningfulComponent(component, draft.title) }
    }

    private fun buildPrompt(
        input: String,
        llmContext: LLMClassificationContext,
        draft: TaskDSLOutput
    ): String {
        return buildString {
            appendLine(SkillPromptCards.buildCompactFinalTaskPrompt(llmContext.detectedTaskType))
            appendLine()
            appendLine("--- RESCUE TASK ---")
            appendLine("The current task draft is structurally valid but low quality.")
            appendLine("Improve it into the best ready-to-use task UI for the original request.")
            appendLine("Prefer intelligence over clarification when a useful first version can be produced.")
            appendLine("You may change selected UI skills if the draft chose the wrong ones.")
            appendLine("Never leave selected skills hollow.")
            appendLine("A teaser sentence, filler sentence, or promise of future content is not acceptable output.")
            appendLine("If the user asked for concrete content, include the actual content now instead of announcing it.")
            appendLine("System hint lines may appear as [[preferred_title]], [[task_type_hint]], or [[clarification]].")
            appendLine("Treat them as machine hints or prior user answers, never as visible task content.")
            appendLine()
            appendLine("Original user input:")
            appendLine(input)
            appendLine()
            appendContextHints(llmContext)
            appendLine()
            appendLine("Current draft JSON:")
            appendLine(auraJson.encodeToString(TaskDSLOutput.serializer(), draft))
            appendLine()
            append("Return only valid JSON.")
        }
    }

    private fun buildFocusedRepairPrompt(
        input: String,
        llmContext: LLMClassificationContext,
        draft: TaskDSLOutput
    ): String {
        val currentUiSkills = draft.components.mapNotNull { component ->
            UiSkillRegistry.resolve(component.type)
        }.distinctBy { it.id }

        return buildString {
            appendLine("You are AURA's focused draft repair agent.")
            appendLine("Repair the task draft into a ready-to-use final task.")
            appendLine("All user-facing text must stay in the same language as the user's input.")
            appendLine("The current draft is still shallow or hollow.")
            appendLine("A teaser sentence, placeholder, filler sentence, or promise of future content is invalid.")
            appendLine("If the request asks for a concrete artifact, the artifact itself must appear in the UI now.")
            appendLine("You may add, remove, or replace UI skills if that improves the result.")
            appendLine("Do not leave any remaining component hollow.")
            appendLine("Do not return a planner-only payload with uiSkills/functionSkills arrays and no real task components.")
            appendLine()
            appendLine(SkillPromptCards.buildCompactFinalTaskContract(llmContext.detectedTaskType))
            appendLine()
            if (currentUiSkills.isNotEmpty()) {
                appendLine("Current UI skill guidance:")
                currentUiSkills.forEach { definition ->
                    appendLine("- ${definition.id}: ${definition.promptHint}")
                }
                appendLine()
            }
            appendLine("Original user input:")
            appendLine(input)
            appendLine()
            appendContextHints(llmContext)
            appendLine()
            appendLine("Current shallow draft JSON:")
            appendLine(auraJson.encodeToString(TaskDSLOutput.serializer(), draft))
            appendLine()
            appendLine("Return only valid JSON for the final task.")
        }
    }

    private suspend fun completeRescuePrompt(
        service: LLMService,
        prompt: String,
        input: String,
        llmContext: LLMClassificationContext,
        fallbackDraft: TaskDSLOutput
    ): TaskDSLOutput? {
        val raw = runCatching {
            service.complete(prompt)
        }.getOrNull()?.trim().orEmpty()
        if (raw.isBlank()) return null

        val decoded = raw.decodeTaskDslOrNull()
        val salvaged = salvageTaskDslFromRaw(
                raw = raw,
                input = input,
                llmContext = llmContext,
                fallbackDraft = decoded ?: fallbackDraft
            )

        return chooseBetterRescueDraft(
            decoded = decoded,
            salvaged = salvaged,
            input = input
        )
    }

    private suspend fun repairHollowNotesWithMarkdown(
        service: LLMService,
        input: String,
        llmContext: LLMClassificationContext,
        draft: TaskDSLOutput
    ): TaskDSLOutput? {
        val notesIndex = draft.components.indexOfFirst { component ->
            component.type == ComponentType.NOTES && DraftContentQuality.isThinNotes(component.noteText())
        }

        val markdown = runCatching {
            service.complete(buildNotesRepairPrompt(input, llmContext, draft))
        }.getOrNull()
            ?.stripCodeFences()
            ?.trim()
            ?.let(DraftContentQuality::sanitizeGeneratedText)
            ?.takeIf { it.isNotBlank() && !DraftContentQuality.isThinNotes(it) }
            ?: return null

        val updatedComponents = when {
            notesIndex >= 0 -> draft.components.mapIndexed { index, component ->
                if (index != notesIndex) {
                    component
                } else {
                    component.withNotesText(markdown)
                }
            }

            else -> draft.components + createNotesComponent(
                sortOrder = draft.components.size,
                markdown = markdown
            )
        }
        val repairedDraft = draft.copy(components = updatedComponents)
        return repairedDraft.takeUnless(::needsRescue)
    }

    private suspend fun repairHollowChecklistWithItems(
        service: LLMService,
        input: String,
        llmContext: LLMClassificationContext,
        draft: TaskDSLOutput
    ): TaskDSLOutput? {
        val checklistIndex = draft.components.indexOfFirst { component ->
            component.type == ComponentType.CHECKLIST && ChecklistDslItems.parse(component.config).isEmpty()
        }
        val shouldCreateChecklist = checklistIndex == -1 && draftLikelyNeedsChecklist(draft.title)
        if (checklistIndex == -1 && !shouldCreateChecklist) return null

        val items = runCatching {
            service.complete(buildChecklistRepairPrompt(input, llmContext, draft))
        }.getOrNull()
            ?.let { raw -> parseChecklistRepairItems(raw, draft.title, input) }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val updatedComponents = if (checklistIndex >= 0) {
            draft.components.mapIndexed { index, component ->
                if (index != checklistIndex) {
                    component
                } else {
                    component.withChecklistItems(
                        items = items,
                        label = draft.title.ifBlank { component.config["label"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                    )
                }
            }
        } else {
            draft.components + createChecklistComponent(
                sortOrder = draft.components.size,
                label = draft.title.ifBlank { "Checklist" },
                items = items
            )
        }
        val repairedDraft = draft.copy(components = updatedComponents)
        return repairedDraft.takeUnless(::needsRescue)
    }

    private suspend fun refineExpandableChecklistItems(
        service: LLMService,
        input: String,
        llmContext: LLMClassificationContext,
        draft: TaskDSLOutput
    ): TaskDSLOutput? {
        val checklistIndex = draft.components.indexOfFirst { component ->
            component.type == ComponentType.CHECKLIST &&
                ChecklistDslItems.parse(component.config).isNotEmpty() &&
                checklistNeedsExpansion(component, draft.title)
        }
        if (checklistIndex == -1) return null

        val checklist = draft.components[checklistIndex]
        val currentItems = ChecklistDslItems.parse(checklist.config).map { it.label }
        val refinedItems = runCatching {
            service.complete(buildChecklistExpansionPrompt(input, llmContext, draft, currentItems))
        }.getOrNull()
            ?.let { raw -> parseChecklistRepairItems(raw, draft.title, input) }
            ?.takeIf { candidate -> isChecklistExpansionBetter(currentItems, candidate, draft.title) }
            ?: return null

        val updatedComponents = draft.components.mapIndexed { index, component ->
            if (index != checklistIndex) {
                component
            } else {
                component.withChecklistItems(
                    items = refinedItems,
                    label = draft.title.ifBlank { component.config["label"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                )
            }
        }

        return draft.copy(components = updatedComponents)
    }

    private suspend fun rebalanceChecklistNarrativeContent(
        service: LLMService,
        input: String,
        llmContext: LLMClassificationContext,
        draft: TaskDSLOutput
    ): TaskDSLOutput? {
        val checklistCount = draft.components.count { it.type == ComponentType.CHECKLIST }
        val hasNotes = draft.components.any { it.type == ComponentType.NOTES }
        if (checklistCount < 2 || hasNotes) return null

        val rebalanced = completeRescuePrompt(
            service = service,
            prompt = buildNarrativeRebalancePrompt(input, llmContext, draft),
            input = input,
            llmContext = llmContext,
            fallbackDraft = draft
        ) ?: return null

        return rebalanced.takeIf { isNarrativeRebalanceBetter(draft, it) }
    }

    private fun buildChecklistRepairPrompt(
        input: String,
        llmContext: LLMClassificationContext,
        draft: TaskDSLOutput
    ): String {
        return buildString {
            appendLine("You are AURA's checklist item repair agent.")
            appendLine("Write the concrete checklist items needed for the task.")
            appendLine("All user-facing text must stay in the same language as the user's input.")
            appendLine("Return only checklist items, one per line. No JSON. No markdown fences. No explanations.")
            appendLine("Keep the items the user already named.")
            appendLine("If the user referenced a known recipe, shopping bundle, packing set, or ingredient set, expand it into concrete items now.")
            appendLine("Do not keep umbrella labels like a named thing plus 'ingredients' when you can expand them into atomic items.")
            appendLine("Do not ask questions. Do not use placeholders. Do not repeat the task title or the full user prompt.")
            appendLine()
            appendLine("Task title:")
            appendLine(draft.title)
            appendLine()
            appendLine("Original user input:")
            appendLine(input)
            appendLine()
            appendContextHints(llmContext)
            appendLine()
            appendLine("Current task draft JSON:")
            appendLine(auraJson.encodeToString(TaskDSLOutput.serializer(), draft))
        }
    }

    private fun buildChecklistExpansionPrompt(
        input: String,
        llmContext: LLMClassificationContext,
        draft: TaskDSLOutput,
        currentItems: List<String>
    ): String {
        return buildString {
            appendLine("You are AURA's checklist refinement agent.")
            appendLine("Rewrite the checklist as the best concrete atomic item list for the task.")
            appendLine("All user-facing text must stay in the same language as the user's input.")
            appendLine("Return only checklist items, one per line. No JSON. No markdown fences. No explanations.")
            appendLine("Preserve concrete items the user already named.")
            appendLine("If one current item is an umbrella reference to a known recipe, shopping bundle, packing set, or ingredient set, replace that umbrella item with the concrete atomic items that belong to it.")
            appendLine("Do not keep both the umbrella label and its expansion unless the user explicitly asked for both.")
            appendLine("Do not ask questions. Do not add commentary.")
            appendLine()
            appendLine("Task title:")
            appendLine(draft.title)
            appendLine()
            appendLine("Current checklist items:")
            currentItems.forEach(::appendLine)
            appendLine()
            appendLine("Original user input:")
            appendLine(input)
            appendLine()
            appendContextHints(llmContext)
        }
    }

    private fun buildNotesRepairPrompt(
        input: String,
        llmContext: LLMClassificationContext,
        draft: TaskDSLOutput
    ): String {
        return buildString {
            appendLine("You are AURA's notes content repair agent.")
            appendLine("Write ready-to-use markdown for the best user-facing content of the task.")
            appendLine("All user-facing text must stay in the same language as the user's input.")
            appendLine("Return only markdown. No JSON. No markdown fences. No explanations.")
            appendLine("If the request asks for a concrete artifact, provide the artifact itself now.")
            appendLine("Do not say the content will come later.")
            appendLine("Do not write research plans, search steps, fill-in-yourself prompts, or placeholders.")
            appendLine("Do not ask the user to provide details that the model can reasonably infer or author.")
            appendLine()
            appendLine("Task title:")
            appendLine(draft.title)
            appendLine()
            appendLine("Original user input:")
            appendLine(input)
            appendLine()
            appendContextHints(llmContext)
            appendLine()
            appendLine("Current task draft JSON:")
            appendLine(auraJson.encodeToString(TaskDSLOutput.serializer(), draft))
        }
    }

    private fun buildNarrativeRebalancePrompt(
        input: String,
        llmContext: LLMClassificationContext,
        draft: TaskDSLOutput
    ): String {
        return buildString {
            appendLine("You are AURA's UI rebalance agent.")
            appendLine("Improve the current task into the best ready-to-use final task JSON.")
            appendLine("All user-facing text must stay in the same language as the user's input.")
            appendLine("Keep concrete item lists as checklist UI.")
            appendLine("If any checklist currently contains written instructions, recipe steps, guide content, or other narrative content, move that content into a notes component with useful markdown.")
            appendLine("Prefer checklist plus notes over multiple checklists when one checklist is carrying prose-like instructional content.")
            appendLine("Preserve useful existing content. Do not add placeholders. Return only valid JSON for the final task.")
            appendLine()
            appendLine("Original user input:")
            appendLine(input)
            appendLine()
            appendContextHints(llmContext)
            appendLine()
            appendLine("Current task draft JSON:")
            appendLine(auraJson.encodeToString(TaskDSLOutput.serializer(), draft))
        }
    }

    private fun isMeaningfulComponent(
        component: ComponentDSL,
        taskTitle: String
    ): Boolean {
        return when (component.type) {
            ComponentType.CHECKLIST -> isMeaningfulChecklist(component, taskTitle) || component.needsClarification

            ComponentType.NOTES ->
                !DraftContentQuality.isThinNotes(component.noteText())

            ComponentType.COUNTDOWN ->
                component.config["targetDate"]?.toString()?.toLongOrNull()?.let { it > 0L } == true ||
                    component.needsClarification

            else -> true
        }
    }

    private fun isMeaningfulChecklist(
        component: ComponentDSL,
        taskTitle: String
    ): Boolean {
        val items = ChecklistContentQuality.sanitizeItems(
            items = ChecklistDslItems.parse(component.config).map { it.label },
            taskTitle = taskTitle
        )
        return ChecklistContentQuality.itemsLookUsable(items, taskTitle)
    }

    private fun ComponentDSL.withNotesText(markdown: String): ComponentDSL {
        val updatedConfig = JsonObject(
            config + mapOf(
                "config_type" to JsonPrimitive(ComponentType.NOTES.name),
                "text" to JsonPrimitive(markdown),
                "isMarkdown" to JsonPrimitive(true)
            )
        )
        return copy(
            config = updatedConfig,
            needsClarification = false
        )
    }

    private fun ComponentDSL.withChecklistItems(
        items: List<String>,
        label: String
    ): ComponentDSL {
        val updatedConfig = ChecklistDslItems.withItems(
            config = JsonObject(
                config + mapOf(
                    "config_type" to JsonPrimitive(ComponentType.CHECKLIST.name),
                    "label" to JsonPrimitive(label.ifBlank { "Checklist" }),
                    "allowAddItems" to JsonPrimitive(config["allowAddItems"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true)
                )
            ),
            items = items.map { ChecklistItemDSL(label = it) }
        )
        return copy(
            config = updatedConfig,
            populatedFromInput = true,
            needsClarification = false
        )
    }

    private fun createNotesComponent(
        sortOrder: Int,
        markdown: String
    ): ComponentDSL {
        return ComponentDSL(
            skillId = "notes",
            type = ComponentType.NOTES,
            sortOrder = sortOrder,
            config = JsonObject(
                mapOf(
                    "config_type" to JsonPrimitive(ComponentType.NOTES.name),
                    "text" to JsonPrimitive(markdown),
                    "isMarkdown" to JsonPrimitive(true)
                )
            ),
            populatedFromInput = false,
            needsClarification = false
        )
    }

    private fun createChecklistComponent(
        sortOrder: Int,
        label: String,
        items: List<String>
    ): ComponentDSL {
        return ComponentDSL(
            skillId = "checklist",
            type = ComponentType.CHECKLIST,
            sortOrder = sortOrder,
            config = ChecklistDslItems.withItems(
                config = JsonObject(
                    mapOf(
                        "config_type" to JsonPrimitive(ComponentType.CHECKLIST.name),
                        "label" to JsonPrimitive(label.ifBlank { "Checklist" }),
                        "allowAddItems" to JsonPrimitive(true)
                    )
                ),
                items = items.map(::ChecklistItemDSL)
            ),
            populatedFromInput = true,
            needsClarification = false
        )
    }

    private fun ComponentDSL.noteText(): String {
        return config["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun parseChecklistRepairItems(
        raw: String,
        title: String,
        input: String
    ): List<String> {
        val sanitized = raw.stripCodeFences().trim()
        if (sanitized.isBlank()) return emptyList()

        parseChecklistItemsFromJson(sanitized, title).takeIf { it.isNotEmpty() }?.let { return it }
        ChecklistContentQuality.sanitizeItems(
            items = ChecklistInputExtraction.extract(sanitized),
            taskTitle = title
        ).takeIf { it.isNotEmpty() }?.let { return it }

        val titleLower = title.trim().lowercase()
        val inputLower = input.trim().lowercase()

        return ChecklistContentQuality.sanitizeItems(
            items = sanitized.lineSequence().toList(),
            taskTitle = title
        ).filter { candidate ->
            candidate.isNotBlank() &&
                candidate != "..." &&
                !candidate.equals(titleLower, ignoreCase = true) &&
                !candidate.equals(inputLower, ignoreCase = true) &&
                !candidate.equals("final output", ignoreCase = true)
        }
    }

    private fun parseChecklistItemsFromJson(
        raw: String,
        taskTitle: String
    ): List<String> {
        val element = runCatching {
            auraJson.parseToJsonElement(raw.extractLikelyJsonBlock())
        }.getOrNull()
        val jsonishFallbackItems = extractChecklistItemLabelsFromJsonish(raw, taskTitle)

        if (element != null) {
            val parsedItems = extractChecklistItemLabels(element, taskTitle)
            return when {
                parsedItems.isEmpty() -> jsonishFallbackItems
                jsonishFallbackItems.size > parsedItems.size -> jsonishFallbackItems
                else -> parsedItems
            }
        }

        return jsonishFallbackItems
    }

    private fun extractChecklistItemLabels(
        element: JsonElement,
        taskTitle: String
    ): List<String> {
        return when (element) {
            is JsonArray -> element
                .flatMap { child -> extractChecklistItemLabels(child, taskTitle) }
                .distinct()

            is JsonObject -> {
                extractChecklistItemLabels(element["items"] ?: JsonArray(emptyList()), taskTitle).takeIf { it.isNotEmpty() }
                    ?: ((element["components"] as? JsonArray)
                        ?.let { children -> extractChecklistItemLabels(children, taskTitle) }
                        ?.takeIf { items -> items.isNotEmpty() })
                    ?: ((element["skills"] as? JsonArray)
                        ?.let { children -> extractChecklistItemLabels(children, taskTitle) }
                        ?.takeIf { items -> items.isNotEmpty() })
                    ?: ((element["config"] as? JsonObject)
                        ?.let { config -> extractChecklistItemLabels(config, taskTitle) }
                        ?.takeIf { items -> items.isNotEmpty() })
                    ?: extractChecklistItemLabel(element, taskTitle)?.let(::listOf)
                    ?: emptyList()
            }

            else -> emptyList()
        }
    }

    private fun extractChecklistItemLabel(
        element: JsonElement,
        taskTitle: String
    ): String? {
        return when (element) {
            is JsonPrimitive -> ChecklistContentQuality
                .normalizeCandidate(element.contentOrNull.orEmpty())
                .takeIf { candidate -> ChecklistContentQuality.looksLikeConcreteItem(candidate, taskTitle) }

            is JsonObject -> {
                element["label"]?.jsonPrimitive?.contentOrNull
                    ?.let(ChecklistContentQuality::normalizeCandidate)
                    ?.takeIf { candidate -> ChecklistContentQuality.looksLikeConcreteItem(candidate, taskTitle) }
            }
            else -> null
        }
    }

    private fun checklistNeedsExpansion(
        component: ComponentDSL,
        taskTitle: String
    ): Boolean {
        val items = ChecklistDslItems.parse(component.config).map { it.label }
        return items.any { item -> looksExpandableChecklistItem(item, taskTitle) }
    }

    private fun isChecklistExpansionBetter(
        currentItems: List<String>,
        refinedItems: List<String>,
        taskTitle: String
    ): Boolean {
        val normalizedCurrent = currentItems
            .map(ChecklistContentQuality::normalizeCandidate)
            .filter(String::isNotBlank)
            .distinct()
        val normalizedRefined = ChecklistContentQuality.sanitizeItems(refinedItems, taskTitle)
        val currentKeys = normalizedCurrent.map(ChecklistContentQuality::comparisonKey)
        val refinedKeys = normalizedRefined.map(ChecklistContentQuality::comparisonKey)
        if (refinedKeys.isEmpty() || refinedKeys == currentKeys) return false

        val expandableCurrent = normalizedCurrent.filter { item -> looksExpandableChecklistItem(item, taskTitle) }
        if (expandableCurrent.isEmpty()) return false

        val expandableKeys = expandableCurrent.map(ChecklistContentQuality::comparisonKey).toSet()
        val preservedConcreteKeys = currentKeys.filterNot { it in expandableKeys }
        if (!preservedConcreteKeys.all { key ->
                refinedKeys.any { refinedKey ->
                    ChecklistContentQuality.areRelatedItems(key, refinedKey)
                }
            }
        ) {
            return false
        }

        val expandableRefinedCount = normalizedRefined.count { item -> looksExpandableChecklistItem(item, taskTitle) }
        return refinedKeys.size > currentKeys.size || expandableRefinedCount < expandableCurrent.size
    }

    private fun isNarrativeRebalanceBetter(
        original: TaskDSLOutput,
        candidate: TaskDSLOutput
    ): Boolean {
        val originalNotes = original.components.count { it.type == ComponentType.NOTES }
        val candidateNotes = candidate.components.count { it.type == ComponentType.NOTES }
        val candidateChecklist = candidate.components.count { it.type == ComponentType.CHECKLIST }
        if (candidateChecklist == 0) return false
        if (candidateNotes <= originalNotes) return false
        return !needsRescue(candidate)
    }

    private fun looksExpandableChecklistItem(
        item: String,
        taskTitle: String
    ): Boolean {
        val itemTokens = ChecklistContentQuality.tokenizeText(item)
        if (itemTokens.size !in 2..6) return false

        val titleTokens = ChecklistContentQuality.tokenizeText(taskTitle)
        if (titleTokens.isEmpty()) return false

        val overlapCount = itemTokens.count { token -> token in titleTokens }
        val overlapRatio = overlapCount.toFloat() / itemTokens.size.toFloat()
        return overlapCount >= 1 && overlapRatio >= 0.34f
    }

    private fun draftLikelyNeedsChecklist(title: String): Boolean {
        val tokens = ChecklistContentQuality.tokenizeText(title)
        return tokens.any { token ->
            token in setOf(
                "shopping",
                "compra",
                "compras",
                "list",
                "lista",
                "ingredient",
                "ingredients",
                "ingrediente",
                "ingredientes",
                "packing",
                "pack",
                "checklist"
            )
        }
    }

    private fun needsFurtherRepair(
        draft: TaskDSLOutput,
        input: String
    ): Boolean {
        return needsRescue(draft) || needsNarrativeRepair(draft, input)
    }

    private fun needsNarrativeRepair(
        draft: TaskDSLOutput,
        input: String
    ): Boolean {
        if (!requestLikelyNeedsNarrative(input, draft.title)) return false

        return draft.components.none { component ->
            component.type == ComponentType.NOTES && !DraftContentQuality.isThinNotes(component.noteText())
        }
    }

    private fun requestLikelyNeedsNarrative(
        input: String,
        title: String
    ): Boolean {
        val tokens = ChecklistContentQuality.tokenizeText("$title $input")
        return tokens.any { token ->
            token in setOf(
                "recipe",
                "receta",
                "guide",
                "guides",
                "instruction",
                "instructions",
                "instruccion",
                "instrucciones",
                "roadmap",
                "summary",
                "resumen",
                "agenda",
                "outline",
                "tutorial"
            )
        }
    }

    private fun chooseBetterRescueDraft(
        decoded: TaskDSLOutput?,
        salvaged: TaskDSLOutput?,
        input: String
    ): TaskDSLOutput? {
        decoded ?: return salvaged
        salvaged ?: return decoded

        val decodedNeedsRepair = needsFurtherRepair(decoded, input)
        val salvagedNeedsRepair = needsFurtherRepair(salvaged, input)
        if (decodedNeedsRepair != salvagedNeedsRepair) {
            return if (salvagedNeedsRepair) decoded else salvaged
        }

        val decodedMeaningful = decoded.components.count { component ->
            isMeaningfulComponent(component, decoded.title)
        }
        val salvagedMeaningful = salvaged.components.count { component ->
            isMeaningfulComponent(component, salvaged.title)
        }
        if (decodedMeaningful != salvagedMeaningful) {
            return if (salvagedMeaningful > decodedMeaningful) salvaged else decoded
        }

        return when {
            salvaged.components.size > decoded.components.size -> salvaged
            decoded.components.size > salvaged.components.size -> decoded
            else -> decoded
        }
    }

    private fun salvageTaskDslFromRaw(
        raw: String,
        input: String,
        llmContext: LLMClassificationContext,
        fallbackDraft: TaskDSLOutput
    ): TaskDSLOutput? {
        val stripped = raw.stripCodeFences().trim()
        if (stripped.isBlank()) return null

        val title = extractJsonishStringField(stripped, "title")
            ?.let(DraftContentQuality::sanitizeGeneratedText)
            ?.takeIf { it.isNotBlank() }
            ?: fallbackDraft.title

        val checklistItems = parseChecklistItemsFromJson(stripped, title)
        val markdown = salvageNotesMarkdownFromRaw(stripped)
        if (checklistItems.isEmpty() && markdown == null) return null

        val components = mutableListOf<ComponentDSL>()

        if (checklistItems.isNotEmpty()) {
            val checklist = fallbackDraft.components.firstOrNull { it.type == ComponentType.CHECKLIST }
            components += (checklist ?: createChecklistComponent(0, title, checklistItems))
                .withChecklistItems(
                    items = checklistItems,
                    label = title.ifBlank { checklist?.config?.get("label")?.jsonPrimitive?.contentOrNull.orEmpty() }
                )
                .copy(sortOrder = components.size)
        }

        if (markdown != null) {
            val notes = fallbackDraft.components.firstOrNull { it.type == ComponentType.NOTES }
            components += (notes ?: createNotesComponent(components.size, markdown))
                .withNotesText(markdown)
                .copy(sortOrder = components.size)
        }

        fallbackDraft.components
            .filter { component ->
                component.type != ComponentType.CHECKLIST &&
                    component.type != ComponentType.NOTES &&
                    isMeaningfulComponent(component, title)
            }
            .forEach { component ->
                components += component.copy(sortOrder = components.size)
            }

        if (components.isEmpty()) return null

        return fallbackDraft.copy(
            title = title.ifBlank { fallbackDraft.title },
            type = extractTaskTypeFromRaw(stripped) ?: llmContext.detectedTaskType ?: fallbackDraft.type,
            components = components
        )
    }

    private fun salvageNotesMarkdownFromRaw(raw: String): String? {
        extractJsonishStringField(raw, "text")
            ?.let(::decodeJsonishString)
            ?.let(DraftContentQuality::sanitizeGeneratedText)
            ?.takeIf { markdown -> !DraftContentQuality.isThinNotes(markdown) }
            ?.let { return it }

        if (raw.contains("\"title\"") || raw.contains("\"components\"") || raw.contains("\"skills\"")) {
            return null
        }

        val cleaned = DraftContentQuality.sanitizeGeneratedText(raw)
        return cleaned.takeUnless(DraftContentQuality::isThinNotes)
    }

    private fun extractChecklistItemLabelsFromJsonish(
        raw: String,
        taskTitle: String
    ): List<String> {
        val labelMatches = JSONISH_LABEL_REGEX.findAll(raw)
            .map { match -> decodeJsonishString(match.groupValues[1]) }
            .toList()
        ChecklistContentQuality.sanitizeItems(labelMatches, taskTitle)
            .takeIf { items -> items.isNotEmpty() }
            ?.let { return it }

        val primitiveItems = JSONISH_ITEMS_ARRAY_REGEX.findAll(raw)
            .map { match -> match.groupValues[1] }
            .filterNot { segment -> '{' in segment }
            .flatMap { segment ->
                JSONISH_STRING_REGEX.findAll(segment).map { match ->
                    decodeJsonishString(match.groupValues[1])
                }
            }
            .toList()

        return ChecklistContentQuality.sanitizeItems(primitiveItems, taskTitle)
    }

    private fun extractJsonishStringField(
        raw: String,
        fieldName: String
    ): String? {
        val regex = Regex(
            "[\"']${Regex.escape(fieldName)}[\"']\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        return regex.find(raw)?.groupValues?.getOrNull(1)
    }

    private fun decodeJsonishString(raw: String): String {
        return raw
            .replace("\\r", "\r")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .trim()
    }

    private fun extractTaskTypeFromRaw(raw: String): com.theveloper.aura.domain.model.TaskType? {
        val rawType = Regex("""["']type["']\s*:\s*["']([A-Z_]+)["']""")
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null

        return runCatching { com.theveloper.aura.domain.model.TaskType.valueOf(rawType) }.getOrNull()
    }

    private fun StringBuilder.appendContextHints(
        llmContext: LLMClassificationContext
    ) {
        llmContext.detectedTaskType?.let {
            appendLine("Detected task type hint: ${it.name}")
        }
        llmContext.extractedDates.takeIf { it.isNotEmpty() }?.let {
            appendLine("Extracted dates (epoch ms): $it")
        }
        llmContext.extractedNumbers.takeIf { it.isNotEmpty() }?.let {
            appendLine("Extracted numbers: $it")
        }
        llmContext.extractedLocations.takeIf { it.isNotEmpty() }?.let {
            appendLine("Extracted locations: $it")
        }
        llmContext.memoryContext.takeIf { it.isNotBlank() }?.let {
            appendLine("Relevant memory:")
            appendLine(it.trim().take(600))
        }
    }

    private companion object {
        val JSONISH_LABEL_REGEX = Regex(
            "\"label\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        val JSONISH_ITEMS_ARRAY_REGEX = Regex(
            "\"items\"\\s*:\\s*\\[(.*?)]",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        val JSONISH_STRING_REGEX = Regex(
            "\"((?:\\\\.|[^\"\\\\])*)\"",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
    }
}

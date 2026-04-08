package com.theveloper.aura.engine.classifier

import android.content.Context
import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.engine.dsl.ChecklistDslItems
import com.theveloper.aura.engine.dsl.ChecklistItemDSL
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.llm.LLMService
import com.theveloper.aura.engine.llm.extractLikelyJsonBlock
import com.theveloper.aura.engine.llm.stripCodeFences
import com.theveloper.aura.engine.llm.normalizeTaskDslJson
import com.theveloper.aura.engine.skill.PromptProfile
import com.theveloper.aura.engine.skill.UiSkillRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
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
            prompt = buildPrompt(input, llmContext, baselineDraft)
        )
        if (firstAttempt != null && !needsRescue(firstAttempt)) {
            return firstAttempt
        }

        val focusedBaseDraft = firstAttempt ?: baselineDraft
        val focusedAttempt = completeRescuePrompt(
            service = service,
            prompt = buildFocusedRepairPrompt(input, llmContext, focusedBaseDraft)
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
        if (bestChecklistAwareAttempt != null && !needsRescue(bestChecklistAwareAttempt)) {
            return bestChecklistAwareAttempt
        }

        val checklistPatched = repairHollowChecklistWithItems(
            service = service,
            input = input,
            llmContext = llmContext,
            draft = bestChecklistAwareAttempt ?: baselineDraft
        )
        if (checklistPatched != null && !needsRescue(checklistPatched)) {
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

        return draft.components.none(::isMeaningfulComponent)
    }

    private fun buildPrompt(
        input: String,
        llmContext: LLMClassificationContext,
        draft: TaskDSLOutput
    ): String {
        val plannerPrompt = UiSkillRegistry.buildSystemPrompt(
            context = context,
            taskTypeHint = llmContext.detectedTaskType,
            profile = PromptProfile.LOCAL_COMPACT
        )

        return buildString {
            appendLine(plannerPrompt)
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
        prompt: String
    ): TaskDSLOutput? {
        val raw = runCatching {
            service.complete(prompt)
        }.getOrNull()?.trim().orEmpty()
        if (raw.isBlank()) return null

        return runCatching {
            auraJson.decodeFromString<TaskDSLOutput>(raw.normalizeTaskDslJson())
        }.getOrNull()
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
        if (checklistIndex == -1) return null

        val items = runCatching {
            service.complete(buildChecklistRepairPrompt(input, llmContext, draft))
        }.getOrNull()
            ?.let { raw -> parseChecklistRepairItems(raw, draft.title, input) }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val updatedComponents = draft.components.mapIndexed { index, component ->
            if (index != checklistIndex) {
                component
            } else {
                component.withChecklistItems(
                    items = items,
                    label = draft.title.ifBlank { component.config["label"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                )
            }
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
            prompt = buildNarrativeRebalancePrompt(input, llmContext, draft)
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

    private fun isMeaningfulComponent(component: ComponentDSL): Boolean {
        return when (component.type) {
            ComponentType.CHECKLIST -> {
                val items = ChecklistDslItems.parse(component.config)
                items.isNotEmpty() ||
                    component.needsClarification
            }

            ComponentType.NOTES ->
                !DraftContentQuality.isThinNotes(component.noteText())

            ComponentType.COUNTDOWN ->
                component.config["targetDate"]?.toString()?.toLongOrNull()?.let { it > 0L } == true ||
                    component.needsClarification

            else -> true
        }
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

        parseChecklistItemsFromJson(sanitized).takeIf { it.isNotEmpty() }?.let { return it }
        ChecklistInputExtraction.extract(sanitized).takeIf { it.isNotEmpty() }?.let { return it }

        val titleLower = title.trim().lowercase()
        val inputLower = input.trim().lowercase()

        return sanitized.lineSequence()
            .map(::normalizeChecklistCandidate)
            .filter { candidate ->
                candidate.isNotBlank() &&
                    candidate != "..." &&
                    !candidate.equals(titleLower, ignoreCase = true) &&
                    !candidate.equals(inputLower, ignoreCase = true) &&
                    !candidate.equals("final output", ignoreCase = true) &&
                    looksLikeChecklistCandidate(candidate)
            }
            .distinct()
            .toList()
    }

    private fun parseChecklistItemsFromJson(raw: String): List<String> {
        val element = runCatching {
            auraJson.parseToJsonElement(raw.extractLikelyJsonBlock())
        }.getOrNull() ?: return emptyList()

        return extractChecklistItemLabels(element)
    }

    private fun extractChecklistItemLabels(element: JsonElement): List<String> {
        return when (element) {
            is JsonArray -> element.mapNotNull(::extractChecklistItemLabel).distinct()

            is JsonObject -> {
                extractChecklistItemLabels(element["items"] ?: JsonArray(emptyList())).takeIf { it.isNotEmpty() }
                    ?: ((element["components"] as? JsonArray)
                        ?.firstOrNull()
                        ?.let(::extractChecklistItemLabels))
                    ?: ((element["config"] as? JsonObject)
                        ?.get("items")
                        ?.let(::extractChecklistItemLabels))
                    ?: emptyList()
            }

            else -> emptyList()
        }
    }

    private fun extractChecklistItemLabel(element: JsonElement): String? {
        return when (element) {
            is JsonPrimitive -> normalizeChecklistCandidate(element.contentOrNull.orEmpty()).takeIf(::looksLikeChecklistCandidate)
            is JsonObject -> {
                element["label"]?.jsonPrimitive?.contentOrNull
                    ?.let(::normalizeChecklistCandidate)
                    ?.takeIf(::looksLikeChecklistCandidate)
            }
            else -> null
        }
    }

    private fun normalizeChecklistCandidate(raw: String): String {
        return raw
            .trim()
            .replace(Regex("""^\[\s*[xX ]?\s*]\s+"""), "")
            .replace(Regex("""^(?:[-*•]\s+|\d+[.)]\s+)"""), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', ':')
    }

    private fun looksLikeChecklistCandidate(candidate: String): Boolean {
        val tokens = candidate.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size !in 1..5) return false
        return candidate.none { it == '?' || it == '!' || it == ':' }
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
        val normalizedCurrent = currentItems.map(::normalizeChecklistCandidate).filter(String::isNotBlank).distinct()
        val normalizedRefined = refinedItems.map(::normalizeChecklistCandidate).filter(String::isNotBlank).distinct()
        val currentKeys = normalizedCurrent.map(::checklistComparisonKey)
        val refinedKeys = normalizedRefined.map(::checklistComparisonKey)
        if (refinedKeys.isEmpty() || refinedKeys == currentKeys) return false

        val expandableCurrent = normalizedCurrent.filter { item -> looksExpandableChecklistItem(item, taskTitle) }
        if (expandableCurrent.isEmpty()) return false

        val expandableKeys = expandableCurrent.map(::checklistComparisonKey).toSet()
        val preservedConcreteKeys = currentKeys.filterNot { it in expandableKeys }
        if (!preservedConcreteKeys.all { it in refinedKeys }) return false

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
        val itemTokens = tokenizeChecklistText(item)
        if (itemTokens.size !in 2..6) return false

        val titleTokens = tokenizeChecklistText(taskTitle)
        if (titleTokens.isEmpty()) return false

        val overlapCount = itemTokens.count { token -> token in titleTokens }
        val overlapRatio = overlapCount.toFloat() / itemTokens.size.toFloat()
        return overlapCount >= 1 && overlapRatio >= 0.34f
    }

    private fun tokenizeChecklistText(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("""[^\p{L}\p{N}]+"""))
            .mapNotNull { token ->
                token.trim()
                    .takeIf { it.length >= 3 }
                    ?.removeSuffix("s")
            }
            .toSet()
    }

    private fun checklistComparisonKey(item: String): String {
        return normalizeChecklistCandidate(item).lowercase()
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
}

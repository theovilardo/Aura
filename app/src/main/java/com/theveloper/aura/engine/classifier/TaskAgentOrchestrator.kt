package com.theveloper.aura.engine.classifier

import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.ChecklistDslItems
import com.theveloper.aura.engine.dsl.ChecklistItemDSL
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.FunctionSkillDSL
import com.theveloper.aura.engine.dsl.SemanticFrame
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.llm.LLMService
import com.theveloper.aura.engine.llm.LLMTier
import com.theveloper.aura.engine.llm.extractLikelyJsonBlock
import com.theveloper.aura.engine.llm.normalizeTaskDslJson
import com.theveloper.aura.engine.llm.stripCodeFences
import com.theveloper.aura.engine.skill.PromptProfile
import com.theveloper.aura.engine.skill.SkillRegistry
import com.theveloper.aura.engine.skill.SkillPromptCards
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class TaskAgentOrchestrator @Inject constructor() {

    suspend fun orchestrate(
        service: LLMService,
        input: String,
        llmContext: LLMClassificationContext
    ): TaskDSLOutput? {
        val promptProfile = service.promptProfile()

        if (promptProfile == PromptProfile.LOCAL_COMPACT) {
            val plan = buildPlan(service, input, llmContext, promptProfile)
            if (plan != null && plan.uiSkills.isNotEmpty()) {
                completeLocalSkillPlan(
                    service = service,
                    input = input,
                    llmContext = llmContext,
                    plan = plan
                )?.let { return it }

                completeTaskPrompt(
                    service = service,
                    prompt = buildComposePrompt(input, llmContext, plan, promptProfile)
                )?.let { return it }
            }

            return completeTaskPrompt(
                service = service,
                prompt = buildDirectTaskPrompt(input, llmContext)
            )
        }

        if (service.prefersSinglePassTaskComposition()) {
            completeTaskPrompt(
                service = service,
                prompt = buildDirectTaskPrompt(input, llmContext)
            )?.let { return it }
        }

        val plan = buildPlan(service, input, llmContext, promptProfile) ?: return null
        if (plan.uiSkills.isEmpty()) return null

        return completeTaskPrompt(
            service = service,
            prompt = buildComposePrompt(input, llmContext, plan, promptProfile)
        )
    }

    private suspend fun buildPlan(
        service: LLMService,
        input: String,
        llmContext: LLMClassificationContext,
        promptProfile: PromptProfile
    ): TaskAgentPlan? {
        val raw = runCatching {
            service.complete(buildPlanPrompt(input, llmContext, promptProfile))
        }.getOrNull()?.trim().orEmpty()
        if (raw.isBlank()) return null

        return parseTaskAgentPlan(raw.extractLikelyJsonBlock())?.let { plan ->
            plan.copy(title = plan.title.ifBlank { input.trim() })
        }
    }

    private fun buildPlanPrompt(
        input: String,
        llmContext: LLMClassificationContext,
        promptProfile: PromptProfile
    ): String {
        if (promptProfile == PromptProfile.LOCAL_COMPACT) {
            val availableUi = SkillRegistry.availableUiSkills(llmContext.detectedTaskType)
            val availableFunction = SkillRegistry.availableFunctionSkills(llmContext.detectedTaskType)

            return buildString {
                appendLine("You are AURA's task orchestration agent.")
                appendLine("Choose the best UI skills for the task and return a compact task plan.")
                appendLine("All user-facing text MUST stay in the same language as the user's input.")
                appendLine("Prefer building a useful first version over asking clarifying questions.")
                appendLine("Concrete named items must become atomic visible items.")
                appendLine("Known bundles, ingredient sets, recipes, kits, or supply groups should be expanded into atomic items when commonly knowable.")
                appendLine("Use the minimum set of skills that fully solves the task.")
                appendLine("This plan is only for skill selection. Do not write checklist items, recipe steps, or notes content here.")
                appendLine("Return only valid JSON.")
                appendLine()
                appendLine("Plan JSON schema:")
                appendLine(
                    """{
  "title": "short task title",
  "type": "GENERAL|TRAVEL|HABIT|HEALTH|PROJECT|FINANCE|EVENT|GOAL",
  "uiSkills": ["checklist", "notes"],
  "functionSkills": ["structured-brief"]
}"""
                )
                appendLine()
                appendLine("UI skill cards:")
                appendLine(SkillPromptCards.renderUiSkillCards(availableUi, promptProfile))
                appendLine()
                appendLine("Function skill cards:")
                appendLine(SkillPromptCards.renderFunctionSkillCards(availableFunction, promptProfile))
                appendLine()
                appendLine("User input:")
                appendLine(input)
                appendLine()
                appendContextHints(llmContext)
            }
        }

        val availableUi = SkillRegistry.availableUiSkills(llmContext.detectedTaskType)
        val availableFunction = SkillRegistry.availableFunctionSkills(llmContext.detectedTaskType)

        return buildString {
            appendLine("You are AURA's task orchestration agent.")
            appendLine("Your job is to decide the best task UI, not just produce any valid JSON.")
            appendLine("All user-facing text MUST stay in the same language as the user's input.")
            appendLine("Avoid clarification if you can build a useful first version from the prompt.")
            appendLine("If the user asks for a concrete artifact that can be authored from general knowledge, choose UI that delivers the artifact itself.")
            appendLine("Do not turn authorable artifact requests into research plans, search tasks, or waiting states.")
            appendLine("When the prompt already contains concrete items, split them into atomic visible items.")
            appendLine("When multiple concrete item sets belong in one practical checklist, merge them into one checklist.")
            appendLine("If the user references a known recipe, packing set, supply bundle, or ingredient set, expand that reference into atomic items instead of keeping it as one umbrella label.")
            appendLine("Choose the minimum set of UI skills that gives the best outcome.")
            appendLine("Use notes only when they add meaningful value beyond the other selected UI.")
            appendLine("Use structured-brief only when the prompt is genuinely vague; do not use it for requests that already name the desired artifact.")
            appendLine("If a concrete artifact naturally includes both concrete items and explanatory text, select both checklist and notes.")
            appendLine("A teaser sentence, filler sentence, or promise of future content counts as hollow output.")
            appendLine("System hint lines may appear as [[preferred_title]], [[task_type_hint]], or [[clarification]].")
            appendLine("Treat them as machine hints or prior user answers, never as visible task content.")
            appendLine("Return only valid JSON.")
            appendLine()
            appendLine("Output schema:")
            appendLine(
                """{
  "title": "short task title",
  "type": "GENERAL|TRAVEL|HABIT|HEALTH|PROJECT|FINANCE|EVENT|GOAL",
  "semantic": {
    "action": "short verb phrase",
    "items": ["atomic item"],
    "subject": "context",
    "goal": "measurable result or empty string",
    "frequency": "DAILY|WEEKLY|"
  },
  "uiSkills": ["checklist", "notes"],
  "functionSkills": ["structured-brief"]
}"""
            )
            appendLine()
            appendLine("Available UI skills:")
            availableUi.forEach { definition ->
                appendLine("- ${definition.id}: ${definition.promptHint}")
            }
            appendLine()
            appendLine("Available function skills:")
            availableFunction.forEach { definition ->
                appendLine("- ${definition.id}: ${definition.promptHint}")
            }
            appendLine()
            appendLine("User input:")
            appendLine(input)
            appendLine()
            appendContextHints(llmContext)
        }
    }

    private fun buildComposePrompt(
        input: String,
        llmContext: LLMClassificationContext,
        plan: TaskAgentPlan,
        promptProfile: PromptProfile
    ): String {
        val selectedUiDefinitions = plan.uiSkills.mapNotNull { SkillRegistry.resolveUi(it) }
        val selectedFunctionDefinitions = plan.functionSkills.mapNotNull { SkillRegistry.resolveFunction(it) }

        return buildString {
            appendLine("You are AURA's task composition agent.")
            appendLine("Build the final ready-to-use task JSON from the approved task plan.")
            appendLine("All user-facing text MUST stay in the same language as the user's input.")
            appendLine("Use the approved plan as a starting point, not as a cage.")
            appendLine("If the approved plan would leave the task hollow, you may add or replace UI skills to deliver the best ready-to-use task.")
            appendLine("Use the selected skills intelligently and fully configure them.")
            appendLine("Never leave any selected skill hollow.")
            appendLine("Checklist items must be atomic. If the prompt references a known recipe, bundle, kit, or ingredient set, expand it into separate checklist items instead of one umbrella row.")
            appendLine("For concrete artifacts such as recipes, guides, instructions, agendas, explanations, drafts, or summaries, include the artifact itself now.")
            appendLine("Do not output research plans, search steps, fill-in-yourself prompts, or placeholders when the model can author the content directly.")
            appendLine("If both checklist and notes are selected, use checklist for concrete items and notes for written instructions or narrative content.")
            appendLine("Return only the final task JSON. Do not include analysis or semantic sections.")
            appendLine("System hint lines may appear as [[preferred_title]], [[task_type_hint]], or [[clarification]].")
            appendLine("Use them as machine hints or prior user answers, never as visible task content.")
            appendLine("Return only valid JSON. No markdown fences. No explanations.")
            appendLine()
            appendLine("Final task JSON schema:")
            appendLine(
                """{
  "title": "required",
  "type": "GENERAL|TRAVEL|HABIT|HEALTH|PROJECT|FINANCE|EVENT|GOAL",
  "priority": 0,
  "targetDateMs": 0,
  "skills": [
    {
      "skill": "ui-skill-id",
      "sortOrder": 0,
      "populatedFromInput": false,
      "needsClarification": false,
      "config": {}
    }
  ],
  "functionSkills": [
    {
      "skill": "function-skill-id",
      "enabled": true,
      "config": {}
    }
  ],
  "reminders": [],
  "fetchers": []
}"""
            )
            appendLine()
            appendLine("Approved task plan:")
            appendLine(auraJson.encodeToString(TaskAgentPlan.serializer(), plan))
            appendLine()
            appendLine("Selected UI skill guidance:")
            appendLine(SkillPromptCards.renderUiSkillCards(selectedUiDefinitions, promptProfile))
            appendLine()
            if (selectedFunctionDefinitions.isNotEmpty()) {
                appendLine("Selected function skills:")
                appendLine(SkillPromptCards.renderFunctionSkillCards(selectedFunctionDefinitions, promptProfile))
                appendLine()
            }
            appendLine("Original user input:")
            appendLine(input)
            appendLine()
            appendContextHints(llmContext)
        }
    }

    private fun buildDirectTaskPrompt(
        input: String,
        llmContext: LLMClassificationContext
    ): String {
        val availableUi = SkillRegistry.availableUiSkills(llmContext.detectedTaskType)
        val availableFunction = SkillRegistry.availableFunctionSkills(llmContext.detectedTaskType)

        return buildString {
            appendLine("You are AURA's local task composer.")
            appendLine("Build the best ready-to-use task UI in a single pass.")
            appendLine("All user-facing text MUST stay in the same language as the user's input.")
            appendLine("Choose the minimum set of UI skills that fully solves the task.")
            appendLine("Prefer a useful first version over clarification whenever possible.")
            appendLine("If the user asks for a concrete artifact, deliver it now through the right UI.")
            appendLine("Concrete named items must become atomic visible items.")
            appendLine("Known bundles, ingredient sets, recipes, kits, or supply groups should be expanded into atomic items when commonly knowable.")
            appendLine("If the task mixes concrete items with written instructions or narrative content, use checklist for the concrete items and notes for the written content.")
            appendLine("Function skills improve content; they do not replace UI.")
            appendLine("Return only the final task JSON. No markdown fences. No explanations.")
            appendLine()
            appendLine("Final task JSON schema:")
            appendLine(
                """{
  "title": "required",
  "type": "GENERAL|TRAVEL|HABIT|HEALTH|PROJECT|FINANCE|EVENT|GOAL",
  "priority": 0,
  "targetDateMs": 0,
  "skills": [
    {
      "skill": "ui-skill-id",
      "sortOrder": 0,
      "populatedFromInput": false,
      "needsClarification": false,
      "config": {}
    }
  ],
  "functionSkills": [
    {
      "skill": "function-skill-id",
      "enabled": true,
      "config": {}
    }
  ],
  "reminders": [],
  "fetchers": []
}"""
            )
            appendLine()
            appendLine("Available UI skill cards:")
            appendLine(SkillPromptCards.renderUiSkillCards(availableUi, PromptProfile.LOCAL_COMPACT))
            appendLine()
            appendLine("Available function skill cards:")
            appendLine(SkillPromptCards.renderFunctionSkillCards(availableFunction, PromptProfile.LOCAL_COMPACT))
            appendLine()
            appendLine("User input:")
            appendLine(input)
            appendLine()
            appendContextHints(llmContext)
        }
    }

    private suspend fun completeTaskPrompt(
        service: LLMService,
        prompt: String
    ): TaskDSLOutput? {
        val raw = runCatching { service.complete(prompt) }.getOrNull()?.trim().orEmpty()
        if (raw.isBlank()) return null

        return runCatching {
            auraJson.decodeFromString<TaskDSLOutput>(raw.normalizeTaskDslJson())
        }.getOrNull()
    }

    private suspend fun completeLocalSkillPlan(
        service: LLMService,
        input: String,
        llmContext: LLMClassificationContext,
        plan: TaskAgentPlan
    ): TaskDSLOutput? {
        if (plan.uiSkills.isEmpty()) return null
        if (plan.uiSkills.any { it !in LOCAL_SKILL_FILLERS }) return null

        val notesComponent = if ("notes" in plan.uiSkills) {
            fillLocalNotesSkill(service, input, llmContext, plan, sortOrder = 0)
        } else {
            null
        }
        val notesMarkdown = notesComponent?.config?.get("text")?.stringContent().orEmpty()

        val components = plan.uiSkills.mapIndexedNotNull { index, skillId ->
            when (skillId) {
                "notes" -> notesComponent?.copy(sortOrder = index)
                else -> buildLocalSkillComponent(
                    service = service,
                    input = input,
                    llmContext = llmContext,
                    plan = plan,
                    skillId = skillId,
                    sortOrder = index,
                    notesMarkdown = notesMarkdown
                )
            }
        }
        if (components.size != plan.uiSkills.size) return null

        val functionSkills = plan.functionSkills
            .mapNotNull { skillId -> SkillRegistry.resolveFunction(skillId)?.id }
            .distinct()
            .map { skillId ->
                FunctionSkillDSL(
                    skillId = skillId,
                    enabled = true
                )
            }

        return TaskDSLOutput(
            title = plan.title.ifBlank { input.trim() },
            type = parseTaskType(plan.type),
            priority = 0,
            targetDateMs = null,
            components = components,
            functionSkills = functionSkills
        )
    }

    private suspend fun buildLocalSkillComponent(
        service: LLMService,
        input: String,
        llmContext: LLMClassificationContext,
        plan: TaskAgentPlan,
        skillId: String,
        sortOrder: Int,
        notesMarkdown: String = ""
    ): ComponentDSL? {
        return when (skillId) {
            "checklist" -> fillLocalChecklistSkill(service, input, llmContext, plan, sortOrder, notesMarkdown)
            "notes" -> fillLocalNotesSkill(service, input, llmContext, plan, sortOrder)
            else -> null
        }
    }

    private suspend fun fillLocalChecklistSkill(
        service: LLMService,
        input: String,
        llmContext: LLMClassificationContext,
        plan: TaskAgentPlan,
        sortOrder: Int,
        notesMarkdown: String
    ): ComponentDSL? {
        val raw = runCatching {
            service.complete(buildLocalChecklistPrompt(input, llmContext, plan))
        }.getOrNull()?.trim().orEmpty()
        val primaryItems = parseChecklistSkillItemsFromRaw(raw)
        val items = primaryItems
            .takeIf { checklistItemsLookUsable(it, plan.title) }
            ?: notesMarkdown.takeIf { it.isNotBlank() }?.let { notes ->
                runCatching {
                    service.complete(buildLocalChecklistFromNotesPrompt(input, llmContext, plan, notes))
                }.getOrNull()
                    ?.trim()
                    .orEmpty()
                    .let(::parseChecklistSkillItemsFromRaw)
                    .takeIf { fallbackItems -> checklistItemsLookUsable(fallbackItems, plan.title) }
            }
            ?: return null

        val config = ChecklistDslItems.withItems(
            config = JsonObject(
                mapOf(
                    "config_type" to JsonPrimitive(ComponentType.CHECKLIST.name),
                    "label" to JsonPrimitive(plan.title.ifBlank { "Checklist" }),
                    "allowAddItems" to JsonPrimitive(true)
                )
            ),
            items = items
        )

        return ComponentDSL(
            skillId = "checklist",
            type = ComponentType.CHECKLIST,
            sortOrder = sortOrder,
            config = config,
            populatedFromInput = true,
            needsClarification = false
        )
    }

    private suspend fun fillLocalNotesSkill(
        service: LLMService,
        input: String,
        llmContext: LLMClassificationContext,
        plan: TaskAgentPlan,
        sortOrder: Int
    ): ComponentDSL? {
        val raw = runCatching {
            service.complete(buildLocalNotesPrompt(input, llmContext, plan))
        }.getOrNull()?.trim().orEmpty()
        if (raw.isBlank()) return null

        val markdown = extractNotesMarkdown(raw)
            ?.takeIf { !DraftContentQuality.isThinNotes(it) }
            ?: return null

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
            populatedFromInput = true,
            needsClarification = false
        )
    }

    private fun buildLocalChecklistPrompt(
        input: String,
        llmContext: LLMClassificationContext,
        plan: TaskAgentPlan
    ): String {
        return buildString {
            appendLine("You are AURA's checklist skill filler.")
            appendLine("Fill only the checklist UI config for this task.")
            appendLine("All user-facing text MUST stay in the same language as the user's input.")
            appendLine("Focus on concrete things to buy, bring, prepare, pack, review, or complete.")
            appendLine("If the task also includes written instructions, recipe steps, or narrative guidance, exclude that written content from the checklist.")
            appendLine("Expand commonly known ingredient sets, recipes, bundles, or kits into atomic checklist items when knowable.")
            appendLine("Return only checklist items, one per line. No JSON. No markdown fences. No explanations.")
            appendLine("Do not output headings, labels, recipe steps, umbrella categories, or abstract placeholders.")
            appendLine("Bad output example:")
            appendLine("Pancake ingredients")
            appendLine("Recipe steps")
            appendLine()
            appendLine("Good output example:")
            appendLine("Flour")
            appendLine("Milk")
            appendLine("Eggs")
            appendLine("Butter")
            appendLine()
            appendLine("Task title:")
            appendLine(plan.title.ifBlank { input.trim() })
            appendLine()
            appendLine("Original user input:")
            appendLine(input)
            appendLine()
            appendContextHints(llmContext)
        }
    }

    private fun buildLocalChecklistFromNotesPrompt(
        input: String,
        llmContext: LLMClassificationContext,
        plan: TaskAgentPlan,
        notesMarkdown: String
    ): String {
        return buildString {
            appendLine("You are AURA's checklist extraction agent.")
            appendLine("Using the task request and the authored notes below, return only the concrete checklist items that belong in the checklist.")
            appendLine("All user-facing text MUST stay in the same language as the user's input.")
            appendLine("Return only checklist items, one per line. No JSON. No markdown fences. No explanations.")
            appendLine("Exclude recipe steps, headings, and narrative sentences.")
            appendLine("If the notes mention ingredients inside the recipe steps, infer those ingredients into the checklist.")
            appendLine()
            appendLine("Task title:")
            appendLine(plan.title.ifBlank { input.trim() })
            appendLine()
            appendLine("Original user input:")
            appendLine(input)
            appendLine()
            appendLine("Authored notes:")
            appendLine(notesMarkdown)
            appendLine()
            appendContextHints(llmContext)
        }
    }

    private fun buildLocalNotesPrompt(
        input: String,
        llmContext: LLMClassificationContext,
        plan: TaskAgentPlan
    ): String {
        val hasChecklist = "checklist" in plan.uiSkills

        return buildString {
            appendLine("You are AURA's notes skill filler.")
            appendLine("Fill only the notes UI content for this task.")
            appendLine("All user-facing text MUST stay in the same language as the user's input.")
            appendLine("Return only markdown. No JSON. No markdown fences. No explanations.")
            appendLine("Write the real user-facing narrative content now.")
            if (hasChecklist) {
                appendLine("Another skill will cover the checklist. Do not include a shopping list or ingredients list here.")
                appendLine("Focus this note on the written instructions, recipe steps, and useful tips.")
            }
            appendLine("Do not output placeholders, teasers, or abstract labels.")
            appendLine()
            appendLine("Task title:")
            appendLine(plan.title.ifBlank { input.trim() })
            appendLine()
            appendLine("Original user input:")
            appendLine(input)
            appendLine()
            appendContextHints(llmContext)
        }
    }

    private fun parseLocalSkillObject(raw: String): JsonObject? {
        val candidate = raw.extractLikelyJsonBlock()
        return runCatching {
            auraJson.parseToJsonElement(candidate)
        }.getOrNull() as? JsonObject
    }

    private fun extractSkillConfigObject(
        root: JsonObject,
        skillId: String
    ): JsonObject? {
        if (root["items"] != null || root["text"] != null || root["label"] != null) {
            return root
        }

        (root["config"] as? JsonObject)?.let { return it }

        val skills = root["skills"] as? JsonArray ?: return null
        val matchingSkill = skills
            .mapNotNull { it as? JsonObject }
            .firstOrNull { element ->
                element["skill"].stringContent().equals(skillId, ignoreCase = true)
            }
            ?: return null

        return (matchingSkill["config"] as? JsonObject) ?: matchingSkill
    }

    private fun parseChecklistSkillItems(element: JsonElement?): List<ChecklistItemDSL> {
        return when (element) {
            is JsonArray -> element.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.contentOrNull
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::ChecklistItemDSL)

                    is JsonObject -> {
                        val label = item["label"].stringContent()
                        if (label.isBlank()) {
                            null
                        } else {
                            ChecklistItemDSL(
                                label = label,
                                isSuggested = item["isSuggested"]?.jsonPrimitive?.booleanOrNull ?: false
                            )
                        }
                    }

                    else -> null
                }
            }

            is JsonPrimitive -> element.contentOrNull
                ?.lines()
                ?.map { line -> line.trim() }
                ?.filter { line -> line.isNotBlank() }
                ?.map(::ChecklistItemDSL)
                .orEmpty()

            else -> emptyList()
        }.distinctBy { item -> item.label.lowercase() }
    }

    private fun parseChecklistSkillItemsFromRaw(raw: String): List<ChecklistItemDSL> {
        parseLocalSkillObject(raw)
            ?.let { root ->
                val configRoot = extractSkillConfigObject(root, skillId = "checklist") ?: root
                parseChecklistSkillItems(configRoot["items"])
                    .takeIf { it.isNotEmpty() }
                    ?.let { return it }
            }

        return raw.stripCodeFences()
            .lineSequence()
            .map(::normalizeChecklistLine)
            .filter(::looksLikeChecklistLine)
            .map(::ChecklistItemDSL)
            .distinctBy { item -> item.label.lowercase() }
            .toList()
    }

    private fun normalizeChecklistLine(line: String): String {
        return line
            .trim()
            .replace(Regex("""^\s*(?:[-*•]|\d+[.)])\s+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '.', ':')
    }

    private fun looksLikeChecklistLine(line: String): Boolean {
        if (line.isBlank()) return false
        val normalized = line.lowercase()
        if (normalized in setOf("true", "false", "null")) return false
        if (normalized.endsWith("skill filler")) return false
        if (normalized in setOf("shopping list", "recipe", "ingredients", "shopping list & recipe")) return false
        if (line.contains('?')) return false
        if (line.split(Regex("\\s+")).size > 8) return false
        return true
    }

    private fun checklistItemsLookUsable(
        items: List<ChecklistItemDSL>,
        taskTitle: String
    ): Boolean {
        if (items.isEmpty()) return false
        val labels = items.map { it.label.trim() }.filter { it.isNotBlank() }
        if (labels.isEmpty()) return false
        if (labels.any { label -> label.lowercase() in setOf("true", "false", "null") }) return false

        val titleTokens = tokenizeChecklistText(taskTitle)
        val informativeCount = labels.count { label ->
            val itemTokens = tokenizeChecklistText(label)
            val concreteTokens = itemTokens - titleTokens - ABSTRACT_CONTAINER_TOKENS
            concreteTokens.isNotEmpty()
        }
        if (informativeCount == 0) return false

        val genericOverlapRatio = labels.count { label ->
            val itemTokens = tokenizeChecklistText(label)
            itemTokens.isNotEmpty() &&
                titleTokens.isNotEmpty() &&
                itemTokens.count { token -> token in titleTokens }.toFloat() / itemTokens.size.toFloat() >= 0.67f
        }.toFloat() / labels.size.toFloat()

        return !(labels.size <= 3 && genericOverlapRatio >= 0.67f)
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

    private fun extractNotesMarkdown(raw: String): String? {
        parseLocalSkillObject(raw)
            ?.let { root ->
                val configRoot = extractSkillConfigObject(root, skillId = "notes") ?: root
                configRoot["text"].stringContent()
                    .takeIf { it.isNotBlank() }
                    ?.let { return DraftContentQuality.sanitizeGeneratedText(it) }
            }

        return raw.stripCodeFences()
            .trim()
            .removePrefix("Final task JSON:")
            .trim()
            .lineSequence()
            .dropWhile { line ->
                val normalized = line.trim().lowercase()
                normalized.isBlank() || normalized.endsWith("skill filler.")
            }
            .joinToString("\n")
            .trim()
            .let(DraftContentQuality::sanitizeGeneratedText)
            .takeIf { it.isNotBlank() }
    }

    private fun parseTaskType(rawType: String): TaskType {
        return runCatching { TaskType.valueOf(rawType.uppercase()) }
            .getOrDefault(TaskType.GENERAL)
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

    private fun LLMService.promptProfile(): PromptProfile {
        return if (tier == LLMTier.GROQ_API || tier == LLMTier.RULES_ONLY) {
            PromptProfile.DEFAULT
        } else {
            PromptProfile.LOCAL_COMPACT
        }
    }

    private fun LLMService.prefersSinglePassTaskComposition(): Boolean {
        return tier != LLMTier.GROQ_API && tier != LLMTier.RULES_ONLY
    }

    private companion object {
        val LOCAL_SKILL_FILLERS = setOf("checklist", "notes")
        val ABSTRACT_CONTAINER_TOKENS = setOf(
            "ingredient", "ingredients", "ingrediente", "ingredientes",
            "item", "items", "step", "steps", "paso", "pasos",
            "recipe", "recipes", "receta", "recetas",
            "shopping", "compras", "compra", "list", "lista", "listas",
            "mix", "mixture", "mezcla", "bundle", "kit", "set", "supply", "supplies"
        )
    }
}

@Serializable
internal data class TaskAgentPlan(
    val title: String,
    val type: String,
    val semantic: TaskAgentSemantic = TaskAgentSemantic(),
    val uiSkills: List<String> = emptyList(),
    val functionSkills: List<String> = emptyList()
)

@Serializable
internal data class TaskAgentSemantic(
    val action: String = "",
    val items: List<String> = emptyList(),
    val subject: String = "",
    val goal: String = "",
    val frequency: String = ""
) {
    fun toSemanticFrame(): SemanticFrame {
        return SemanticFrame(
            action = action,
            items = items,
            subject = subject,
            goal = goal,
            frequency = frequency
        )
    }
}

internal fun parseTaskAgentPlan(rawJson: String): TaskAgentPlan? {
    val root = runCatching {
        auraJson.parseToJsonElement(rawJson)
    }.getOrNull() as? JsonObject ?: return null

    val title = root["title"].stringContent()
    val rawType = root["type"].stringContent().uppercase()
    val type = rawType.takeIf { candidate ->
        candidate.isNotBlank() && runCatching {
            enumValueOf<com.theveloper.aura.domain.model.TaskType>(candidate)
        }.isSuccess
    } ?: com.theveloper.aura.domain.model.TaskType.GENERAL.name
    val semantic = (root["semantic"] as? JsonObject).toTaskAgentSemantic()
    val uiSkills = root["uiSkills"].stringList()
        .mapNotNull { skillId -> SkillRegistry.resolveUi(skillId)?.id }
        .distinct()
    val functionSkills = root["functionSkills"].stringList()
        .mapNotNull { skillId -> SkillRegistry.resolveFunction(skillId)?.id }
        .distinct()

    return TaskAgentPlan(
        title = title,
        type = type,
        semantic = semantic,
        uiSkills = uiSkills,
        functionSkills = functionSkills
    )
}

private fun JsonObject?.toTaskAgentSemantic(): TaskAgentSemantic {
    this ?: return TaskAgentSemantic()
    return TaskAgentSemantic(
        action = this["action"].stringContent(),
        items = this["items"].stringList(),
        subject = this["subject"].stringContent(),
        goal = this["goal"].stringContent(),
        frequency = this["frequency"].stringContent()
    )
}

private fun JsonElement?.stringList(): List<String> {
    return when (this) {
        is JsonArray -> this.mapNotNull { element ->
            (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        }

        is JsonPrimitive -> listOfNotNull(contentOrNull?.trim()?.takeIf { it.isNotBlank() })
        else -> emptyList()
    }
}

private fun JsonElement?.stringContent(): String {
    return (this as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
}

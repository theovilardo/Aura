package com.theveloper.aura.engine.classifier

import android.content.Context
import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.engine.dsl.SemanticFrame
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.llm.LLMService
import com.theveloper.aura.engine.llm.extractLikelyJsonBlock
import com.theveloper.aura.engine.llm.normalizeTaskDslJson
import com.theveloper.aura.engine.skill.SkillRegistry
import com.theveloper.aura.engine.skill.UiSkillRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Singleton
class TaskAgentOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun orchestrate(
        service: LLMService,
        input: String,
        llmContext: LLMClassificationContext
    ): TaskDSLOutput? {
        val plan = buildPlan(service, input, llmContext) ?: return null
        if (plan.uiSkills.isEmpty()) return null

        val composed = runCatching {
            service.complete(buildComposePrompt(input, llmContext, plan))
        }.getOrNull()?.trim().orEmpty()
        if (composed.isBlank()) return null

        return runCatching {
            auraJson.decodeFromString<TaskDSLOutput>(composed.normalizeTaskDslJson())
        }.getOrNull()
    }

    private suspend fun buildPlan(
        service: LLMService,
        input: String,
        llmContext: LLMClassificationContext
    ): TaskAgentPlan? {
        val raw = runCatching {
            service.complete(buildPlanPrompt(input, llmContext))
        }.getOrNull()?.trim().orEmpty()
        if (raw.isBlank()) return null

        return parseTaskAgentPlan(raw.extractLikelyJsonBlock())?.let { plan ->
            plan.copy(title = plan.title.ifBlank { input.trim() })
        }
    }

    private fun buildPlanPrompt(
        input: String,
        llmContext: LLMClassificationContext
    ): String {
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
        }
    }

    private fun buildComposePrompt(
        input: String,
        llmContext: LLMClassificationContext,
        plan: TaskAgentPlan
    ): String {
        val selectedUiDefinitions = plan.uiSkills.mapNotNull { SkillRegistry.resolveUi(it) }
        val selectedFunctionDefinitions = plan.functionSkills.mapNotNull { SkillRegistry.resolveFunction(it) }

        return buildString {
            appendLine("You are AURA's task composition agent.")
            appendLine("Build the final task JSON from the approved task plan.")
            appendLine("All user-facing text MUST stay in the same language as the user's input.")
            appendLine("Use the approved plan as a starting point, not as a cage.")
            appendLine("If the approved plan would leave the task hollow, you may add or replace UI skills to deliver the best ready-to-use task.")
            appendLine("Use the selected skills intelligently and fully configure them.")
            appendLine("Never leave checklist items empty if the prompt already contains enough information.")
            appendLine("Never leave notes empty if you selected notes.")
            appendLine("For concrete artifacts such as recipes, guides, instructions, agendas, explanations, drafts, or summaries, include the artifact itself now.")
            appendLine("Do not output research plans, search steps, fill-in-yourself prompts, or placeholders when the model can author the content directly.")
            appendLine("A teaser sentence, filler sentence, or promise of future content is invalid. Include the real content now.")
            appendLine("System hint lines may appear as [[preferred_title]], [[task_type_hint]], or [[clarification]].")
            appendLine("Use them as machine hints or prior user answers, never as visible task content.")
            appendLine("Return only valid JSON. No markdown fences. No explanations.")
            appendLine()
            appendLine("Final output schema:")
            appendLine(
                """{
  "analysis": {
    "intent": "short verb phrase",
    "constraints": ["short constraint"],
    "ui_skills_needed": ["ui-skill-id"],
    "function_skills_needed": ["function-skill-id"]
  },
  "semantic": {
    "action": "short verb phrase",
    "items": ["atomic item"],
    "subject": "context",
    "goal": "measurable result or empty string",
    "frequency": "DAILY|WEEKLY|"
  },
  "task": {
    "title": "required",
    "type": "GENERAL|TRAVEL|HABIT|HEALTH|PROJECT|FINANCE|EVENT|GOAL",
    "priority": 0,
    "targetDateMs": 0,
    "skills": [],
    "functionSkills": [],
    "reminders": [],
    "fetchers": []
  }
}"""
            )
            appendLine()
            appendLine("Approved task plan:")
            appendLine(auraJson.encodeToString(TaskAgentPlan.serializer(), plan))
            appendLine()
            appendLine("Selected UI skill guidance:")
            selectedUiDefinitions.forEach { definition ->
                val document = UiSkillRegistry.loadDocument(context, definition)
                appendLine("- ${definition.id}: ${document.description}")
                UiSkillRegistry.loadPromptGuidance(context, definition).forEach { line ->
                    appendLine("  $line")
                }
                appendLine()
            }
            if (selectedFunctionDefinitions.isNotEmpty()) {
                appendLine("Selected function skills:")
                selectedFunctionDefinitions.forEach { definition ->
                    appendLine("- ${definition.id}: ${definition.promptHint}")
                }
                appendLine()
            }
            appendLine("Original user input:")
            appendLine(input)
            appendLine()
            llmContext.extractedDates.takeIf { it.isNotEmpty() }?.let {
                appendLine("Extracted dates (epoch ms): $it")
            }
            llmContext.extractedNumbers.takeIf { it.isNotEmpty() }?.let {
                appendLine("Extracted numbers: $it")
            }
            llmContext.extractedLocations.takeIf { it.isNotEmpty() }?.let {
                appendLine("Extracted locations: $it")
            }
        }
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

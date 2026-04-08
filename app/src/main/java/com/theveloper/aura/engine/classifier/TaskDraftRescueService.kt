package com.theveloper.aura.engine.classifier

import android.content.Context
import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.engine.dsl.ChecklistDslItems
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.llm.LLMService
import com.theveloper.aura.engine.llm.stripCodeFences
import com.theveloper.aura.engine.llm.normalizeTaskDslJson
import com.theveloper.aura.engine.skill.UiSkillRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
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
        if (!needsRescue(draft)) return null

        val firstAttempt = completeRescuePrompt(
            service = service,
            prompt = buildPrompt(input, llmContext, draft)
        )
        if (firstAttempt != null && !needsRescue(firstAttempt)) {
            return firstAttempt
        }

        val focusedBaseDraft = firstAttempt ?: draft
        val focusedAttempt = completeRescuePrompt(
            service = service,
            prompt = buildFocusedRepairPrompt(input, llmContext, focusedBaseDraft)
        )

        val bestStructuredAttempt = focusedAttempt ?: firstAttempt
        if (bestStructuredAttempt != null && !needsRescue(bestStructuredAttempt)) {
            return bestStructuredAttempt
        }

        val contentPatched = repairHollowNotesWithMarkdown(
            service = service,
            input = input,
            llmContext = llmContext,
            draft = bestStructuredAttempt ?: draft
        )

        return contentPatched ?: bestStructuredAttempt
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
            taskTypeHint = llmContext.detectedTaskType
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
}

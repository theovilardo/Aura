package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.TaskComponentCatalog
import com.theveloper.aura.engine.dsl.TaskComponentContext
import com.theveloper.aura.engine.dsl.TaskDSLOutput

object TaskDSLBuilder {

    fun buildDeterministic(
        input: String,
        intentResult: IntentResult,
        entities: ExtractedEntities
    ): TaskDSLOutput {
        val taskType = intentResult.taskType
        val targetDateMs = entities.dateTimes.firstOrNull()
        val title = buildTitle(input, taskType)
        val context = TaskComponentContext(
            input = input,
            title = title,
            taskType = taskType,
            targetDateMs = targetDateMs,
            numbers = entities.numbers,
            locations = entities.locations
        )
        val presetIds = defaultTemplateIdsFor(taskType, entities, input)
        val (components, reminders) = TaskComponentCatalog.buildSelection(
            templateIds = presetIds,
            now = System.currentTimeMillis(),
            context = context
        )

        return TaskDSLOutput(
            title = title,
            type = taskType,
            targetDateMs = targetDateMs,
            components = components,
            reminders = reminders
        )
    }

    fun buildFallback(input: String, context: LLMClassificationContext): TaskDSLOutput {
        // No intentHint available — always fall back to GENERAL as the safest default
        return buildDeterministic(
            input = input,
            intentResult = IntentResult(TaskType.GENERAL, context.intentConfidence),
            entities = ExtractedEntities(
                dateTimes = context.extractedDates,
                numbers = context.extractedNumbers,
                locations = context.extractedLocations
            )
        )
    }

    fun buildTitle(input: String, taskType: TaskType): String {
        return input.trim()
            .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
            .ifBlank { taskType.name.lowercase().replaceFirstChar { it.titlecase() } }
    }

    /**
     * Returns the default template IDs for a given task type.
     *
     * Template selection uses language-agnostic structural signals only:
     *   - Extracted dates (from ML EntityExtractor) → date-sensitive templates
     *   - List/multi-line patterns → checklist templates
     *   - Set×rep / unit annotations → fitness metric templates
     * No keyword detection tied to any specific language.
     */
    fun defaultTemplateIdsFor(taskType: TaskType, entities: ExtractedEntities, input: String = ""): List<String> {
        val hasDate = entities.dateTimes.isNotEmpty()
        val hasMultipleDates = entities.dateTimes.size > 1
        val hasListStructure = LIST_STRUCTURE.containsMatchIn(input)
        val isMultiLine = input.lines().count { it.isNotBlank() } > 2
        val hasSetRep = SET_REP.containsMatchIn(input)

        return when (taskType) {
            TaskType.TRAVEL  -> listOf("travel_countdown", "packing_checklist", "metric_budget_target")
            TaskType.HABIT   -> listOf(if (hasMultipleDates) "habit_weekly" else "habit_daily", "journal_reflection")
            TaskType.HEALTH  -> listOf(if (hasSetRep) "metric_weight" else "metric_weight", "progress_manual", "notes_clinic")
            TaskType.PROJECT -> listOf("progress_milestones", "action_checklist", "notes_meeting")
            TaskType.FINANCE -> listOf("metric_budget_target", "progress_budget")
            TaskType.EVENT   -> listOf("event_countdown", "event_notes")
            TaskType.GOAL    -> listOf("goal_progress", "goal_milestones_checklist", "goal_notes")
            TaskType.GENERAL -> buildList {
                if (hasDate) add("deadline_countdown")
                if (hasListStructure || isMultiLine) add("action_checklist")
                add("notes_brain_dump")
            }
        }
    }

    private val SET_REP = Regex(
        """\b\d+\s*[xX×]\s*\d+\b|\b\d+\s*(sets?|reps?|series?)\b""",
        RegexOption.IGNORE_CASE
    )

    private val LIST_STRUCTURE = Regex(
        """(?:^|\n)\s*[-*•]\s+\S|(?:^|\n)\s*\d+[.)]\s+\S"""
    )
}

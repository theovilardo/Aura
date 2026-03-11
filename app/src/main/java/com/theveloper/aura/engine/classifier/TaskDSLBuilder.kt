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
        val presetIds = presetIdsFor(taskType, input)
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
        val fallbackType = context.intentHint ?: TaskType.GENERAL
        return buildDeterministic(
            input = input,
            intentResult = IntentResult(fallbackType, context.intentConfidence),
            entities = ExtractedEntities(
                dateTimes = context.extractedDates,
                numbers = context.extractedNumbers,
                locations = context.extractedLocations
            )
        )
    }

    private fun buildTitle(input: String, taskType: TaskType): String {
        return input.trim()
            .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
            .ifBlank { taskType.name.lowercase().replaceFirstChar { it.titlecase() } }
    }

    private fun presetIdsFor(taskType: TaskType, input: String): List<String> {
        return when (taskType) {
            TaskType.TRAVEL -> listOf("travel_countdown", "packing_checklist")
            TaskType.HABIT -> listOf(if (isWeekly(input)) "habit_weekly" else "habit_daily")
            TaskType.HEALTH -> listOf(metricPreset(input), "notes_brain_dump")
            TaskType.PROJECT -> listOf("progress_milestones", "action_checklist", "notes_meeting")
            TaskType.FINANCE -> listOf("feed_exchange", "notes_meeting")
            TaskType.GENERAL -> buildList {
                if (containsDate(input)) add("deadline_countdown")
                add("notes_brain_dump")
            }
        }
    }

    private fun metricPreset(input: String): String {
        return when {
            Regex("\\bagua\\b|hidrat", RegexOption.IGNORE_CASE).containsMatchIn(input) -> "metric_hydration"
            Regex("\\bpasos\\b|camina|actividad", RegexOption.IGNORE_CASE).containsMatchIn(input) -> "metric_steps"
            else -> "metric_weight"
        }
    }

    private fun isWeekly(input: String): Boolean {
        return Regex("\\bsemana\\b|semanal", RegexOption.IGNORE_CASE).containsMatchIn(input)
    }

    private fun containsDate(input: String): Boolean {
        return Regex("\\bmañana\\b|hoy\\b|viernes\\b|lunes\\b|martes\\b|miércoles\\b|jueves\\b|sábado\\b|domingo\\b|enero\\b|febrero\\b|marzo\\b|abril\\b|mayo\\b|junio\\b|julio\\b|agosto\\b|septiembre\\b|octubre\\b|noviembre\\b|diciembre\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(input)
    }
}

package com.theveloper.aura.engine.skill

import android.content.Context
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class UiSkillDefinition(
    val id: String,
    val assetPath: String,
    val componentType: ComponentType,
    val displayName: String,
    val shortLabel: String,
    val promptHint: String,
    val supportedTaskTypes: Set<TaskType>
)

data class UiSkillDocument(
    val name: String,
    val description: String,
    val body: String
)

data class UiSkillValidation(
    val definition: UiSkillDefinition?,
    val isValid: Boolean,
    val reason: String? = null
)

object UiSkillRegistry {

    private val allTaskTypes = TaskType.entries.toSet()

    val all: List<UiSkillDefinition> = listOf(
        UiSkillDefinition(
            id = "checklist",
            assetPath = "ui-skills/checklist/SKILL.md",
            componentType = ComponentType.CHECKLIST,
            displayName = "Checklist",
            shortLabel = "List",
            promptHint = "Concrete items, steps, ingredients, packing, or shopping lists.",
            supportedTaskTypes = allTaskTypes - setOf(TaskType.HABIT)
        ),
        UiSkillDefinition(
            id = "progress-bar",
            assetPath = "ui-skills/progress-bar/SKILL.md",
            componentType = ComponentType.PROGRESS_BAR,
            displayName = "Progress",
            shortLabel = "Track",
            promptHint = "Stages, milestones, or overall completion state.",
            supportedTaskTypes = setOf(
                TaskType.GENERAL,
                TaskType.TRAVEL,
                TaskType.HEALTH,
                TaskType.PROJECT,
                TaskType.FINANCE,
                TaskType.GOAL
            )
        ),
        UiSkillDefinition(
            id = "countdown",
            assetPath = "ui-skills/countdown/SKILL.md",
            componentType = ComponentType.COUNTDOWN,
            displayName = "Countdown",
            shortLabel = "Time",
            promptHint = "Future date, deadline, appointment, or due date.",
            supportedTaskTypes = setOf(
                TaskType.GENERAL,
                TaskType.TRAVEL,
                TaskType.HEALTH,
                TaskType.PROJECT,
                TaskType.FINANCE,
                TaskType.EVENT,
                TaskType.GOAL
            )
        ),
        UiSkillDefinition(
            id = "habit-ring",
            assetPath = "ui-skills/habit-ring/SKILL.md",
            componentType = ComponentType.HABIT_RING,
            displayName = "Habit ring",
            shortLabel = "Habit",
            promptHint = "Recurring daily or weekly routine.",
            supportedTaskTypes = setOf(TaskType.HABIT, TaskType.HEALTH, TaskType.GENERAL)
        ),
        UiSkillDefinition(
            id = "notes",
            assetPath = "ui-skills/notes/SKILL.md",
            componentType = ComponentType.NOTES,
            displayName = "Notes",
            shortLabel = "Text",
            promptHint = "Structured context, roadmap, instructions, agenda, or markdown guidance.",
            supportedTaskTypes = allTaskTypes
        ),
        UiSkillDefinition(
            id = "metric-tracker",
            assetPath = "ui-skills/metric-tracker/SKILL.md",
            componentType = ComponentType.METRIC_TRACKER,
            displayName = "Metric tracker",
            shortLabel = "Metric",
            promptHint = "Numeric target like money, distance, weight, reps, or time.",
            supportedTaskTypes = setOf(
                TaskType.GENERAL,
                TaskType.TRAVEL,
                TaskType.HABIT,
                TaskType.HEALTH,
                TaskType.PROJECT,
                TaskType.FINANCE,
                TaskType.GOAL
            )
        ),
        UiSkillDefinition(
            id = "data-feed",
            assetPath = "ui-skills/data-feed/SKILL.md",
            componentType = ComponentType.DATA_FEED,
            displayName = "Data feed",
            shortLabel = "Live",
            promptHint = "Fresh external data such as weather, exchange rates, or flight prices.",
            supportedTaskTypes = setOf(TaskType.GENERAL, TaskType.TRAVEL, TaskType.FINANCE, TaskType.EVENT)
        )
    )

    fun availableFor(taskTypeHint: TaskType?): List<UiSkillDefinition> {
        taskTypeHint ?: return all
        val matched = all.filter { taskTypeHint in it.supportedTaskTypes }
        return if (matched.isNotEmpty()) matched else all
    }

    fun resolve(skillId: String): UiSkillDefinition? {
        return all.firstOrNull { definition ->
            definition.id.equals(skillId.trim(), ignoreCase = true)
        }
    }

    fun resolve(componentType: ComponentType): UiSkillDefinition? {
        return all.firstOrNull { it.componentType == componentType }
    }

    fun validate(skillId: String, config: JsonObject): UiSkillValidation {
        val definition = resolve(skillId)
            ?: return UiSkillValidation(
                definition = null,
                isValid = false,
                reason = "Unknown UI skill: $skillId"
            )

        val configType = config["config_type"]?.jsonPrimitive?.contentOrNull
        if (configType != null && !configType.equals(definition.componentType.name, ignoreCase = true)) {
            return UiSkillValidation(
                definition = definition,
                isValid = false,
                reason = "UI skill ${definition.id} must use config_type ${definition.componentType.name}"
            )
        }

        return UiSkillValidation(definition = definition, isValid = true)
    }

    fun buildSystemPrompt(
        context: Context,
        taskTypeHint: TaskType?
    ): String {
        val basePrompt = context.assets.open("system_prompt.txt").bufferedReader().use { it.readText() }.trim()
        val availableSkills = availableFor(taskTypeHint)
        return buildString {
            appendLine(basePrompt)
            appendLine()
            appendLine("--- AVAILABLE UI SKILLS ---")
            appendLine("Use ONLY these skill ids in analysis.skills_needed and task.skills[].skill.")
            appendLine("Name and description are the canonical discovery surface for the planner.")
            appendLine()
            availableSkills.forEach { definition ->
                appendLine(
                    "- ${definition.id} -> ${definition.componentType.name}: ${definition.promptHint}"
                )
            }
        }.trim()
    }

    fun loadDocument(context: Context, definition: UiSkillDefinition): UiSkillDocument {
        val raw = context.assets.open(definition.assetPath).bufferedReader().use { it.readText() }
        return parseUiSkillMarkdown(raw)
    }
}

internal fun parseUiSkillMarkdown(raw: String): UiSkillDocument {
    val normalized = raw.trim()
    if (!normalized.startsWith("---")) {
        return UiSkillDocument(name = "", description = "", body = normalized)
    }

    val lines = normalized.lines()
    val metadata = linkedMapOf<String, String>()
    var index = 1
    while (index < lines.size && lines[index].trim() != "---") {
        val line = lines[index]
        val separatorIndex = line.indexOf(':')
        if (separatorIndex > 0) {
            val key = line.substring(0, separatorIndex).trim()
            val value = line.substring(separatorIndex + 1).trim()
            metadata[key] = value
        }
        index++
    }

    val body = lines.drop(index + 1).joinToString("\n").trim()
    return UiSkillDocument(
        name = metadata["name"].orEmpty(),
        description = metadata["description"].orEmpty(),
        body = body
    )
}

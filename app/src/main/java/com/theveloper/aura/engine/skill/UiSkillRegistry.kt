package com.theveloper.aura.engine.skill

import android.content.Context
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.FunctionSkillRuntime
import com.theveloper.aura.domain.model.SkillSource
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.model.UiSkillRuntime
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
    val supportedTaskTypes: Set<TaskType>,
    val runtime: UiSkillRuntime = UiSkillRuntime.NATIVE,
    val source: SkillSource = SkillSource.BUILTIN,
    val entryAssetPath: String? = null,
    val entrypoint: String? = null
)

data class FunctionSkillDefinition(
    val id: String,
    val assetPath: String? = null,
    val displayName: String,
    val shortLabel: String,
    val promptHint: String,
    val supportedTaskTypes: Set<TaskType>,
    val runtime: FunctionSkillRuntime = FunctionSkillRuntime.PROMPT_AUGMENTATION,
    val source: SkillSource = SkillSource.BUILTIN
)

data class SkillDocument(
    val name: String,
    val description: String,
    val body: String
)

data class UiSkillValidation(
    val definition: UiSkillDefinition?,
    val isValid: Boolean,
    val reason: String? = null
)

data class FunctionSkillValidation(
    val definition: FunctionSkillDefinition?,
    val isValid: Boolean,
    val reason: String? = null
)

object SkillRegistry {

    private val allTaskTypes = TaskType.entries.toSet()

    val uiSkills: List<UiSkillDefinition> = listOf(
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

    val functionSkills: List<FunctionSkillDefinition> = listOf(
        FunctionSkillDefinition(
            id = "learning-guide",
            displayName = "Learning guide",
            shortLabel = "Learn",
            promptHint = "Improve learning roadmaps with sequencing, practice suggestions, and study guidance.",
            supportedTaskTypes = setOf(TaskType.GENERAL, TaskType.GOAL, TaskType.PROJECT)
        ),
        FunctionSkillDefinition(
            id = "resource-curator",
            displayName = "Resource curator",
            shortLabel = "Sources",
            promptHint = "Add useful references, canonical docs, and source suggestions when the task asks for them.",
            supportedTaskTypes = allTaskTypes
        ),
        FunctionSkillDefinition(
            id = "structured-brief",
            displayName = "Structured brief",
            shortLabel = "Brief",
            promptHint = "Turn vague requests into a clearer plan, brief, or explanation before configuring UI.",
            supportedTaskTypes = allTaskTypes
        )
    )

    fun availableUiSkills(taskTypeHint: TaskType?): List<UiSkillDefinition> {
        taskTypeHint ?: return uiSkills
        val matched = uiSkills.filter { taskTypeHint in it.supportedTaskTypes }
        return if (matched.isNotEmpty()) matched else uiSkills
    }

    fun availableFunctionSkills(taskTypeHint: TaskType?): List<FunctionSkillDefinition> {
        taskTypeHint ?: return functionSkills
        val matched = functionSkills.filter { taskTypeHint in it.supportedTaskTypes }
        return if (matched.isNotEmpty()) matched else functionSkills
    }

    fun resolveUi(skillId: String): UiSkillDefinition? {
        return uiSkills.firstOrNull { definition ->
            definition.id.equals(skillId.trim(), ignoreCase = true)
        }
    }

    fun resolveUi(componentType: ComponentType): UiSkillDefinition? {
        return uiSkills.firstOrNull { it.componentType == componentType }
    }

    fun resolveFunction(skillId: String): FunctionSkillDefinition? {
        return functionSkills.firstOrNull { definition ->
            definition.id.equals(skillId.trim(), ignoreCase = true)
        }
    }

    fun validateUiSkill(skillId: String, config: JsonObject): UiSkillValidation {
        val definition = resolveUi(skillId)
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

    fun validateFunctionSkill(skillId: String, config: JsonObject): FunctionSkillValidation {
        val definition = resolveFunction(skillId)
            ?: return FunctionSkillValidation(
                definition = null,
                isValid = false,
                reason = "Unknown function skill: $skillId"
            )

        return FunctionSkillValidation(
            definition = definition,
            isValid = config.isNotEmpty() || definition.runtime == FunctionSkillRuntime.PROMPT_AUGMENTATION
        )
    }

    fun buildPlannerPrompt(
        context: Context,
        taskTypeHint: TaskType?,
        profile: PromptProfile = PromptProfile.DEFAULT
    ): String {
        if (profile == PromptProfile.LOCAL_COMPACT) {
            return SkillPromptCards.buildCompactPlannerPrompt(taskTypeHint)
        }

        val basePrompt = context.assets.open("system_prompt.txt").bufferedReader().use { it.readText() }.trim()
        val availableUiSkills = availableUiSkills(taskTypeHint)
        val availableFunctionSkills = availableFunctionSkills(taskTypeHint)
        return buildString {
            appendLine(basePrompt)
            appendLine()
            appendLine("--- AVAILABLE UI SKILLS ---")
            appendLine("Use ONLY these ids in analysis.ui_skills_needed and task.skills[].skill.")
            appendLine("UI-Skills are the renderable building blocks of the task UI.")
            appendLine()
            availableUiSkills.forEach { definition ->
                val document = loadDocument(context, definition.assetPath)
                appendLine(
                    "- ${definition.id} -> ${definition.componentType.name} [${definition.runtime.name}]: ${definition.promptHint}"
                )
                val guidance = buildPromptGuidance(document)
                guidance.forEach { line ->
                    appendLine("  $line")
                }
            }
            appendLine()
            appendLine("--- AVAILABLE FUNCTION SKILLS ---")
            appendLine("Use ONLY these ids in analysis.function_skills_needed and task.functionSkills[].skill.")
            appendLine("Function-Skills improve reasoning and content, but they do not create UI on their own.")
            appendLine()
            availableFunctionSkills.forEach { definition ->
                appendLine(
                    "- ${definition.id} -> ${definition.runtime.name}: ${definition.promptHint}"
                )
            }
        }.trim()
    }

    fun loadPromptGuidance(
        context: Context,
        definition: UiSkillDefinition
    ): List<String> {
        return buildPromptGuidance(loadDocument(context, definition.assetPath))
    }

    private fun buildPromptGuidance(document: SkillDocument): List<String> {
        val instructionStart = document.body.lines().indexOfFirst { it.trim() == "## Instructions" }
        if (instructionStart == -1) return emptyList()

        return document.body.lines()
            .drop(instructionStart + 1)
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                    !line.startsWith("#") &&
                    line != "Rules:" &&
                    !line.startsWith("When selected")
            }
            .take(6)
            .map { line ->
                if (line.startsWith("-")) line else "- $line"
            }
    }

    fun loadDocument(context: Context, assetPath: String): SkillDocument {
        val raw = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return parseSkillMarkdown(raw)
    }
}

object UiSkillRegistry {
    val all: List<UiSkillDefinition> get() = SkillRegistry.uiSkills

    fun availableFor(taskTypeHint: TaskType?): List<UiSkillDefinition> {
        return SkillRegistry.availableUiSkills(taskTypeHint)
    }

    fun resolve(skillId: String): UiSkillDefinition? = SkillRegistry.resolveUi(skillId)

    fun resolve(componentType: ComponentType): UiSkillDefinition? = SkillRegistry.resolveUi(componentType)

    fun validate(skillId: String, config: JsonObject): UiSkillValidation {
        return SkillRegistry.validateUiSkill(skillId, config)
    }

    fun buildSystemPrompt(
        context: Context,
        taskTypeHint: TaskType?,
        profile: PromptProfile = PromptProfile.DEFAULT
    ): String {
        return SkillRegistry.buildPlannerPrompt(context, taskTypeHint, profile)
    }

    fun loadDocument(context: Context, definition: UiSkillDefinition): SkillDocument {
        return SkillRegistry.loadDocument(context, definition.assetPath)
    }

    fun loadPromptGuidance(context: Context, definition: UiSkillDefinition): List<String> {
        return SkillRegistry.loadPromptGuidance(context, definition)
    }
}

internal fun parseSkillMarkdown(raw: String): SkillDocument {
    val normalized = raw.trim()
    if (!normalized.startsWith("---")) {
        return SkillDocument(name = "", description = "", body = normalized)
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
    return SkillDocument(
        name = metadata["name"].orEmpty(),
        description = metadata["description"].orEmpty(),
        body = body
    )
}

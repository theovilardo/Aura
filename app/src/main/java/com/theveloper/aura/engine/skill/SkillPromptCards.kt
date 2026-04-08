package com.theveloper.aura.engine.skill

import com.theveloper.aura.domain.model.TaskType

enum class PromptProfile {
    DEFAULT,
    LOCAL_COMPACT
}

internal data class UiSkillPromptCard(
    val chooseWhen: String,
    val avoidWhen: String,
    val requiredConfig: List<String>,
    val qualityRules: List<String>
)

internal data class FunctionSkillPromptCard(
    val chooseWhen: String,
    val avoidWhen: String,
    val qualityRules: List<String>
)

object SkillPromptCards {

    private val uiCards: Map<String, UiSkillPromptCard> = mapOf(
        "checklist" to UiSkillPromptCard(
            chooseWhen = "Concrete things to buy, bring, prepare, pack, review, or complete.",
            avoidWhen = "Pure prose, authored narrative instructions, or explanation that should read as markdown.",
            requiredConfig = listOf(
                "config_type=CHECKLIST",
                "label=short label in user language",
                "allowAddItems=boolean",
                "items=[{label,isSuggested}]"
            ),
            qualityRules = listOf(
                "include the real atomic items now",
                "expand known bundles, recipes, kits, and ingredient groups into separate items when knowable",
                "mark inferred extras with isSuggested=true"
            )
        ),
        "progress-bar" to UiSkillPromptCard(
            chooseWhen = "The task has phases, milestones, or overall progress worth tracking.",
            avoidWhen = "A one-shot note, reminder, or flat checklist with no real stages.",
            requiredConfig = listOf(
                "config_type=PROGRESS_BAR",
                "source=MANUAL",
                "label=short label in user language",
                "manualProgress=0.0..1.0"
            ),
            qualityRules = listOf(
                "start at 0.0 unless the user gave current progress",
                "use only when visual progress adds value"
            )
        ),
        "countdown" to UiSkillPromptCard(
            chooseWhen = "A future date, appointment, event, deadline, or due time matters.",
            avoidWhen = "No concrete future time is known or inferable.",
            requiredConfig = listOf(
                "config_type=COUNTDOWN",
                "label=short label in user language",
                "targetDate=epoch millis"
            ),
            qualityRules = listOf(
                "only clarify if the date is truly blocking",
                "use a real future timestamp, not prose"
            )
        ),
        "habit-ring" to UiSkillPromptCard(
            chooseWhen = "A daily or weekly routine should be reinforced as a habit.",
            avoidWhen = "A non-recurring one-off task or project.",
            requiredConfig = listOf(
                "config_type=HABIT_RING",
                "frequency=DAILY|WEEKLY",
                "label=short label in user language",
                "targetCount=optional integer"
            ),
            qualityRules = listOf(
                "use only for recurring behavior",
                "do not fake recurrence if the prompt is one-shot"
            )
        ),
        "notes" to UiSkillPromptCard(
            chooseWhen = "Structured context, explanation, instructions, roadmap, agenda, or markdown guidance helps.",
            avoidWhen = "The task is already fully delivered by stronger concrete UI and extra prose would be filler.",
            requiredConfig = listOf(
                "config_type=NOTES",
                "text=useful markdown in user language",
                "isMarkdown=true"
            ),
            qualityRules = listOf(
                "write real content now, never placeholders",
                "use notes for written instructions, recipe steps, guides, or other narrative content",
                "use headings or lists only when they help readability"
            )
        ),
        "metric-tracker" to UiSkillPromptCard(
            chooseWhen = "A numeric target like money, distance, reps, weight, or time should be tracked.",
            avoidWhen = "No numeric target exists and no tracking value is implied.",
            requiredConfig = listOf(
                "config_type=METRIC_TRACKER",
                "unit=precise unit",
                "label=short label in user language",
                "goal=optional number"
            ),
            qualityRules = listOf(
                "use a concrete unit",
                "leave goal empty rather than inventing one"
            )
        ),
        "data-feed" to UiSkillPromptCard(
            chooseWhen = "The user explicitly needs fresh external data.",
            avoidWhen = "General knowledge or authored content is enough.",
            requiredConfig = listOf(
                "config_type=DATA_FEED",
                "fetcherConfigId=existing fetcher id",
                "displayLabel=short label in user language"
            ),
            qualityRules = listOf(
                "do not use for stale or static content",
                "prefer authored UI when no live fetch is required"
            )
        )
    )

    private val functionCards: Map<String, FunctionSkillPromptCard> = mapOf(
        "learning-guide" to FunctionSkillPromptCard(
            chooseWhen = "The user wants to learn, study, practice, or follow a learning roadmap.",
            avoidWhen = "The task is not about learning or skill development.",
            qualityRules = listOf(
                "improve sequencing and practice guidance",
                "do not replace the core UI artifact"
            )
        ),
        "resource-curator" to FunctionSkillPromptCard(
            chooseWhen = "The user explicitly asks for links, references, docs, or sources.",
            avoidWhen = "The user only wants the authored artifact itself.",
            qualityRules = listOf(
                "add references only when they are requested or clearly useful",
                "do not turn the task into research when direct content can be authored now"
            )
        ),
        "structured-brief" to FunctionSkillPromptCard(
            chooseWhen = "The prompt is too vague to build useful UI directly.",
            avoidWhen = "The user already named the artifact or gave enough detail to build it now.",
            qualityRules = listOf(
                "clarify structure, not by asking the user unless truly blocked",
                "use as a last resort, not as the default"
            )
        )
    )

    fun buildCompactPlannerPrompt(taskTypeHint: TaskType?): String {
        val availableUi = SkillRegistry.availableUiSkills(taskTypeHint)
        val availableFunction = SkillRegistry.availableFunctionSkills(taskTypeHint)

        return buildString {
            appendLine("You are AURA's local task planner.")
            appendLine("Choose the best ready-to-use task UI for the user in a single reasoning pass.")
            appendLine("All user-facing text MUST stay in the same language as the user's input.")
            appendLine("Prefer a useful first version over clarification whenever possible.")
            appendLine("If the user asks for a concrete artifact, deliver it through the right UI now.")
            appendLine("Use the minimum set of skills that fully solves the task.")
            appendLine("Function skills improve content; they never replace UI.")
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
            appendLine("Type mapping:")
            appendLine("- future event or deadline -> EVENT")
            appendLine("- recurring routine -> HABIT")
            appendLine("- health/body/workout/nutrition tracking -> HEALTH")
            appendLine("- trip or travel planning -> TRAVEL")
            appendLine("- money or budget tracking -> FINANCE")
            appendLine("- multi-phase deliverable -> PROJECT")
            appendLine("- learning or skill growth -> GOAL")
            appendLine("- everything else -> GENERAL")
            appendLine()
            appendLine("UI skill cards:")
            appendLine(renderUiSkillCards(availableUi, profile = PromptProfile.LOCAL_COMPACT))
            appendLine()
            appendLine("Function skill cards:")
            appendLine(renderFunctionSkillCards(availableFunction, profile = PromptProfile.LOCAL_COMPACT))
            appendLine()
            appendLine("Quality rules:")
            appendLine("- concrete named items must become visible atomic items")
            appendLine("- known sets or bundles should be expanded into atomic items when commonly knowable")
            appendLine("- if the task mixes concrete items with written instructions, use checklist for items and notes for the written content")
            appendLine("- choose notes only when they add real value beyond the other selected UI")
            appendLine("- use structured-brief only when the prompt is genuinely vague")
            appendLine("- clarification is the last resort")
        }.trim()
    }

    fun renderUiSkillCards(
        definitions: List<UiSkillDefinition>,
        profile: PromptProfile = PromptProfile.DEFAULT
    ): String {
        return definitions.joinToString(separator = "\n") { definition ->
            val card = uiCards[definition.id]
            buildString {
                append("- ${definition.id}: ${definition.promptHint}")
                if (card != null) {
                    append("\n  use_when: ${card.chooseWhen}")
                    append("\n  avoid_when: ${card.avoidWhen}")
                    append("\n  required_config: ${card.requiredConfig.joinToString(", ")}")
                    append("\n  quality_rules: ${card.qualityRules.joinToString("; ")}")
                } else if (profile == PromptProfile.LOCAL_COMPACT) {
                    append("\n  use_when: ${definition.promptHint}")
                }
            }
        }
    }

    fun renderFunctionSkillCards(
        definitions: List<FunctionSkillDefinition>,
        profile: PromptProfile = PromptProfile.DEFAULT
    ): String {
        return definitions.joinToString(separator = "\n") { definition ->
            val card = functionCards[definition.id]
            buildString {
                append("- ${definition.id}: ${definition.promptHint}")
                if (card != null) {
                    append("\n  use_when: ${card.chooseWhen}")
                    append("\n  avoid_when: ${card.avoidWhen}")
                    append("\n  quality_rules: ${card.qualityRules.joinToString("; ")}")
                } else if (profile == PromptProfile.LOCAL_COMPACT) {
                    append("\n  use_when: ${definition.promptHint}")
                }
            }
        }
    }
}

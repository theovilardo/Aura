package com.theveloper.aura.engine.dsl

import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.domain.model.ChecklistConfig
import com.theveloper.aura.domain.model.ComponentConfig
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.CountdownConfig
import com.theveloper.aura.domain.model.DataFeedConfig
import com.theveloper.aura.domain.model.DataFeedStatus
import com.theveloper.aura.domain.model.HabitRingConfig
import com.theveloper.aura.domain.model.MetricTrackerConfig
import com.theveloper.aura.domain.model.NotesConfig
import com.theveloper.aura.domain.model.ProgressBarConfig
import com.theveloper.aura.domain.model.TaskType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

data class TaskComponentContext(
    val input: String = "",
    val title: String = "",
    val taskType: TaskType = TaskType.GENERAL,
    val targetDateMs: Long? = null,
    val numbers: List<Double> = emptyList(),
    val locations: List<String> = emptyList()
)

data class BuiltTaskComponent(
    val component: ComponentDSL,
    val reminders: List<ReminderDSL> = emptyList()
)

data class TaskComponentTemplate(
    val id: String,
    val title: String,
    val variantLabel: String,
    val subtitle: String,
    val type: ComponentType,
    val supportedTaskTypes: Set<TaskType>,
    val build: (sortOrder: Int, now: Long, context: TaskComponentContext) -> BuiltTaskComponent
)

object TaskComponentCatalog {

    val templates: List<TaskComponentTemplate> = listOf(

        // ── Countdown ────────────────────────────────────────────────────────

        TaskComponentTemplate(
            id = "travel_countdown",
            title = "Countdown",
            variantLabel = "Trip",
            subtitle = "Travel date with urgency and remaining time.",
            type = ComponentType.COUNTDOWN,
            supportedTaskTypes = setOf(TaskType.TRAVEL, TaskType.GENERAL),
            build = { sortOrder, _, context ->
                builtComponent(
                    type = ComponentType.COUNTDOWN,
                    sortOrder = sortOrder,
                    config = CountdownConfig(
                        targetDate = context.targetDateMs ?: 0L,
                        label = "Trip date"
                    )
                )
            }
        ),
        TaskComponentTemplate(
            id = "deadline_countdown",
            title = "Countdown",
            variantLabel = "Deadline",
            subtitle = "Delivery target for projects and general goals.",
            type = ComponentType.COUNTDOWN,
            supportedTaskTypes = setOf(TaskType.PROJECT, TaskType.GENERAL, TaskType.HEALTH, TaskType.EVENT, TaskType.GOAL),
            build = { sortOrder, _, context ->
                builtComponent(
                    type = ComponentType.COUNTDOWN,
                    sortOrder = sortOrder,
                    config = CountdownConfig(
                        targetDate = context.targetDateMs ?: 0L,
                        label = "Target date"
                    )
                )
            }
        ),
        TaskComponentTemplate(
            id = "payment_countdown",
            title = "Countdown",
            variantLabel = "Payment",
            subtitle = "Due date for invoices, rent and other payments.",
            type = ComponentType.COUNTDOWN,
            supportedTaskTypes = setOf(TaskType.FINANCE, TaskType.GENERAL),
            build = { sortOrder, _, context ->
                builtComponent(
                    type = ComponentType.COUNTDOWN,
                    sortOrder = sortOrder,
                    config = CountdownConfig(
                        targetDate = context.targetDateMs ?: 0L,
                        label = "Payment due"
                    )
                )
            }
        ),
        TaskComponentTemplate(
            id = "event_countdown",
            title = "Countdown",
            variantLabel = "Event",
            subtitle = "Fixed date for meetings, appointments and key moments.",
            type = ComponentType.COUNTDOWN,
            supportedTaskTypes = setOf(TaskType.EVENT),
            build = { sortOrder, _, context ->
                builtComponent(
                    type = ComponentType.COUNTDOWN,
                    sortOrder = sortOrder,
                    config = CountdownConfig(
                        targetDate = context.targetDateMs ?: 0L,
                        label = "Event date"
                    )
                )
            }
        ),

        // ── Checklist ─────────────────────────────────────────────────────────
        // Items are intentionally empty — populated by the LLM semantic bridge or by the user.

        TaskComponentTemplate(
            id = "packing_checklist",
            title = "Checklist",
            variantLabel = "Packing",
            subtitle = "Travel prep, baggage and document checks.",
            type = ComponentType.CHECKLIST,
            supportedTaskTypes = setOf(TaskType.TRAVEL, TaskType.GENERAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.CHECKLIST,
                    sortOrder = sortOrder,
                    config = ChecklistConfig(label = "Travel prep", allowAddItems = true)
                )
            }
        ),
        TaskComponentTemplate(
            id = "travel_documents_checklist",
            title = "Checklist",
            variantLabel = "Documents",
            subtitle = "Passport, tickets and travel papers in one place.",
            type = ComponentType.CHECKLIST,
            supportedTaskTypes = setOf(TaskType.TRAVEL, TaskType.GENERAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.CHECKLIST,
                    sortOrder = sortOrder,
                    config = ChecklistConfig(label = "Travel documents", allowAddItems = true)
                )
            }
        ),
        TaskComponentTemplate(
            id = "action_checklist",
            title = "Checklist",
            variantLabel = "Execution",
            subtitle = "Action plan, milestones and final review.",
            type = ComponentType.CHECKLIST,
            supportedTaskTypes = setOf(TaskType.PROJECT, TaskType.GENERAL, TaskType.HEALTH, TaskType.EVENT, TaskType.GOAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.CHECKLIST,
                    sortOrder = sortOrder,
                    config = ChecklistConfig(label = "Key steps", allowAddItems = true)
                )
            }
        ),
        TaskComponentTemplate(
            id = "event_runbook_checklist",
            title = "Checklist",
            variantLabel = "Prep",
            subtitle = "Simple prep list for a fixed event or meeting.",
            type = ComponentType.CHECKLIST,
            supportedTaskTypes = setOf(TaskType.EVENT),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.CHECKLIST,
                    sortOrder = sortOrder,
                    config = ChecklistConfig(label = "Event prep", allowAddItems = true)
                )
            }
        ),
        TaskComponentTemplate(
            id = "goal_milestones_checklist",
            title = "Checklist",
            variantLabel = "Milestones",
            subtitle = "Milestones and next steps for a medium-term goal.",
            type = ComponentType.CHECKLIST,
            supportedTaskTypes = setOf(TaskType.GOAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.CHECKLIST,
                    sortOrder = sortOrder,
                    config = ChecklistConfig(label = "Milestones", allowAddItems = true)
                )
            }
        ),
        TaskComponentTemplate(
            id = "finance_payment_checklist",
            title = "Checklist",
            variantLabel = "Payment",
            subtitle = "Payment verification, transfer and receipt follow-up.",
            type = ComponentType.CHECKLIST,
            supportedTaskTypes = setOf(TaskType.FINANCE, TaskType.GENERAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.CHECKLIST,
                    sortOrder = sortOrder,
                    config = ChecklistConfig(label = "Payment steps", allowAddItems = true)
                )
            }
        ),
        TaskComponentTemplate(
            id = "medication_checklist",
            title = "Checklist",
            variantLabel = "Medication",
            subtitle = "Medication cycle, dose and side-effect follow-up.",
            type = ComponentType.CHECKLIST,
            supportedTaskTypes = setOf(TaskType.HEALTH, TaskType.HABIT),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.CHECKLIST,
                    sortOrder = sortOrder,
                    config = ChecklistConfig(label = "Medication cycle", allowAddItems = true)
                )
            }
        ),

        // ── Habit Ring ────────────────────────────────────────────────────────

        TaskComponentTemplate(
            id = "habit_daily",
            title = "Habit Ring",
            variantLabel = "Daily",
            subtitle = "Simple daily streak with recurring reminder.",
            type = ComponentType.HABIT_RING,
            supportedTaskTypes = setOf(TaskType.HABIT, TaskType.HEALTH, TaskType.GENERAL),
            build = { sortOrder, now, _ ->
                builtComponent(
                    type = ComponentType.HABIT_RING,
                    sortOrder = sortOrder,
                    config = HabitRingConfig(frequency = "DAILY", label = "Daily streak"),
                    reminders = listOf(
                        ReminderDSL(
                            scheduledAtMs = now + DAY_IN_MILLIS,
                            intervalDays = 1f
                        )
                    )
                )
            }
        ),
        TaskComponentTemplate(
            id = "habit_weekly",
            title = "Habit Ring",
            variantLabel = "Weekly",
            subtitle = "Weekly rhythm for routines that do not happen every day.",
            type = ComponentType.HABIT_RING,
            supportedTaskTypes = setOf(TaskType.HABIT, TaskType.HEALTH),
            build = { sortOrder, now, _ ->
                builtComponent(
                    type = ComponentType.HABIT_RING,
                    sortOrder = sortOrder,
                    config = HabitRingConfig(frequency = "WEEKLY", label = "Weekly rhythm"),
                    reminders = listOf(
                        ReminderDSL(
                            scheduledAtMs = now + DAY_IN_MILLIS,
                            intervalDays = 7f
                        )
                    )
                )
            }
        ),

        // ── Progress Bar ──────────────────────────────────────────────────────

        TaskComponentTemplate(
            id = "progress_manual",
            title = "Progress Bar",
            variantLabel = "Manual",
            subtitle = "Percentage driven manually by the user.",
            type = ComponentType.PROGRESS_BAR,
            supportedTaskTypes = setOf(TaskType.PROJECT, TaskType.GENERAL, TaskType.TRAVEL, TaskType.HEALTH),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.PROGRESS_BAR,
                    sortOrder = sortOrder,
                    config = ProgressBarConfig(source = "MANUAL", label = "Overall progress")
                )
            }
        ),
        TaskComponentTemplate(
            id = "progress_milestones",
            title = "Progress Bar",
            variantLabel = "Milestones",
            subtitle = "Progress framed around stages or deliverables.",
            type = ComponentType.PROGRESS_BAR,
            supportedTaskTypes = setOf(TaskType.PROJECT, TaskType.TRAVEL, TaskType.GENERAL, TaskType.GOAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.PROGRESS_BAR,
                    sortOrder = sortOrder,
                    config = ProgressBarConfig(source = "MANUAL", label = "Milestone progress")
                )
            }
        ),
        TaskComponentTemplate(
            id = "progress_budget",
            title = "Progress Bar",
            variantLabel = "Budget",
            subtitle = "Savings or budget target tracked as a percentage.",
            type = ComponentType.PROGRESS_BAR,
            supportedTaskTypes = setOf(TaskType.FINANCE, TaskType.TRAVEL, TaskType.PROJECT, TaskType.GENERAL, TaskType.GOAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.PROGRESS_BAR,
                    sortOrder = sortOrder,
                    config = ProgressBarConfig(source = "MANUAL", label = "Budget target")
                )
            }
        ),
        TaskComponentTemplate(
            id = "progress_sprint",
            title = "Progress Bar",
            variantLabel = "Sprint",
            subtitle = "Fast-moving execution progress for launches and studies.",
            type = ComponentType.PROGRESS_BAR,
            supportedTaskTypes = setOf(TaskType.PROJECT, TaskType.GENERAL, TaskType.HABIT, TaskType.GOAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.PROGRESS_BAR,
                    sortOrder = sortOrder,
                    config = ProgressBarConfig(source = "MANUAL", label = "Execution sprint")
                )
            }
        ),
        TaskComponentTemplate(
            id = "goal_progress",
            title = "Progress Bar",
            variantLabel = "Goal",
            subtitle = "Top-line progress for a medium-term personal goal.",
            type = ComponentType.PROGRESS_BAR,
            supportedTaskTypes = setOf(TaskType.GOAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.PROGRESS_BAR,
                    sortOrder = sortOrder,
                    config = ProgressBarConfig(source = "MANUAL", label = "Goal progress")
                )
            }
        ),

        // ── Notes ─────────────────────────────────────────────────────────────
        // Text is intentionally empty — populated by the LLM semantic bridge or by the user.

        TaskComponentTemplate(
            id = "notes_brain_dump",
            title = "Notes",
            variantLabel = "Brain dump",
            subtitle = "Open markdown space for context and loose ideas.",
            type = ComponentType.NOTES,
            supportedTaskTypes = TaskType.entries.toSet(),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.NOTES,
                    sortOrder = sortOrder,
                    config = NotesConfig(text = "", isMarkdown = true)
                )
            }
        ),
        TaskComponentTemplate(
            id = "notes_meeting",
            title = "Notes",
            variantLabel = "Meeting",
            subtitle = "Structured recap for summaries and action items.",
            type = ComponentType.NOTES,
            supportedTaskTypes = setOf(TaskType.PROJECT, TaskType.GENERAL, TaskType.FINANCE, TaskType.EVENT),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.NOTES,
                    sortOrder = sortOrder,
                    config = NotesConfig(text = "", isMarkdown = true)
                )
            }
        ),
        TaskComponentTemplate(
            id = "event_notes",
            title = "Notes",
            variantLabel = "Event",
            subtitle = "People, context and what to bring for a fixed date.",
            type = ComponentType.NOTES,
            supportedTaskTypes = setOf(TaskType.EVENT),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.NOTES,
                    sortOrder = sortOrder,
                    config = NotesConfig(text = "", isMarkdown = true)
                )
            }
        ),
        TaskComponentTemplate(
            id = "travel_itinerary_notes",
            title = "Notes",
            variantLabel = "Itinerary",
            subtitle = "Flights, stays, transfers and local contacts.",
            type = ComponentType.NOTES,
            supportedTaskTypes = setOf(TaskType.TRAVEL, TaskType.GENERAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.NOTES,
                    sortOrder = sortOrder,
                    config = NotesConfig(text = "", isMarkdown = true)
                )
            }
        ),
        TaskComponentTemplate(
            id = "study_plan_notes",
            title = "Notes",
            variantLabel = "Study",
            subtitle = "Structured note for learning plans or research.",
            type = ComponentType.NOTES,
            supportedTaskTypes = setOf(TaskType.PROJECT, TaskType.GENERAL, TaskType.GOAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.NOTES,
                    sortOrder = sortOrder,
                    config = NotesConfig(text = "", isMarkdown = true)
                )
            }
        ),
        TaskComponentTemplate(
            id = "goal_notes",
            title = "Notes",
            variantLabel = "Why it matters",
            subtitle = "Motivation, blockers and next milestone in one place.",
            type = ComponentType.NOTES,
            supportedTaskTypes = setOf(TaskType.GOAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.NOTES,
                    sortOrder = sortOrder,
                    config = NotesConfig(text = "", isMarkdown = true)
                )
            }
        ),
        TaskComponentTemplate(
            id = "budget_snapshot_notes",
            title = "Notes",
            variantLabel = "Budget",
            subtitle = "Money snapshot, risks and next payment checkpoint.",
            type = ComponentType.NOTES,
            supportedTaskTypes = setOf(TaskType.FINANCE, TaskType.TRAVEL, TaskType.GENERAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.NOTES,
                    sortOrder = sortOrder,
                    config = NotesConfig(text = "", isMarkdown = true)
                )
            }
        ),
        TaskComponentTemplate(
            id = "journal_reflection",
            title = "Notes",
            variantLabel = "Journal",
            subtitle = "Quick reflection for routines, wellbeing and progress.",
            type = ComponentType.NOTES,
            supportedTaskTypes = setOf(TaskType.HABIT, TaskType.HEALTH, TaskType.GENERAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.NOTES,
                    sortOrder = sortOrder,
                    config = NotesConfig(text = "", isMarkdown = true)
                )
            }
        ),
        TaskComponentTemplate(
            id = "notes_clinic",
            title = "Notes",
            variantLabel = "Clinical",
            subtitle = "Symptoms, medication response and doctor follow-up.",
            type = ComponentType.NOTES,
            supportedTaskTypes = setOf(TaskType.HEALTH, TaskType.GENERAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.NOTES,
                    sortOrder = sortOrder,
                    config = NotesConfig(text = "", isMarkdown = true)
                )
            }
        ),

        // ── Metric Tracker ────────────────────────────────────────────────────
        // History is intentionally empty — populated by real user entries over time.

        TaskComponentTemplate(
            id = "metric_hydration",
            title = "Metric Tracker",
            variantLabel = "Hydration",
            subtitle = "Numeric tracker for water or liquid intake.",
            type = ComponentType.METRIC_TRACKER,
            supportedTaskTypes = setOf(TaskType.HEALTH, TaskType.HABIT, TaskType.GENERAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.METRIC_TRACKER,
                    sortOrder = sortOrder,
                    config = MetricTrackerConfig(unit = "ml", label = "Hydration")
                )
            }
        ),
        TaskComponentTemplate(
            id = "metric_weight",
            title = "Metric Tracker",
            variantLabel = "Weight",
            subtitle = "Trend line for weight and body metrics.",
            type = ComponentType.METRIC_TRACKER,
            supportedTaskTypes = setOf(TaskType.HEALTH, TaskType.GENERAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.METRIC_TRACKER,
                    sortOrder = sortOrder,
                    config = MetricTrackerConfig(unit = "kg", label = "Weight trend")
                )
            }
        ),
        TaskComponentTemplate(
            id = "metric_steps",
            title = "Metric Tracker",
            variantLabel = "Steps",
            subtitle = "Daily movement or activity target history.",
            type = ComponentType.METRIC_TRACKER,
            supportedTaskTypes = setOf(TaskType.HEALTH, TaskType.HABIT),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.METRIC_TRACKER,
                    sortOrder = sortOrder,
                    config = MetricTrackerConfig(unit = "steps", label = "Daily activity")
                )
            }
        ),
        TaskComponentTemplate(
            id = "metric_sleep",
            title = "Metric Tracker",
            variantLabel = "Sleep",
            subtitle = "Sleep duration trend to support recovery routines.",
            type = ComponentType.METRIC_TRACKER,
            supportedTaskTypes = setOf(TaskType.HEALTH, TaskType.HABIT, TaskType.GENERAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.METRIC_TRACKER,
                    sortOrder = sortOrder,
                    config = MetricTrackerConfig(unit = "h", label = "Sleep trend")
                )
            }
        ),
        TaskComponentTemplate(
            id = "metric_budget_target",
            title = "Metric Tracker",
            variantLabel = "Budget",
            subtitle = "Track saved money or available budget against a target.",
            type = ComponentType.METRIC_TRACKER,
            supportedTaskTypes = setOf(TaskType.FINANCE, TaskType.TRAVEL, TaskType.GOAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.METRIC_TRACKER,
                    sortOrder = sortOrder,
                    config = MetricTrackerConfig(unit = "USD", label = "Budget target")
                )
            }
        ),
        TaskComponentTemplate(
            id = "goal_momentum_metric",
            title = "Metric Tracker",
            variantLabel = "Momentum",
            subtitle = "A lightweight score to track consistency toward a goal.",
            type = ComponentType.METRIC_TRACKER,
            supportedTaskTypes = setOf(TaskType.GOAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.METRIC_TRACKER,
                    sortOrder = sortOrder,
                    config = MetricTrackerConfig(unit = "%", label = "Momentum")
                )
            }
        ),

        // ── Data Feed ─────────────────────────────────────────────────────────

        TaskComponentTemplate(
            id = "feed_weather",
            title = "Data Feed",
            variantLabel = "Weather",
            subtitle = "External weather snapshot tied to a place or trip.",
            type = ComponentType.DATA_FEED,
            supportedTaskTypes = setOf(TaskType.TRAVEL, TaskType.GENERAL),
            build = { sortOrder, _, context ->
                val location = context.locations.firstOrNull()
                builtComponent(
                    type = ComponentType.DATA_FEED,
                    sortOrder = sortOrder,
                    config = DataFeedConfig(
                        fetcherConfigId = "weather_manual",
                        displayLabel = location?.let { "Weather in $it" } ?: "Weather snapshot",
                        status = DataFeedStatus.LOADING
                    )
                )
            }
        ),
        TaskComponentTemplate(
            id = "feed_exchange",
            title = "Data Feed",
            variantLabel = "FX",
            subtitle = "Exchange rate or external finance indicator.",
            type = ComponentType.DATA_FEED,
            supportedTaskTypes = setOf(TaskType.FINANCE, TaskType.TRAVEL, TaskType.GENERAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.DATA_FEED,
                    sortOrder = sortOrder,
                    config = DataFeedConfig(
                        fetcherConfigId = "usd_ars",
                        displayLabel = "USD / ARS",
                        status = DataFeedStatus.LOADING
                    )
                )
            }
        )
    )

    fun recommended(taskType: TaskType): List<TaskComponentTemplate> {
        val orderedTemplateIds = when (taskType) {
            TaskType.TRAVEL -> listOf(
                "travel_countdown",
                "packing_checklist",
                "travel_documents_checklist",
                "metric_budget_target",
                "travel_itinerary_notes"
            )
            TaskType.HABIT -> listOf(
                "habit_daily",
                "habit_weekly",
                "metric_hydration",
                "metric_steps",
                "journal_reflection"
            )
            TaskType.HEALTH -> listOf(
                "metric_weight",
                "progress_manual",
                "metric_hydration",
                "metric_steps",
                "metric_sleep",
                "medication_checklist",
                "deadline_countdown",
                "notes_clinic"
            )
            TaskType.PROJECT -> listOf(
                "progress_milestones",
                "action_checklist",
                "progress_sprint",
                "deadline_countdown",
                "notes_meeting",
                "study_plan_notes"
            )
            TaskType.FINANCE -> listOf(
                "metric_budget_target",
                "progress_budget",
                "payment_countdown",
                "finance_payment_checklist",
                "budget_snapshot_notes"
            )
            TaskType.EVENT -> listOf(
                "event_countdown",
                "event_runbook_checklist",
                "event_notes"
            )
            TaskType.GOAL -> listOf(
                "goal_progress",
                "goal_milestones_checklist",
                "goal_momentum_metric",
                "deadline_countdown",
                "goal_notes"
            )
            TaskType.GENERAL -> listOf(
                "notes_brain_dump",
                "action_checklist",
                "deadline_countdown"
            )
        }

        return orderedTemplateIds.mapNotNull(::find)
    }

    fun find(id: String): TaskComponentTemplate? = templates.firstOrNull { it.id == id }

    fun buildSelection(
        templateIds: List<String>,
        now: Long,
        context: TaskComponentContext
    ): Pair<List<ComponentDSL>, List<ReminderDSL>> {
        val selectedTemplates = templateIds.distinct().mapNotNull(::find)
        val components = mutableListOf<ComponentDSL>()
        val reminders = mutableListOf<ReminderDSL>()

        selectedTemplates.forEachIndexed { index, template ->
            val built = template.build(index, now, context)
            components += built.component
            reminders += built.reminders
        }

        return components to reminders
    }

    private fun builtComponent(
        type: ComponentType,
        sortOrder: Int,
        config: ComponentConfig,
        extras: Map<String, JsonElement> = emptyMap(),
        reminders: List<ReminderDSL> = emptyList()
    ): BuiltTaskComponent {
        val baseConfig = auraJson.encodeToJsonElement(config).let { it as JsonObject }
        return BuiltTaskComponent(
            component = ComponentDSL(
                type = type,
                sortOrder = sortOrder,
                config = JsonObject(baseConfig + extras)
            ),
            reminders = reminders
        )
    }

    private const val HOUR_IN_MILLIS = 60 * 60 * 1000L
    private const val DAY_IN_MILLIS = 24 * HOUR_IN_MILLIS
}

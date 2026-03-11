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
import kotlinx.serialization.json.JsonArray
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
        TaskComponentTemplate(
            id = "travel_countdown",
            title = "Countdown",
            variantLabel = "Trip",
            subtitle = "Travel date with urgency and remaining time.",
            type = ComponentType.COUNTDOWN,
            supportedTaskTypes = setOf(TaskType.TRAVEL, TaskType.GENERAL),
            build = { sortOrder, now, context ->
                builtComponent(
                    type = ComponentType.COUNTDOWN,
                    sortOrder = sortOrder,
                    config = CountdownConfig(
                        targetDate = context.targetDateMs ?: now + DAY_IN_MILLIS * 14,
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
            supportedTaskTypes = setOf(TaskType.PROJECT, TaskType.GENERAL, TaskType.HEALTH),
            build = { sortOrder, now, context ->
                builtComponent(
                    type = ComponentType.COUNTDOWN,
                    sortOrder = sortOrder,
                    config = CountdownConfig(
                        targetDate = context.targetDateMs ?: now + DAY_IN_MILLIS * 7,
                        label = "Target date"
                    )
                )
            }
        ),
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
                    config = ChecklistConfig(
                        label = "Travel prep",
                        allowAddItems = true
                    ),
                    extras = mapOf(
                        "items" to jsonArrayOf("Passport", "Insurance", "Packing list")
                    )
                )
            }
        ),
        TaskComponentTemplate(
            id = "action_checklist",
            title = "Checklist",
            variantLabel = "Execution",
            subtitle = "Action plan, milestones and final review.",
            type = ComponentType.CHECKLIST,
            supportedTaskTypes = setOf(TaskType.PROJECT, TaskType.GENERAL, TaskType.HEALTH),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.CHECKLIST,
                    sortOrder = sortOrder,
                    config = ChecklistConfig(
                        label = "Key steps",
                        allowAddItems = true
                    ),
                    extras = mapOf(
                        "items" to jsonArrayOf("Define next step", "Execute", "Review result")
                    )
                )
            }
        ),
        TaskComponentTemplate(
            id = "habit_daily",
            title = "Habit Ring",
            variantLabel = "Daily",
            subtitle = "Simple daily streak with recurring reminder.",
            type = ComponentType.HABIT_RING,
            supportedTaskTypes = setOf(TaskType.HABIT, TaskType.HEALTH, TaskType.GENERAL),
            build = { sortOrder, now, context ->
                builtComponent(
                    type = ComponentType.HABIT_RING,
                    sortOrder = sortOrder,
                    config = HabitRingConfig(
                        frequency = "DAILY",
                        label = "Daily streak"
                    ),
                    reminders = listOf(
                        ReminderDSL(
                            scheduledAtMs = now + recurringHours(context.input) * HOUR_IN_MILLIS,
                            intervalDays = recurringHours(context.input) / 24f
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
                    config = HabitRingConfig(
                        frequency = "WEEKLY",
                        label = "Weekly rhythm"
                    ),
                    reminders = listOf(
                        ReminderDSL(
                            scheduledAtMs = now + DAY_IN_MILLIS,
                            intervalDays = 7f
                        )
                    )
                )
            }
        ),
        TaskComponentTemplate(
            id = "progress_manual",
            title = "Progress Bar",
            variantLabel = "Manual",
            subtitle = "Percentage driven manually by the user.",
            type = ComponentType.PROGRESS_BAR,
            supportedTaskTypes = setOf(TaskType.PROJECT, TaskType.GENERAL, TaskType.TRAVEL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.PROGRESS_BAR,
                    sortOrder = sortOrder,
                    config = ProgressBarConfig(
                        source = "MANUAL",
                        label = "Overall progress",
                        manualProgress = 0.15f
                    )
                )
            }
        ),
        TaskComponentTemplate(
            id = "progress_milestones",
            title = "Progress Bar",
            variantLabel = "Milestones",
            subtitle = "Progress framed around stages or deliverables.",
            type = ComponentType.PROGRESS_BAR,
            supportedTaskTypes = setOf(TaskType.PROJECT, TaskType.TRAVEL, TaskType.GENERAL),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.PROGRESS_BAR,
                    sortOrder = sortOrder,
                    config = ProgressBarConfig(
                        source = "MANUAL",
                        label = "Milestone progress",
                        manualProgress = 0.35f
                    )
                )
            }
        ),
        TaskComponentTemplate(
            id = "notes_brain_dump",
            title = "Notes",
            variantLabel = "Brain dump",
            subtitle = "Open markdown space for context and loose ideas.",
            type = ComponentType.NOTES,
            supportedTaskTypes = TaskType.entries.toSet(),
            build = { sortOrder, _, context ->
                builtComponent(
                    type = ComponentType.NOTES,
                    sortOrder = sortOrder,
                    config = NotesConfig(
                        text = if (context.title.isBlank()) "" else "### ${context.title}\n- Context\n- Next step\n- Notes",
                        isMarkdown = true
                    )
                )
            }
        ),
        TaskComponentTemplate(
            id = "notes_meeting",
            title = "Notes",
            variantLabel = "Meeting",
            subtitle = "Structured recap for summaries and action items.",
            type = ComponentType.NOTES,
            supportedTaskTypes = setOf(TaskType.PROJECT, TaskType.GENERAL, TaskType.FINANCE),
            build = { sortOrder, _, _ ->
                builtComponent(
                    type = ComponentType.NOTES,
                    sortOrder = sortOrder,
                    config = NotesConfig(
                        text = "### Summary\n- Decision\n- Owner\n- Next checkpoint",
                        isMarkdown = true
                    )
                )
            }
        ),
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
                    config = MetricTrackerConfig(
                        unit = "ml",
                        label = "Hydration",
                        history = listOf(350f, 700f, 1100f, 1450f, 1800f)
                    )
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
                    config = MetricTrackerConfig(
                        unit = "kg",
                        label = "Weight trend",
                        history = listOf(82.4f, 82.1f, 81.9f, 81.6f, 81.3f)
                    )
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
                    config = MetricTrackerConfig(
                        unit = "steps",
                        label = "Daily activity",
                        history = listOf(4200f, 6100f, 7800f, 8600f, 10200f)
                    )
                )
            }
        ),
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
            build = { sortOrder, now, _ ->
                builtComponent(
                    type = ComponentType.DATA_FEED,
                    sortOrder = sortOrder,
                    config = DataFeedConfig(
                        fetcherConfigId = "usd_ars",
                        displayLabel = "USD / ARS",
                        status = DataFeedStatus.STALE,
                        lastValue = "1 USD = 1,045 ARS",
                        lastUpdatedAt = now - HOUR_IN_MILLIS
                    )
                )
            }
        )
    )

    fun recommended(taskType: TaskType): List<TaskComponentTemplate> {
        return templates.filter { template -> taskType in template.supportedTaskTypes }
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

    private fun jsonArrayOf(vararg values: String): JsonArray {
        return JsonArray(values.map(::JsonPrimitive))
    }

    private fun recurringHours(input: String): Long {
        val explicit = Regex("\\bcada\\s+(\\d+)\\s+hor", RegexOption.IGNORE_CASE)
            .find(input)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        return explicit ?: 24L
    }

    private const val HOUR_IN_MILLIS = 60 * 60 * 1000L
    private const val DAY_IN_MILLIS = 24 * HOUR_IN_MILLIS
}

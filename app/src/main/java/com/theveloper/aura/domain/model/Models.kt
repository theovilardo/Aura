package com.theveloper.aura.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed class ComponentConfig

@Immutable
@Serializable
@SerialName("CHECKLIST")
data class ChecklistConfig(
    val label: String = "",
    val allowAddItems: Boolean = true
) : ComponentConfig()

@Immutable
@Serializable
@SerialName("PROGRESS_BAR")
data class ProgressBarConfig(
    val source: String = "MANUAL", // "SUBTASKS" or "MANUAL"
    val label: String = "",
    val manualProgress: Float? = null
) : ComponentConfig()

@Immutable
@Serializable
@SerialName("COUNTDOWN")
data class CountdownConfig(
    val targetDate: Long = 0L,
    val label: String = ""
) : ComponentConfig()

@Immutable
@Serializable
@SerialName("HABIT_RING")
data class HabitRingConfig(
    val frequency: String = "DAILY", // "DAILY", "WEEKLY"
    val label: String = "",
    val targetCount: Int? = null,
    val completedToday: Boolean = false,
    val streakCount: Int = 0
) : ComponentConfig()

@Immutable
@Serializable
@SerialName("NOTES")
data class NotesConfig(
    val text: String = "",
    val isMarkdown: Boolean = true
) : ComponentConfig()

@Immutable
@Serializable
@SerialName("METRIC_TRACKER")
data class MetricTrackerConfig(
    val unit: String = "", // e.g., "kg", "km"
    val label: String = "",
    val goal: Float? = null,
    val history: List<Float> = emptyList()
) : ComponentConfig()

@Immutable
@Serializable
@SerialName("DATA_FEED")
data class DataFeedConfig(
    val fetcherConfigId: String = "",
    val displayLabel: String = "",
    val status: DataFeedStatus = DataFeedStatus.DATA,
    val value: String? = null,
    val lastValue: String? = null,
    val lastUpdatedAt: Long? = null,
    val errorMessage: String? = null
) : ComponentConfig()

@Immutable
@Serializable
@SerialName("UNKNOWN")
class UnknownConfig : ComponentConfig()

@Immutable
data class Task(
    val id: String,
    val title: String,
    val type: TaskType,
    val status: TaskStatus = TaskStatus.ACTIVE,
    val priority: Int = 0,
    val targetDate: Long? = null,
    val components: List<TaskComponent> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Immutable
data class TaskComponent(
    val id: String,
    val taskId: String,
    val type: ComponentType,
    val sortOrder: Int,
    val config: ComponentConfig,
    val needsClarification: Boolean = false,
    val checklistItems: List<ChecklistItem> = emptyList()
)

@Immutable
data class ChecklistItem(
    val id: String,
    val componentId: String,
    val text: String,
    val isCompleted: Boolean = false,
    val isSuggested: Boolean = false,
    val sortOrder: Int
)

@Immutable
data class Reminder(
    val id: String,
    val taskId: String,
    val scheduledAt: Long,
    val intervalDays: Float,
    val easeFactor: Float,
    val repetitions: Int
)

@Immutable
data class Suggestion(
    val id: String,
    val taskId: String?,
    val type: SuggestionType,
    val status: SuggestionStatus,
    val payloadJson: String,
    val reasoning: String,
    val createdAt: Long,
    val expiresAt: Long
)

@Immutable
data class MemorySlot(
    val id: String,
    val category: MemoryCategory,
    val content: String,
    val lastUpdatedAt: Long,
    val version: Int = 0,
    val tokenCount: Int = 0,
    val maxTokens: Int = 300
)

// ── Multi-Creation-Type Domain Models ───────────────────────────────────────

/**
 * Standalone reminder — fires by date/time with optional recurrence.
 * Distinct from [Reminder] which is SM-2 spaced-repetition tied to tasks.
 */
@Immutable
data class AuraReminder(
    val id: String,
    val title: String,
    val body: String = "",
    val reminderType: ReminderType,
    val scheduledAt: Long,
    val repeatCount: Int = 0,
    val intervalMs: Long = 0L,
    val cronExpression: String = "",
    val linkedTaskId: String? = null,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val links: List<String> = emptyList(),
    val status: ReminderStatus = ReminderStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * LLM-driven scheduled process. The [executionPlan] is generated at creation time
 * and executed by [AutomationWorker] when the cron schedule fires.
 */
@Immutable
data class AuraAutomation(
    val id: String,
    val title: String,
    val prompt: String,
    val cronExpression: String,
    val executionPlan: AutomationExecutionPlan,
    val outputType: AutomationOutputType = AutomationOutputType.NOTIFICATION,
    val lastExecutionAt: Long? = null,
    val lastResultJson: String? = null,
    val status: AutomationStatus = AutomationStatus.ACTIVE,
    val failureCount: Int = 0,
    val maxRetries: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Describes the steps the engine must execute when an automation fires.
 * Generated by LLM at creation time; stored as JSON in the DB.
 */
@Serializable
@Immutable
data class AutomationExecutionPlan(
    val steps: List<AutomationStep> = emptyList()
)

@Serializable
@Immutable
data class AutomationStep(
    val type: AutomationStepType,
    val description: String = "",
    val params: Map<String, String> = emptyMap()
)

/**
 * Time-span entity (e.g. a festival, conference, sprint).
 * Contains [subActions] that execute during the event period
 * and [components] for visual tracking (reuses TaskComponent).
 */
@Immutable
data class AuraEvent(
    val id: String,
    val title: String,
    val description: String = "",
    val startAt: Long,
    val endAt: Long,
    val subActions: List<EventSubAction> = emptyList(),
    val components: List<TaskComponent> = emptyList(),
    val status: EventStatus = EventStatus.UPCOMING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** An action that runs during an active [AuraEvent]. */
@Immutable
data class EventSubAction(
    val id: String,
    val eventId: String,
    val type: EventSubActionType,
    val title: String = "",
    val cronExpression: String = "",
    val intervalMs: Long = 0L,
    val prompt: String = "",
    val config: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
)

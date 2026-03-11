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
    val checklistItems: List<ChecklistItem> = emptyList()
)

@Immutable
data class ChecklistItem(
    val id: String,
    val componentId: String,
    val text: String,
    val isCompleted: Boolean = false,
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

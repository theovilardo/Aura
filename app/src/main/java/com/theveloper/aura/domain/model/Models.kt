package com.theveloper.aura.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ComponentConfig

@Serializable
@SerialName("CHECKLIST")
data class ChecklistConfig(
    val label: String = "",
    val allowAddItems: Boolean = true
) : ComponentConfig()

@Serializable
@SerialName("PROGRESS_BAR")
data class ProgressBarConfig(
    val source: String = "MANUAL", // "SUBTASKS" or "MANUAL"
    val label: String = ""
) : ComponentConfig()

@Serializable
@SerialName("COUNTDOWN")
data class CountdownConfig(
    val targetDate: Long = 0L,
    val label: String = ""
) : ComponentConfig()

@Serializable
@SerialName("HABIT_RING")
data class HabitRingConfig(
    val frequency: String = "DAILY", // "DAILY", "WEEKLY"
    val label: String = ""
) : ComponentConfig()

@Serializable
@SerialName("NOTES")
data class NotesConfig(
    val text: String = "",
    val isMarkdown: Boolean = true
) : ComponentConfig()

@Serializable
@SerialName("METRIC_TRACKER")
data class MetricTrackerConfig(
    val unit: String = "", // e.g., "kg", "km"
    val label: String = ""
) : ComponentConfig()

@Serializable
@SerialName("DATA_FEED")
data class DataFeedConfig(
    val fetcherConfigId: String = "",
    val displayLabel: String = ""
) : ComponentConfig()

@Serializable
@SerialName("UNKNOWN")
class UnknownConfig : ComponentConfig()

data class Task(
    val id: String,
    val title: String,
    val type: TaskType,
    val status: TaskStatus = TaskStatus.ACTIVE,
    val priority: Int = 0,
    val targetDate: Long? = null,
    val components: List<TaskComponent> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class TaskComponent(
    val id: String,
    val taskId: String,
    val type: ComponentType,
    val sortOrder: Int,
    val config: ComponentConfig
)

data class ChecklistItem(
    val id: String,
    val componentId: String,
    val text: String,
    val isCompleted: Boolean = false,
    val sortOrder: Int
)

data class Reminder(
    val id: String,
    val taskId: String,
    val scheduledAt: Long,
    val intervalDays: Float,
    val easeFactor: Float,
    val repetitions: Int
)

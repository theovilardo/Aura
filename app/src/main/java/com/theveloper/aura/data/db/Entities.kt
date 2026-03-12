package com.theveloper.aura.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.FetcherType
import com.theveloper.aura.domain.model.MemoryCategory
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.domain.model.SuggestionType
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.model.TaskType

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: TaskType,
    val status: TaskStatus,
    val priority: Int = 0,
    @ColumnInfo(name = "target_date") val targetDate: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "task_components")
data class TaskComponentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    val type: ComponentType,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val config: String, // JSON representation of ComponentConfig
    @ColumnInfo(name = "needs_clarification", defaultValue = "0") val needsClarification: Boolean = false
)

@Entity(tableName = "checklist_items")
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "component_id") val componentId: String,
    val text: String,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_suggested", defaultValue = "0") val isSuggested: Boolean = false
)

@Entity(tableName = "habit_signals")
data class HabitSignalEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "signal_type") val signalType: SignalType,
    @ColumnInfo(name = "hour_of_day") val hourOfDay: Int,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long
)

@Entity(tableName = "user_patterns", primaryKeys = ["task_type", "hour_of_day", "day_of_week"])
data class UserPatternEntity(
    @ColumnInfo(name = "task_type") val taskType: TaskType,
    @ColumnInfo(name = "hour_of_day") val hourOfDay: Int,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "completion_rate") val completionRate: Float,
    @ColumnInfo(name = "dismiss_rate") val dismissRate: Float,
    @ColumnInfo(name = "avg_delay_ms") val avgDelayMs: Long,
    @ColumnInfo(name = "sample_size") val sampleSize: Int,
    val confidence: Float
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Long,
    @ColumnInfo(name = "interval_days") val intervalDays: Float,
    @ColumnInfo(name = "ease_factor") val easeFactor: Float,
    val repetitions: Int
)

@Entity(tableName = "fetcher_configs")
data class FetcherConfigEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    val type: FetcherType,
    val params: String, // JSON
    @ColumnInfo(name = "cron_expression") val cronExpression: String,
    @ColumnInfo(name = "last_result_json") val lastResultJson: String?,
    @ColumnInfo(name = "last_updated_at") val lastUpdatedAt: Long?
)

@Entity(tableName = "suggestions")
data class SuggestionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String?,
    val type: SuggestionType,
    val status: String, // PENDING, APPROVED, REJECTED, EXPIRED
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    val reasoning: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val payload: String, // JSON E2E encrypted
    @ColumnInfo(name = "synced_at") val syncedAt: Long?
)

@Entity(
    tableName = "memory_slots",
    indices = [Index(value = ["category"], unique = true)]
)
data class MemorySlotEntity(
    @PrimaryKey val id: String,
    val category: MemoryCategory,
    val content: String,
    @ColumnInfo(name = "last_updated_at") val lastUpdatedAt: Long,
    val version: Int = 0,
    @ColumnInfo(name = "token_count") val tokenCount: Int,
    @ColumnInfo(name = "max_tokens", defaultValue = "300") val maxTokens: Int = 300
)

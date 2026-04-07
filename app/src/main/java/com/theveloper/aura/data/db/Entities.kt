package com.theveloper.aura.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.aura.domain.model.AutomationOutputType
import com.theveloper.aura.domain.model.AutomationStatus
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.EventStatus
import com.theveloper.aura.domain.model.EventSubActionType
import com.theveloper.aura.domain.model.FetcherType
import com.theveloper.aura.domain.model.FunctionSkillRuntime
import com.theveloper.aura.domain.model.MemoryCategory
import com.theveloper.aura.domain.model.ReminderStatus
import com.theveloper.aura.domain.model.ReminderType
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.domain.model.SuggestionType
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.model.UiSkillRuntime

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: TaskType,
    val status: TaskStatus,
    val priority: Int = 0,
    @ColumnInfo(name = "target_date") val targetDate: Long? = null,
    @ColumnInfo(name = "function_skills_json", defaultValue = "'[]'") val functionSkillsJson: String = "[]",
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
    @ColumnInfo(name = "skill_id") val skillId: String? = null,
    @ColumnInfo(name = "skill_runtime") val skillRuntime: UiSkillRuntime? = null,
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

@Entity(
    tableName = "component_rules",
    indices = [
        Index(value = ["task_id"]),
        Index(value = ["trigger_component_id"])
    ]
)
data class ComponentRuleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "trigger_component_id") val triggerComponentId: String,
    @ColumnInfo(name = "trigger_event") val triggerEvent: String,
    @ColumnInfo(name = "trigger_condition_json") val triggerConditionJson: String? = null,
    @ColumnInfo(name = "target_component_id") val targetComponentId: String,
    val action: String,
    @ColumnInfo(name = "action_params_json") val actionParamsJson: String? = null,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean = true,
    val priority: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "created_by") val createdBy: String
)

@Entity(tableName = "paired_devices")
data class PairedDeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val platform: String,
    @ColumnInfo(name = "connection_url") val connectionUrl: String,
    @ColumnInfo(name = "relay_url") val relayUrl: String? = null,
    @ColumnInfo(name = "shared_secret") val sharedSecret: String = "",
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
    @ColumnInfo(name = "paired_at") val pairedAt: Long
)

// ── Multi-Creation-Type Entities ────────────────────────────────────────────

@Entity(
    tableName = "aura_reminders",
    indices = [Index(value = ["linked_task_id"])]
)
data class AuraReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String = "",
    @ColumnInfo(name = "reminder_type") val reminderType: ReminderType,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Long,
    @ColumnInfo(name = "repeat_count") val repeatCount: Int = 0,
    @ColumnInfo(name = "interval_ms") val intervalMs: Long = 0L,
    @ColumnInfo(name = "cron_expression") val cronExpression: String = "",
    @ColumnInfo(name = "linked_task_id") val linkedTaskId: String? = null,
    val links: String = "[]", // JSON array of URLs
    val status: ReminderStatus = ReminderStatus.PENDING,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "aura_automations")
data class AuraAutomationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val prompt: String,
    @ColumnInfo(name = "cron_expression") val cronExpression: String,
    @ColumnInfo(name = "execution_plan") val executionPlan: String, // JSON AutomationExecutionPlan
    @ColumnInfo(name = "output_type") val outputType: AutomationOutputType =
        AutomationOutputType.NOTIFICATION,
    @ColumnInfo(name = "last_execution_at") val lastExecutionAt: Long? = null,
    @ColumnInfo(name = "last_result_json") val lastResultJson: String? = null,
    val status: AutomationStatus = AutomationStatus.ACTIVE,
    @ColumnInfo(name = "failure_count") val failureCount: Int = 0,
    @ColumnInfo(name = "max_retries") val maxRetries: Int = 3,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "aura_events")
data class AuraEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "end_at") val endAt: Long,
    val status: EventStatus = EventStatus.UPCOMING,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "event_sub_actions",
    indices = [Index(value = ["event_id"])],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = AuraEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class EventSubActionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    val type: EventSubActionType,
    val title: String = "",
    @ColumnInfo(name = "cron_expression") val cronExpression: String = "",
    @ColumnInfo(name = "interval_ms") val intervalMs: Long = 0L,
    val prompt: String = "",
    val config: String = "{}", // JSON map
    val enabled: Boolean = true
)

/**
 * Links [TaskComponentEntity] to an [AuraEventEntity].
 * Events reuse the task component system for visual tracking.
 */
@Entity(
    tableName = "event_components",
    indices = [Index(value = ["event_id"])],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = AuraEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class EventComponentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "component_id") val componentId: String
)

/**
 * Links [ChecklistItemEntity] to an [AuraReminderEntity].
 * Reminders can carry embedded checklists.
 */
@Entity(
    tableName = "reminder_checklist_items",
    indices = [Index(value = ["reminder_id"])],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = AuraReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminder_id"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class ReminderChecklistItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "reminder_id") val reminderId: String,
    val text: String,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int
)

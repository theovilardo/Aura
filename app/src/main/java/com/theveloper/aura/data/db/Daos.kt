package com.theveloper.aura.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.theveloper.aura.domain.model.TaskType
import kotlinx.coroutines.flow.Flow

data class TaskComponentWithItems(
    @Embedded val component: TaskComponentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "component_id"
    )
    val checklistItems: List<ChecklistItemEntity>
)

data class TaskWithDetails(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "task_id",
        entity = TaskComponentEntity::class
    )
    val components: List<TaskComponentWithItems>,
    @Relation(
        parentColumn = "id",
        entityColumn = "task_id"
    )
    val reminders: List<ReminderEntity>
)

@Dao
interface TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks ORDER BY created_at DESC")
    fun getTasksDetailedFlow(): Flow<List<TaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskWithDetails(taskId: String): TaskWithDetails?

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun getTaskWithDetailsFlow(taskId: String): Flow<TaskWithDetails?>

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTaskCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)
}

@Dao
interface TaskComponentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComponents(components: List<TaskComponentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComponent(component: TaskComponentEntity)

    @Update
    suspend fun updateComponent(component: TaskComponentEntity)

    @Delete
    suspend fun deleteComponent(component: TaskComponentEntity)

    @Query("SELECT * FROM task_components WHERE task_id = :taskId ORDER BY sort_order ASC")
    fun getComponentsForTask(taskId: String): Flow<List<TaskComponentEntity>>

    @Query("DELETE FROM task_components WHERE task_id = :taskId")
    suspend fun deleteComponentsForTask(taskId: String)
}

@Dao
interface ChecklistItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ChecklistItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ChecklistItemEntity)

    @Update
    suspend fun update(item: ChecklistItemEntity)

    @Delete
    suspend fun delete(item: ChecklistItemEntity)

    @Query("SELECT * FROM checklist_items WHERE component_id = :componentId ORDER BY sort_order ASC")
    suspend fun getItemsForComponent(componentId: String): List<ChecklistItemEntity>

    @Query("DELETE FROM checklist_items WHERE component_id IN (SELECT id FROM task_components WHERE task_id = :taskId)")
    suspend fun deleteItemsForTask(taskId: String)
}

@Dao
interface HabitSignalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) // append-only as required
    suspend fun insert(signal: HabitSignalEntity)

    @Query("SELECT * FROM habit_signals WHERE task_id = :taskId")
    suspend fun getSignalsForTask(taskId: String): List<HabitSignalEntity>

    @Query("SELECT * FROM habit_signals WHERE task_id IN (:taskIds)")
    suspend fun getSignalsForTasks(taskIds: List<String>): List<HabitSignalEntity>

    @Query("SELECT * FROM habit_signals WHERE hour_of_day = :hourOfDay AND day_of_week = :dayOfWeek")
    suspend fun getSignalsByTimeWindow(hourOfDay: Int, dayOfWeek: Int): List<HabitSignalEntity>
}

@Dao
interface UserPatternDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(pattern: UserPatternEntity)

    @Query("SELECT * FROM user_patterns WHERE task_type = :taskType")
    suspend fun getPatternsForType(taskType: TaskType): List<UserPatternEntity>
}

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity)

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE scheduled_at < :beforeTime")
    fun getActiveRemindersScheduled(beforeTime: Long): Flow<List<ReminderEntity>>

    @Query("DELETE FROM reminders WHERE task_id = :taskId")
    suspend fun deleteRemindersForTask(taskId: String)
}

@Dao
interface FetcherConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: FetcherConfigEntity)

    @Update
    suspend fun update(config: FetcherConfigEntity)

    @Delete
    suspend fun delete(config: FetcherConfigEntity)

    @Query("SELECT * FROM fetcher_configs WHERE task_id = :taskId")
    suspend fun getConfigsForTask(taskId: String): List<FetcherConfigEntity>

    @Query("SELECT * FROM fetcher_configs")
    suspend fun getActiveConfigs(): List<FetcherConfigEntity>

    @Query("SELECT * FROM fetcher_configs WHERE id = :id")
    fun getConfigFlow(id: String): Flow<FetcherConfigEntity?>

    @Query("SELECT * FROM fetcher_configs WHERE id = :id")
    suspend fun getConfigById(id: String): FetcherConfigEntity?

    @Query("UPDATE fetcher_configs SET last_result_json = :json, last_updated_at = :timestamp WHERE id = :configId")
    suspend fun updateLastResult(configId: String, json: String, timestamp: Long)
}

@Dao
interface SuggestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(suggestion: SuggestionEntity)

    @Query("SELECT * FROM suggestions WHERE status = 'PENDING' AND expires_at > :currentTime")
    fun getPendingSuggestions(currentTime: Long): Flow<List<SuggestionEntity>>

    @Query("UPDATE suggestions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE suggestions SET status = 'EXPIRED' WHERE expires_at < :currentTime AND status = 'PENDING'")
    suspend fun markExpired(currentTime: Long)
}

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE synced_at IS NULL")
    suspend fun getUnsyncedItems(): List<SyncQueueEntity>

    @Update
    suspend fun update(item: SyncQueueEntity)
}

@Dao
interface MemorySlotDao {
    @Query("SELECT * FROM memory_slots ORDER BY category ASC")
    suspend fun getAll(): List<MemorySlotEntity>

    @Query("SELECT * FROM memory_slots WHERE category = :category LIMIT 1")
    suspend fun getByCategory(category: com.theveloper.aura.domain.model.MemoryCategory): MemorySlotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(slot: MemorySlotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(slots: List<MemorySlotEntity>)
}

@Dao
interface ComponentRuleDao {
    @Query("SELECT * FROM component_rules WHERE task_id = :taskId AND is_enabled = 1 ORDER BY priority ASC")
    fun getRulesForTask(taskId: String): Flow<List<ComponentRuleEntity>>

    @Query("SELECT * FROM component_rules WHERE trigger_component_id = :componentId AND is_enabled = 1 ORDER BY priority ASC")
    suspend fun getRulesForComponent(componentId: String): List<ComponentRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ComponentRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<ComponentRuleEntity>)

    @Query("UPDATE component_rules SET is_enabled = :enabled WHERE id = :ruleId")
    suspend fun setRuleEnabled(ruleId: String, enabled: Boolean)

    @Query("DELETE FROM component_rules WHERE task_id = :taskId")
    suspend fun deleteRulesForTask(taskId: String)

    @Delete
    suspend fun deleteRule(rule: ComponentRuleEntity)
}

@Dao
interface PairedDeviceDao {
    @Query("SELECT * FROM paired_devices ORDER BY last_seen_at DESC")
    suspend fun getAll(): List<PairedDeviceEntity>

    @Query("SELECT * FROM paired_devices ORDER BY last_seen_at DESC")
    fun observeAll(): Flow<List<PairedDeviceEntity>>

    @Query("SELECT * FROM paired_devices WHERE id = :deviceId")
    suspend fun getById(deviceId: String): PairedDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: PairedDeviceEntity)

    @Query("UPDATE paired_devices SET last_seen_at = :timestamp WHERE id = :deviceId")
    suspend fun updateLastSeen(deviceId: String, timestamp: Long)

    @Query("DELETE FROM paired_devices WHERE id = :deviceId")
    suspend fun delete(deviceId: String)
}

// ── Multi-Creation-Type DAOs ────────────────────────────────────────────────

data class AuraReminderWithChecklist(
    @Embedded val reminder: AuraReminderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "reminder_id"
    )
    val checklistItems: List<ReminderChecklistItemEntity>
)

data class AuraEventWithDetails(
    @Embedded val event: AuraEventEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "event_id"
    )
    val subActions: List<EventSubActionEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "event_id",
        entity = EventComponentEntity::class
    )
    val componentLinks: List<EventComponentEntity>
)

@Dao
interface AuraReminderDao {
    @Transaction
    @Query("SELECT * FROM aura_reminders ORDER BY scheduled_at ASC")
    fun observeAll(): Flow<List<AuraReminderWithChecklist>>

    @Transaction
    @Query("SELECT * FROM aura_reminders WHERE id = :id")
    suspend fun getWithChecklist(id: String): AuraReminderWithChecklist?

    @Transaction
    @Query("SELECT * FROM aura_reminders WHERE id = :id")
    fun observeById(id: String): Flow<AuraReminderWithChecklist?>

    @Query("SELECT * FROM aura_reminders WHERE status = 'PENDING' AND scheduled_at <= :beforeTime")
    suspend fun getDueReminders(beforeTime: Long): List<AuraReminderEntity>

    @Query("SELECT * FROM aura_reminders WHERE status = 'PENDING' ORDER BY scheduled_at ASC")
    suspend fun getUpcoming(): List<AuraReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: AuraReminderEntity)

    @Update
    suspend fun update(reminder: AuraReminderEntity)

    @Query("UPDATE aura_reminders SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: com.theveloper.aura.domain.model.ReminderStatus, now: Long)

    @Query("DELETE FROM aura_reminders WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ReminderChecklistItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ReminderChecklistItemEntity>)

    @Update
    suspend fun update(item: ReminderChecklistItemEntity)

    @Query("DELETE FROM reminder_checklist_items WHERE reminder_id = :reminderId")
    suspend fun deleteForReminder(reminderId: String)
}

@Dao
interface AuraAutomationDao {
    @Query("SELECT * FROM aura_automations ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AuraAutomationEntity>>

    @Query("SELECT * FROM aura_automations WHERE id = :id")
    suspend fun getById(id: String): AuraAutomationEntity?

    @Query("SELECT * FROM aura_automations WHERE id = :id")
    fun observeById(id: String): Flow<AuraAutomationEntity?>

    @Query("SELECT * FROM aura_automations WHERE status = 'ACTIVE'")
    suspend fun getActive(): List<AuraAutomationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(automation: AuraAutomationEntity)

    @Update
    suspend fun update(automation: AuraAutomationEntity)

    @Query("UPDATE aura_automations SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: com.theveloper.aura.domain.model.AutomationStatus, now: Long)

    @Query("""
        UPDATE aura_automations
        SET last_execution_at = :executedAt, last_result_json = :resultJson,
            failure_count = :failureCount, updated_at = :now
        WHERE id = :id
    """)
    suspend fun updateExecutionResult(
        id: String, executedAt: Long, resultJson: String?, failureCount: Int, now: Long
    )

    @Query("DELETE FROM aura_automations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AuraEventDao {
    @Transaction
    @Query("SELECT * FROM aura_events ORDER BY start_at ASC")
    fun observeAll(): Flow<List<AuraEventWithDetails>>

    @Transaction
    @Query("SELECT * FROM aura_events WHERE id = :id")
    suspend fun getWithDetails(id: String): AuraEventWithDetails?

    @Transaction
    @Query("SELECT * FROM aura_events WHERE id = :id")
    fun observeById(id: String): Flow<AuraEventWithDetails?>

    @Query("SELECT * FROM aura_events WHERE status = 'UPCOMING' AND start_at <= :now")
    suspend fun getEventsToActivate(now: Long): List<AuraEventEntity>

    @Query("SELECT * FROM aura_events WHERE status = 'ACTIVE' AND end_at <= :now")
    suspend fun getEventsToComplete(now: Long): List<AuraEventEntity>

    @Query("SELECT * FROM aura_events WHERE status = 'ACTIVE'")
    suspend fun getActiveEvents(): List<AuraEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AuraEventEntity)

    @Update
    suspend fun update(event: AuraEventEntity)

    @Query("UPDATE aura_events SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: com.theveloper.aura.domain.model.EventStatus, now: Long)

    @Query("DELETE FROM aura_events WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface EventSubActionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subActions: List<EventSubActionEntity>)

    @Update
    suspend fun update(subAction: EventSubActionEntity)

    @Query("SELECT * FROM event_sub_actions WHERE event_id = :eventId AND enabled = 1")
    suspend fun getEnabledForEvent(eventId: String): List<EventSubActionEntity>

    @Query("DELETE FROM event_sub_actions WHERE event_id = :eventId")
    suspend fun deleteForEvent(eventId: String)
}

@Dao
interface EventComponentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<EventComponentEntity>)

    @Query("SELECT component_id FROM event_components WHERE event_id = :eventId")
    suspend fun getComponentIdsForEvent(eventId: String): List<String>

    @Query("DELETE FROM event_components WHERE event_id = :eventId")
    suspend fun deleteForEvent(eventId: String)
}

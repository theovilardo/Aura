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

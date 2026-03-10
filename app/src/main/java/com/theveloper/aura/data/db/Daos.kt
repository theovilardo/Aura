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

data class TaskWithComponents(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "task_id"
    )
    val components: List<TaskComponentEntity>
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun getTasksFlow(): Flow<List<TaskEntity>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskWithComponents(taskId: String): TaskWithComponents?

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
    suspend fun insertComponent(component: TaskComponentEntity)

    @Update
    suspend fun updateComponent(component: TaskComponentEntity)

    @Delete
    suspend fun deleteComponent(component: TaskComponentEntity)

    @Query("SELECT * FROM task_components WHERE task_id = :taskId ORDER BY sort_order ASC")
    fun getComponentsForTask(taskId: String): Flow<List<TaskComponentEntity>>
}

@Dao
interface ChecklistItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ChecklistItemEntity)

    @Update
    suspend fun update(item: ChecklistItemEntity)

    @Delete
    suspend fun delete(item: ChecklistItemEntity)

    @Query("SELECT * FROM checklist_items WHERE component_id = :componentId ORDER BY sort_order ASC")
    suspend fun getItemsForComponent(componentId: String): List<ChecklistItemEntity>
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
    suspend fun insert(reminder: ReminderEntity)

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE scheduled_at < :beforeTime")
    fun getActiveRemindersScheduled(beforeTime: Long): Flow<List<ReminderEntity>>
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
}

@Dao
interface SuggestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(suggestion: SuggestionEntity)

    @Query("SELECT * FROM suggestions WHERE status = 'PENDING' AND expires_at > :currentTime")
    fun getPendingSuggestions(currentTime: Long): Flow<List<SuggestionEntity>>

    @Query("UPDATE suggestions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM suggestions WHERE expires_at < :currentTime")
    suspend fun deleteExpired(currentTime: Long)
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

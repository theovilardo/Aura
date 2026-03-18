package com.theveloper.aura.data.repository

import androidx.room.withTransaction
import com.theveloper.aura.data.db.AuraDatabase
import com.theveloper.aura.data.db.ChecklistItemEntity
import com.theveloper.aura.data.db.ReminderEntity
import com.theveloper.aura.data.db.TaskComponentEntity
import com.theveloper.aura.data.db.TaskEntity
import com.theveloper.aura.data.mapper.toDomain
import com.theveloper.aura.data.mapper.toEntity
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val db: AuraDatabase
) : TaskRepository {
    private val taskDao = db.taskDao()
    private val taskComponentDao = db.taskComponentDao()
    private val checklistItemDao = db.checklistItemDao()
    private val reminderDao = db.reminderDao()

    override fun getTasksFlow(): Flow<List<Task>> {
        return taskDao.getTasksDetailedFlow().map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(Dispatchers.Default)
    }

    override fun getTaskFlow(taskId: String): Flow<Task?> {
        return taskDao.getTaskWithDetailsFlow(taskId)
            .map { it?.toDomain() }
            .flowOn(Dispatchers.Default)
    }

    override suspend fun getTask(taskId: String): Task? {
        return withContext(Dispatchers.Default) {
            taskDao.getTaskWithDetails(taskId)?.toDomain()
        }
    }

    override suspend fun insertTask(task: Task) {
        val payload = task.toWritePayload()
        db.withTransaction {
            taskDao.insertTask(payload.task)
            if (payload.components.isNotEmpty()) {
                taskComponentDao.insertComponents(payload.components)
            }
            if (payload.checklistItems.isNotEmpty()) {
                checklistItemDao.insertItems(payload.checklistItems)
            }
            if (payload.reminders.isNotEmpty()) {
                reminderDao.insertReminders(payload.reminders)
            }
        }
    }

    override suspend fun updateTask(task: Task) {
        val payload = task.toWritePayload()
        db.withTransaction {
            checklistItemDao.deleteItemsForTask(task.id)
            taskComponentDao.deleteComponentsForTask(task.id)
            reminderDao.deleteRemindersForTask(task.id)

            taskDao.updateTask(payload.task)
            if (payload.components.isNotEmpty()) {
                taskComponentDao.insertComponents(payload.components)
            }
            if (payload.checklistItems.isNotEmpty()) {
                checklistItemDao.insertItems(payload.checklistItems)
            }
            if (payload.reminders.isNotEmpty()) {
                reminderDao.insertReminders(payload.reminders)
            }
        }
    }

    override suspend fun deleteTask(task: Task) {
        db.withTransaction {
            checklistItemDao.deleteItemsForTask(task.id)
            taskComponentDao.deleteComponentsForTask(task.id)
            reminderDao.deleteRemindersForTask(task.id)
            taskDao.deleteTask(task.toEntity())
        }
    }

    private suspend fun Task.toWritePayload(): TaskWritePayload = withContext(Dispatchers.Default) {
        TaskWritePayload(
            task = toEntity(),
            components = components.map { it.toEntity() },
            checklistItems = components.flatMap { component -> component.checklistItems }.map { it.toEntity() },
            reminders = reminders.map { it.toEntity() }
        )
    }

    private data class TaskWritePayload(
        val task: TaskEntity,
        val components: List<TaskComponentEntity>,
        val checklistItems: List<ChecklistItemEntity>,
        val reminders: List<ReminderEntity>
    )
}

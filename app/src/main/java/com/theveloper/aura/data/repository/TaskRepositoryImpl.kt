package com.theveloper.aura.data.repository

import androidx.room.withTransaction
import com.theveloper.aura.data.db.AuraDatabase
import com.theveloper.aura.data.mapper.toDomain
import com.theveloper.aura.data.mapper.toEntity
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        }
    }

    override fun getTaskFlow(taskId: String): Flow<Task?> {
        return taskDao.getTaskWithDetailsFlow(taskId).map { it?.toDomain() }
    }

    override suspend fun getTask(taskId: String): Task? {
        return taskDao.getTaskWithDetails(taskId)?.toDomain()
    }

    override suspend fun insertTask(task: Task) {
        db.withTransaction {
            taskDao.insertTask(task.toEntity())
            if (task.components.isNotEmpty()) {
                taskComponentDao.insertComponents(task.components.map { it.toEntity() })
            }
            val checklistItems = task.components.flatMap { component -> component.checklistItems }
            if (checklistItems.isNotEmpty()) {
                checklistItemDao.insertItems(checklistItems.map { it.toEntity() })
            }
            if (task.reminders.isNotEmpty()) {
                reminderDao.insertReminders(task.reminders.map { it.toEntity() })
            }
        }
    }

    override suspend fun updateTask(task: Task) {
        db.withTransaction {
            checklistItemDao.deleteItemsForTask(task.id)
            taskComponentDao.deleteComponentsForTask(task.id)
            reminderDao.deleteRemindersForTask(task.id)

            taskDao.updateTask(task.toEntity())
            if (task.components.isNotEmpty()) {
                taskComponentDao.insertComponents(task.components.map { it.toEntity() })
            }
            val checklistItems = task.components.flatMap { component -> component.checklistItems }
            if (checklistItems.isNotEmpty()) {
                checklistItemDao.insertItems(checklistItems.map { it.toEntity() })
            }
            if (task.reminders.isNotEmpty()) {
                reminderDao.insertReminders(task.reminders.map { it.toEntity() })
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
}

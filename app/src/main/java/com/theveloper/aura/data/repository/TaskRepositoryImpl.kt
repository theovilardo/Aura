package com.theveloper.aura.data.repository

import com.theveloper.aura.data.db.TaskComponentDao
import com.theveloper.aura.data.db.TaskDao
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
    private val taskDao: TaskDao,
    private val taskComponentDao: TaskComponentDao
) : TaskRepository {
    override fun getTasksFlow(): Flow<List<Task>> {
        return taskDao.getTasksFlow().map { entities -> 
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTask(taskId: String): Task? {
        return taskDao.getTaskWithComponents(taskId)?.toDomain()
    }

    override suspend fun insertTask(task: Task) {
        taskDao.insertTask(task.toEntity())
        task.components.forEach {
            taskComponentDao.insertComponent(it.toEntity())
        }
    }

    override suspend fun updateTask(task: Task) {
        taskDao.updateTask(task.toEntity())
    }

    override suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task.toEntity())
    }
}

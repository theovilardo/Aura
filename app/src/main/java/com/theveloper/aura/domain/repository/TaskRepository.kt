package com.theveloper.aura.domain.repository

import com.theveloper.aura.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun getTasksFlow(): Flow<List<Task>>
    suspend fun getTask(taskId: String): Task?
    suspend fun insertTask(task: Task)
    suspend fun updateTask(task: Task)
    suspend fun deleteTask(task: Task)
}

package com.theveloper.aura.data.repository

import com.theveloper.aura.domain.model.*
import com.theveloper.aura.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeTaskRepository @Inject constructor() : TaskRepository {

    private val tasks = MutableStateFlow<List<Task>>(emptyList())

    override fun getTasksFlow(): Flow<List<Task>> = tasks

    override fun getTaskFlow(taskId: String): Flow<Task?> = tasks.map { current ->
        current.find { it.id == taskId }
    }

    override suspend fun getTask(taskId: String): Task? = tasks.value.find { it.id == taskId }

    override suspend fun insertTask(task: Task) {
        tasks.update { it + task }
    }

    override suspend fun updateTask(task: Task) {
        tasks.update { current ->
            current.map { if (it.id == task.id) task else it }
        }
    }

    override suspend fun deleteTask(task: Task) {
        tasks.update { current ->
            current.filter { it.id != task.id }
        }
    }
}

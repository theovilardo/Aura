package com.theveloper.aura.domain.usecase

import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.repository.TaskRepository
import javax.inject.Inject

class UpdateTaskStatusUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(taskId: String, status: TaskStatus): Task {
        val task = requireNotNull(taskRepository.getTask(taskId)) {
            "Task $taskId was not found."
        }
        val updatedTask = task.copy(
            status = status,
            updatedAt = System.currentTimeMillis()
        )
        taskRepository.updateTask(updatedTask)
        return updatedTask
    }
}

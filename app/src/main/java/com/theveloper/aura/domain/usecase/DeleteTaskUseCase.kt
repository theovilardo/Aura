package com.theveloper.aura.domain.usecase

import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.repository.TaskRepository
import javax.inject.Inject

class DeleteTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(taskId: String): Task {
        val task = requireNotNull(taskRepository.getTask(taskId)) {
            "Task $taskId was not found."
        }
        taskRepository.deleteTask(task)
        return task
    }
}

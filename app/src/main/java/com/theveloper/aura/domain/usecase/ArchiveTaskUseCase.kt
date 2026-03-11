package com.theveloper.aura.domain.usecase

import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskStatus
import javax.inject.Inject

class ArchiveTaskUseCase @Inject constructor(
    private val updateTaskStatusUseCase: UpdateTaskStatusUseCase
) {
    suspend operator fun invoke(taskId: String): Task {
        return updateTaskStatusUseCase(taskId, TaskStatus.ARCHIVED)
    }
}

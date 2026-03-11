package com.theveloper.aura.domain.usecase

import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.repository.TaskRepository
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.dsl.TaskDslMapper
import javax.inject.Inject

class CreateTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(dsl: TaskDSLOutput): Task {
        val task = TaskDslMapper.toTask(dsl)
        taskRepository.insertTask(task)
        return task
    }
}

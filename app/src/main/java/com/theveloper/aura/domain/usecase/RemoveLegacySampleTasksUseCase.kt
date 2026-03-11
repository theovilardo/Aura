package com.theveloper.aura.domain.usecase

import com.theveloper.aura.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RemoveLegacySampleTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke() {
        val tasks = taskRepository.getTasksFlow().first()
        tasks.filter { task -> task.id in LEGACY_SAMPLE_IDS }
            .forEach { task -> taskRepository.deleteTask(task) }
    }

    companion object {
        private val LEGACY_SAMPLE_IDS = setOf(
            "travel-madrid",
            "habit-water",
            "project-aura",
            "health-weight",
            "finance-usd-data",
            "finance-weather-loading",
            "finance-error",
            "finance-stale"
        )
    }
}

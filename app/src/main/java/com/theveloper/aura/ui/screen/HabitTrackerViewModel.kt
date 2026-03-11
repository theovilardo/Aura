package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.data.db.HabitSignalEntity
import com.theveloper.aura.data.repository.AppSettingsRepository
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.repository.HabitRepository
import com.theveloper.aura.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HabitTrackerState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val doneToday: Int = 0,
    val totalActive: Int = 0,
    val habitItems: List<HabitItemUiState> = emptyList()
)

data class HabitItemUiState(
    val taskId: String,
    val title: String,
    val type: TaskType,
    val streakDays: Int,
    val completionRate7d: Float,
    val completionRate30d: Float,
    /** 30 entries: index 0 = 29 days ago, index 29 = today */
    val completionGrid30d: List<Boolean>,
    val completedToday: Boolean
)

@HiltViewModel
class HabitTrackerViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitTrackerState())
    val uiState: StateFlow<HabitTrackerState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                taskRepository.getTasksFlow(),
                appSettingsRepository.settingsFlow
            ) { tasks, settings ->
                tasks to settings.developerMockHabitDataEnabled
            }.collect { (tasks, developerMockHabitDataEnabled) ->
                val habitItems = if (developerMockHabitDataEnabled) {
                    buildMockHabitItems()
                } else {
                    val habitTasks = tasks.filter {
                        (it.type == TaskType.HABIT || it.type == TaskType.HEALTH) &&
                            it.status == TaskStatus.ACTIVE
                    }
                    habitTasks.map { task ->
                        val signals = habitRepository.getSignalsForTask(task.id)
                        buildHabitItem(task, signals)
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        today = LocalDate.now(),
                        totalActive = habitItems.size,
                        doneToday = habitItems.count { item -> item.completedToday },
                        habitItems = habitItems
                    )
                }
            }
        }
    }

    private fun buildHabitItem(task: Task, signals: List<HabitSignalEntity>): HabitItemUiState {
        val completedSignals = signals.filter { it.signalType == SignalType.TASK_COMPLETED }
        val today = LocalDate.now()
        val zoneId = ZoneId.systemDefault()

        fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

        // 30-day completion grid: index 0 = 29 days ago, index 29 = today
        val grid30d = (29 downTo 0).map { daysAgo ->
            val day = today.minusDays(daysAgo.toLong())
            completedSignals.any { it.recordedAt.toDate() == day }
        }

        val completedToday = grid30d.last()

        return habitItemFromGrid(
            taskId = task.id,
            title = task.title,
            type = task.type,
            grid30d = grid30d
        )
    }

    private fun buildMockHabitItems(): List<HabitItemUiState> {
        return listOf(
            buildMockHabitItem(
                taskId = "mock-habit-strength",
                title = "Morning Strength",
                type = TaskType.HABIT,
                completedDaysAgo = setOf(0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 12, 13, 14, 15, 16, 17, 18, 20, 21, 22, 23, 24, 25, 27, 28)
            ),
            buildMockHabitItem(
                taskId = "mock-habit-reading",
                title = "Read 20 min",
                type = TaskType.HABIT,
                completedDaysAgo = setOf(0, 1, 3, 4, 6, 7, 8, 10, 11, 12, 14, 17, 18, 19, 21, 22, 24, 25, 26, 28)
            ),
            buildMockHabitItem(
                taskId = "mock-health-vitamins",
                title = "Supplements",
                type = TaskType.HEALTH,
                completedDaysAgo = setOf(0, 1, 2, 4, 5, 7, 8, 9, 11, 12, 14, 15, 16, 18, 19, 21, 22, 24, 25, 26, 27, 28, 29)
            ),
            buildMockHabitItem(
                taskId = "mock-health-sleep",
                title = "Sleep wind-down",
                type = TaskType.HEALTH,
                completedDaysAgo = setOf(0, 2, 3, 4, 5, 6, 8, 9, 10, 12, 13, 15, 16, 17, 18, 20, 21, 22, 23, 24, 26, 27, 28)
            )
        )
    }

    private fun buildMockHabitItem(
        taskId: String,
        title: String,
        type: TaskType,
        completedDaysAgo: Set<Int>
    ): HabitItemUiState {
        val grid30d = (29 downTo 0).map { daysAgo -> daysAgo in completedDaysAgo }
        return habitItemFromGrid(
            taskId = taskId,
            title = title,
            type = type,
            grid30d = grid30d
        )
    }

    private fun habitItemFromGrid(
        taskId: String,
        title: String,
        type: TaskType,
        grid30d: List<Boolean>
    ): HabitItemUiState {
        val completedToday = grid30d.lastOrNull() == true
        var streak = 0
        var index = if (completedToday) grid30d.lastIndex else grid30d.lastIndex - 1

        while (index >= 0 && grid30d[index]) {
            streak++
            index--
        }

        return HabitItemUiState(
            taskId = taskId,
            title = title,
            type = type,
            streakDays = streak,
            completionRate7d = grid30d.takeLast(7).count { it } / 7f,
            completionRate30d = grid30d.count { it } / 30f,
            completionGrid30d = grid30d,
            completedToday = completedToday
        )
    }
}

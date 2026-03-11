package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.data.db.HabitSignalEntity
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.repository.HabitRepository
import com.theveloper.aura.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val habitRepository: HabitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitTrackerState())
    val uiState: StateFlow<HabitTrackerState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            taskRepository.getTasksFlow().collect { tasks ->
                val habitTasks = tasks.filter {
                    (it.type == TaskType.HABIT || it.type == TaskType.HEALTH) &&
                        it.status == TaskStatus.ACTIVE
                }
                val habitItems = habitTasks.map { task ->
                    val signals = habitRepository.getSignalsForTask(task.id)
                    buildHabitItem(task, signals)
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        today = LocalDate.now(),
                        totalActive = habitTasks.size,
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

        // Streak: consecutive completed days ending with today (if done) or yesterday
        var streak = 0
        val startOffset = if (completedToday) 0L else 1L
        for (daysAgo in startOffset..364L) {
            val day = today.minusDays(daysAgo)
            if (completedSignals.any { it.recordedAt.toDate() == day }) {
                streak++
            } else {
                break
            }
        }

        val rate7d = grid30d.takeLast(7).count { it } / 7f
        val rate30d = grid30d.count { it } / 30f

        return HabitItemUiState(
            taskId = task.id,
            title = task.title,
            type = task.type,
            streakDays = streak,
            completionRate7d = rate7d,
            completionRate30d = rate30d,
            completionGrid30d = grid30d,
            completedToday = completedToday
        )
    }
}

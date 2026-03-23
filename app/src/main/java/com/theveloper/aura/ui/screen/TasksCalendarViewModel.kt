package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class TasksCalendarUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val eventsByDate: Map<LocalDate, List<CalendarTaskEventUiState>> = emptyMap(),
    val totalEventCount: Int = 0
)

data class CalendarTaskEventUiState(
    val instanceId: String,
    val taskId: String,
    val title: String,
    val type: TaskType,
    val status: TaskStatus,
    val timestamp: Long,
    val source: CalendarEventSource,
    val isAllDay: Boolean
)

enum class CalendarEventSource {
    TARGET_DATE,
    REMINDER
}

@HiltViewModel
class TasksCalendarViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksCalendarUiState())
    val uiState: StateFlow<TasksCalendarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            taskRepository.getTasksFlow().collectLatest { tasks ->
                val today = LocalDate.now()
                val eventsByDate = withContext(Dispatchers.Default) {
                    buildCalendarEvents(tasks)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        today = today,
                        eventsByDate = eventsByDate,
                        totalEventCount = eventsByDate.values.sumOf(List<CalendarTaskEventUiState>::size)
                    )
                }
            }
        }
    }

    private fun buildCalendarEvents(tasks: List<Task>): Map<LocalDate, List<CalendarTaskEventUiState>> {
        val zoneId = ZoneId.systemDefault()

        return tasks
            .asSequence()
            .filter { task -> task.status != TaskStatus.ARCHIVED }
            .flatMap { task -> buildTaskEvents(task, zoneId).asSequence() }
            .groupBy { event -> event.timestamp.toLocalDate(zoneId) }
            .mapValues { (_, events) ->
                events.sortedWith(
                    compareBy<CalendarTaskEventUiState> { !it.isAllDay }
                        .thenBy { it.timestamp }
                        .thenBy { it.title.lowercase() }
                )
            }
            .toSortedMap()
    }

    private fun buildTaskEvents(
        task: Task,
        zoneId: ZoneId
    ): List<CalendarTaskEventUiState> {
        val eventItems = buildList {
            task.targetDate?.let { targetDate ->
                add(
                    CalendarTaskEventUiState(
                        instanceId = "${task.id}-target-$targetDate",
                        taskId = task.id,
                        title = task.title,
                        type = task.type,
                        status = task.status,
                        timestamp = targetDate,
                        source = CalendarEventSource.TARGET_DATE,
                        isAllDay = targetDate.isAllDay(zoneId)
                    )
                )
            }

            task.reminders.forEach { reminder ->
                add(
                    CalendarTaskEventUiState(
                        instanceId = reminder.id,
                        taskId = task.id,
                        title = task.title,
                        type = task.type,
                        status = task.status,
                        timestamp = reminder.scheduledAt,
                        source = CalendarEventSource.REMINDER,
                        isAllDay = reminder.scheduledAt.isAllDay(zoneId)
                    )
                )
            }
        }

        return eventItems.distinctBy { event -> event.taskId to event.timestamp }
    }
}

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate {
    return Instant.ofEpochMilli(this)
        .atZone(zoneId)
        .toLocalDate()
}

private fun Long.isAllDay(zoneId: ZoneId): Boolean {
    return Instant.ofEpochMilli(this)
        .atZone(zoneId)
        .toLocalDateTime()
        .toLocalTime() == LocalTime.MIDNIGHT
}

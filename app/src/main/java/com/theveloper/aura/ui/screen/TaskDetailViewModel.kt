package com.theveloper.aura.ui.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.repository.TaskRepository
import com.theveloper.aura.domain.usecase.DeleteTaskUseCase
import com.theveloper.aura.engine.habit.HabitEngine
import com.theveloper.aura.domain.usecase.UpdateTaskStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val updateTaskStatusUseCase: UpdateTaskStatusUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val habitEngine: HabitEngine,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val taskId: String = checkNotNull(savedStateHandle["taskId"])
    private val events = MutableSharedFlow<TaskDetailEvent>(extraBufferCapacity = 1)

    val eventFlow = events.asSharedFlow()

    val uiState: StateFlow<TaskDetailUiState> = taskRepository.getTaskFlow(taskId)
        .map { task ->
            TaskDetailUiState(
                isLoading = false,
                task = task
            )
        }
        .catch { throwable ->
            emit(
                TaskDetailUiState(
                    isLoading = false,
                    task = null,
                    errorMessage = throwable.message ?: "Could not load task details."
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TaskDetailUiState(isLoading = true)
        )

    fun onSignal(signalType: SignalType) {
        viewModelScope.launch {
            habitEngine.logSignal(taskId, signalType)
            when (signalType) {
                SignalType.TASK_COMPLETED -> onCompleteTask()
                SignalType.REMINDER_DISMISSED,
                SignalType.REMINDER_SNOOZED -> Unit
            }
        }
    }

    fun onCompleteTask() {
        viewModelScope.launch {
            runCatching {
                updateTaskStatusUseCase(taskId, TaskStatus.COMPLETED)
            }
        }
    }

    fun onReopenTask() {
        viewModelScope.launch {
            runCatching {
                updateTaskStatusUseCase(taskId, TaskStatus.ACTIVE)
            }
        }
    }

    fun onDeleteTask() {
        viewModelScope.launch {
            runCatching {
                deleteTaskUseCase(taskId)
            }.onSuccess {
                events.tryEmit(TaskDetailEvent.TaskDeleted)
            }
        }
    }

    fun saveTaskEdits(task: Task) {
        viewModelScope.launch {
            runCatching {
                taskRepository.updateTask(
                    task.copy(updatedAt = System.currentTimeMillis())
                )
            }.onSuccess {
                events.tryEmit(TaskDetailEvent.TaskSaved)
            }
        }
    }
}

data class TaskDetailUiState(
    val isLoading: Boolean = false,
    val task: Task? = null,
    val errorMessage: String? = null
)

sealed interface TaskDetailEvent {
    data object TaskDeleted : TaskDetailEvent
    data object TaskSaved : TaskDetailEvent
}

package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.model.Suggestion
import com.theveloper.aura.domain.repository.SuggestionRepository
import com.theveloper.aura.domain.repository.TaskRepository
import com.theveloper.aura.domain.usecase.ApplySuggestionUseCase
import com.theveloper.aura.domain.usecase.RemoveLegacySampleTasksUseCase
import com.theveloper.aura.engine.suggestion.DayRescueEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    taskRepository: TaskRepository,
    private val suggestionRepository: SuggestionRepository,
    private val applySuggestionUseCase: ApplySuggestionUseCase,
    private val dayRescueEngine: DayRescueEngine,
    private val removeLegacySampleTasksUseCase: RemoveLegacySampleTasksUseCase
) : ViewModel() {

    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        taskRepository.getTasksFlow()
            .map { tasks -> tasks.filter { task -> task.status != TaskStatus.ARCHIVED } }
            .catch { throwable ->
                errorMessage.value = throwable.message ?: "No se pudo cargar la home."
                emit(emptyList())
            },
        suggestionRepository.getPendingSuggestions(),
        errorMessage
    ) { tasks, suggestions, error ->
        HomeUiState(
            isLoading = false,
            tasks = tasks,
            suggestions = suggestions,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun applySuggestion(suggestion: Suggestion) {
        viewModelScope.launch {
            applySuggestionUseCase.applySuggestion(suggestion)
        }
    }

    fun rejectSuggestion(suggestion: Suggestion) {
        viewModelScope.launch {
            applySuggestionUseCase.rejectSuggestion(suggestion)
        }
    }

    fun runDayRescue() {
        viewModelScope.launch {
            dayRescueEngine.runDayRescue()
        }
    }

    init {
        viewModelScope.launch {
            runCatching { removeLegacySampleTasksUseCase() }
        }
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val tasks: List<Task> = emptyList(),
    val suggestions: List<Suggestion> = emptyList(),
    val errorMessage: String? = null
)

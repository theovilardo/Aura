package com.theveloper.aura.ui.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.capability.CapabilityRegistry
import com.theveloper.aura.engine.capability.CapabilityRequest
import com.theveloper.aura.engine.capability.CapabilityResponse
import com.theveloper.aura.engine.classifier.TaskClassifier
import com.theveloper.aura.engine.dsl.TaskComponentCatalog
import com.theveloper.aura.engine.dsl.TaskComponentContext
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

@HiltViewModel
class CreateTaskViewModel @Inject constructor(
    private val taskClassifier: TaskClassifier,
    private val capabilityRegistry: CapabilityRegistry,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialMode = TaskCreationMode.fromNavValue(savedStateHandle["mode"])
    private val initialInput = savedStateHandle.get<String>("input").orEmpty()
    private val autoSubmit = savedStateHandle.get<Boolean>("autoSubmit") ?: false

    private val _uiState = MutableStateFlow(
        CreateTaskUiState(
            mode = initialMode,
            input = initialInput,
            manual = ManualTaskDraft.default()
        )
    )
    val uiState: StateFlow<CreateTaskUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CreateTaskEffect>()
    val effects: SharedFlow<CreateTaskEffect> = _effects.asSharedFlow()

    init {
        if (autoSubmit && initialMode == TaskCreationMode.PROMPT && initialInput.isNotBlank()) {
            submitPrompt()
        }
    }

    fun updateInput(value: String) {
        _uiState.update { state ->
            state.copy(
                input = value,
                errorMessage = null
            )
        }
    }

    fun selectMode(mode: TaskCreationMode) {
        _uiState.update { state ->
            state.copy(
                mode = mode,
                errorMessage = null
            )
        }
    }

    fun updateManualTitle(value: String) {
        _uiState.update { state ->
            state.copy(
                manual = state.manual.copy(title = value),
                errorMessage = null
            )
        }
    }

    fun selectManualTaskType(taskType: TaskType) {
        _uiState.update { state ->
            val supportedSelection = state.manual.selectedTemplateIds.filter { templateId ->
                TaskComponentCatalog.find(templateId)?.supportedTaskTypes?.contains(taskType) == true
            }
            val newSelection = supportedSelection.ifEmpty {
                TaskComponentCatalog.recommended(taskType).take(2).map { it.id }
            }
            state.copy(
                manual = state.manual.copy(
                    taskType = taskType,
                    selectedTemplateIds = newSelection
                ),
                errorMessage = null
            )
        }
    }

    fun toggleTemplate(templateId: String) {
        _uiState.update { state ->
            val current = state.manual.selectedTemplateIds
            val updated = if (templateId in current) {
                current - templateId
            } else {
                current + templateId
            }
            state.copy(
                manual = state.manual.copy(selectedTemplateIds = updated),
                errorMessage = null
            )
        }
    }

    fun submit() {
        if (uiState.value.isClassifying || uiState.value.isSaving) {
            return
        }
        when (uiState.value.mode) {
            TaskCreationMode.PROMPT -> submitPrompt()
            TaskCreationMode.MANUAL -> submitManual()
        }
    }

    private fun submitPrompt() {
        val input = uiState.value.input.trim()
        if (input.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Escribí una tarea antes de clasificar.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isClassifying = true,
                    errorMessage = null
                )
            }

            runCatching { taskClassifier.classify(input) }
                .onSuccess { preview ->
                    _uiState.update {
                        it.copy(
                            isClassifying = false,
                            preview = preview
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isClassifying = false,
                            errorMessage = error.message ?: "No pudimos interpretar la tarea."
                        )
                    }
                }
        }
    }

    private fun submitManual() {
        val draft = uiState.value.manual
        if (draft.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Poné un título para la tarea manual.") }
            return
        }
        if (draft.selectedTemplateIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Elegí al menos un componente dinámico.") }
            return
        }

        val now = System.currentTimeMillis()
        val (components, reminders) = TaskComponentCatalog.buildSelection(
            templateIds = draft.selectedTemplateIds,
            now = now,
            context = TaskComponentContext(
                title = draft.title.trim(),
                taskType = draft.taskType
            )
        )
        val targetDateMs = components.firstOrNull { component -> component.type == com.theveloper.aura.domain.model.ComponentType.COUNTDOWN }
            ?.config?.get("targetDate")
            ?.jsonPrimitive
            ?.longOrNull

        _uiState.update {
            it.copy(
                preview = TaskDSLOutput(
                    title = draft.title.trim(),
                    type = draft.taskType,
                    priority = 1,
                    targetDateMs = targetDateMs,
                    components = components,
                    reminders = reminders
                ),
                errorMessage = null
            )
        }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(preview = null) }
    }

    fun confirmPreview() {
        val preview = uiState.value.preview ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = capabilityRegistry.execute(CapabilityRequest.CreateTask(preview))
            when (val response = result.response) {
                is CapabilityResponse.TaskCreated -> {
                    _uiState.value = CreateTaskUiState(
                        mode = uiState.value.mode,
                        manual = ManualTaskDraft.default()
                    )
                    _effects.emit(CreateTaskEffect.TaskCreated(response.taskId))
                }
                CapabilityResponse.Success,
                null -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = result.errorMessage ?: "No pudimos guardar la tarea."
                        )
                    }
                }
            }
        }
    }
}

data class CreateTaskUiState(
    val mode: TaskCreationMode = TaskCreationMode.PROMPT,
    val input: String = "",
    val manual: ManualTaskDraft = ManualTaskDraft.default(),
    val isClassifying: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val preview: TaskDSLOutput? = null
)

data class ManualTaskDraft(
    val title: String,
    val taskType: TaskType,
    val selectedTemplateIds: List<String>
) {
    companion object {
        fun default(): ManualTaskDraft {
            return ManualTaskDraft(
                title = "",
                taskType = TaskType.GENERAL,
                selectedTemplateIds = listOf("notes_brain_dump")
            )
        }
    }
}

sealed interface CreateTaskEffect {
    data class TaskCreated(val taskId: String) : CreateTaskEffect
}

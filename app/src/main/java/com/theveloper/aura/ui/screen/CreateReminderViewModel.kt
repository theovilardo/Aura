package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.engine.capability.CapabilityRegistry
import com.theveloper.aura.engine.capability.CapabilityRequest
import com.theveloper.aura.engine.capability.CapabilityResponse
import com.theveloper.aura.engine.classifier.ReminderClassifier
import com.theveloper.aura.engine.dsl.ReminderDSLOutput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CreateReminderUiState {
    data object Idle : CreateReminderUiState
    data object Loading : CreateReminderUiState
    data class Preview(
        val dsl: ReminderDSLOutput,
        val warnings: List<String> = emptyList()
    ) : CreateReminderUiState
    data class Error(val message: String) : CreateReminderUiState
    data class Created(val reminderId: String) : CreateReminderUiState
}

@HiltViewModel
class CreateReminderViewModel @Inject constructor(
    private val reminderClassifier: ReminderClassifier,
    private val capabilityRegistry: CapabilityRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateReminderUiState>(CreateReminderUiState.Idle)
    val uiState: StateFlow<CreateReminderUiState> = _uiState.asStateFlow()

    private var currentDsl: ReminderDSLOutput? = null

    fun submitPrompt(input: String) {
        if (input.isBlank()) return
        viewModelScope.launch {
            _uiState.value = CreateReminderUiState.Loading
            try {
                val result = reminderClassifier.classify(input)
                currentDsl = result.dsl
                _uiState.value = CreateReminderUiState.Preview(
                    dsl = result.dsl,
                    warnings = result.warnings
                )
            } catch (e: Exception) {
                _uiState.value = CreateReminderUiState.Error(
                    e.message ?: "Failed to classify reminder"
                )
            }
        }
    }

    fun confirmPreview() {
        val dsl = currentDsl ?: return
        viewModelScope.launch {
            try {
                val result = capabilityRegistry.execute(CapabilityRequest.CreateReminder(dsl))
                val response = result.response
                if (response is CapabilityResponse.ReminderCreated) {
                    _uiState.value = CreateReminderUiState.Created(response.reminderId)
                } else {
                    _uiState.value = CreateReminderUiState.Error(
                        result.errorMessage ?: "Creation failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = CreateReminderUiState.Error(
                    e.message ?: "Failed to create reminder"
                )
            }
        }
    }
}

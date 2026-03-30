package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.engine.capability.CapabilityRegistry
import com.theveloper.aura.engine.capability.CapabilityRequest
import com.theveloper.aura.engine.capability.CapabilityResponse
import com.theveloper.aura.engine.classifier.AutomationClassifier
import com.theveloper.aura.engine.dsl.AutomationDSLOutput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CreateAutomationUiState {
    data object Idle : CreateAutomationUiState
    data object Loading : CreateAutomationUiState
    data class Preview(
        val dsl: AutomationDSLOutput,
        val warnings: List<String> = emptyList()
    ) : CreateAutomationUiState
    data class Error(val message: String) : CreateAutomationUiState
    data class Created(val automationId: String) : CreateAutomationUiState
}

@HiltViewModel
class CreateAutomationViewModel @Inject constructor(
    private val automationClassifier: AutomationClassifier,
    private val capabilityRegistry: CapabilityRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateAutomationUiState>(CreateAutomationUiState.Idle)
    val uiState: StateFlow<CreateAutomationUiState> = _uiState.asStateFlow()

    private var currentDsl: AutomationDSLOutput? = null

    fun submitPrompt(input: String) {
        if (input.isBlank()) return
        viewModelScope.launch {
            _uiState.value = CreateAutomationUiState.Loading
            try {
                val result = automationClassifier.classify(input)
                currentDsl = result.dsl
                _uiState.value = CreateAutomationUiState.Preview(
                    dsl = result.dsl,
                    warnings = result.warnings
                )
            } catch (e: Exception) {
                _uiState.value = CreateAutomationUiState.Error(
                    e.message ?: "Failed to classify automation"
                )
            }
        }
    }

    fun confirmPreview() {
        val dsl = currentDsl ?: return
        viewModelScope.launch {
            try {
                val result = capabilityRegistry.execute(CapabilityRequest.CreateAutomation(dsl))
                val response = result.response
                if (response is CapabilityResponse.AutomationCreated) {
                    _uiState.value = CreateAutomationUiState.Created(response.automationId)
                } else {
                    _uiState.value = CreateAutomationUiState.Error(
                        result.errorMessage ?: "Creation failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = CreateAutomationUiState.Error(
                    e.message ?: "Failed to create automation"
                )
            }
        }
    }
}

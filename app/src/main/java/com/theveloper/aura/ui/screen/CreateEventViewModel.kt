package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.engine.capability.CapabilityRegistry
import com.theveloper.aura.engine.capability.CapabilityRequest
import com.theveloper.aura.engine.capability.CapabilityResponse
import com.theveloper.aura.engine.classifier.EventClassifier
import com.theveloper.aura.engine.dsl.EventDSLOutput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CreateEventUiState {
    data object Idle : CreateEventUiState
    data object Loading : CreateEventUiState
    data class Preview(
        val dsl: EventDSLOutput,
        val warnings: List<String> = emptyList()
    ) : CreateEventUiState
    data class Error(val message: String) : CreateEventUiState
    data class Created(val eventId: String) : CreateEventUiState
}

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    private val eventClassifier: EventClassifier,
    private val capabilityRegistry: CapabilityRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateEventUiState>(CreateEventUiState.Idle)
    val uiState: StateFlow<CreateEventUiState> = _uiState.asStateFlow()

    private var currentDsl: EventDSLOutput? = null

    fun submitPrompt(input: String) {
        if (input.isBlank()) return
        viewModelScope.launch {
            _uiState.value = CreateEventUiState.Loading
            try {
                val result = eventClassifier.classify(input)
                currentDsl = result.dsl
                _uiState.value = CreateEventUiState.Preview(
                    dsl = result.dsl,
                    warnings = result.warnings
                )
            } catch (e: Exception) {
                _uiState.value = CreateEventUiState.Error(
                    e.message ?: "Failed to classify event"
                )
            }
        }
    }

    fun confirmPreview() {
        val dsl = currentDsl ?: return
        viewModelScope.launch {
            try {
                val result = capabilityRegistry.execute(CapabilityRequest.CreateEvent(dsl))
                val response = result.response
                if (response is CapabilityResponse.EventCreated) {
                    _uiState.value = CreateEventUiState.Created(response.eventId)
                } else {
                    _uiState.value = CreateEventUiState.Error(
                        result.errorMessage ?: "Creation failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = CreateEventUiState.Error(
                    e.message ?: "Failed to create event"
                )
            }
        }
    }
}

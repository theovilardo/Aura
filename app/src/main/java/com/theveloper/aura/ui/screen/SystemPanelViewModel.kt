package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.domain.model.AuraAutomation
import com.theveloper.aura.domain.model.AuraEvent
import com.theveloper.aura.domain.model.AuraReminder
import com.theveloper.aura.domain.model.AutomationStatus
import com.theveloper.aura.domain.repository.AuraEventRepository
import com.theveloper.aura.domain.repository.AuraReminderRepository
import com.theveloper.aura.domain.repository.AutomationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SystemPanelUiState(
    val automations: List<AuraAutomation> = emptyList(),
    val reminders: List<AuraReminder> = emptyList(),
    val events: List<AuraEvent> = emptyList()
)

@HiltViewModel
class SystemPanelViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val reminderRepository: AuraReminderRepository,
    private val eventRepository: AuraEventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemPanelUiState())
    val uiState: StateFlow<SystemPanelUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                automationRepository.observeAll(),
                reminderRepository.observeAll(),
                eventRepository.observeAll()
            ) { automations, reminders, events ->
                SystemPanelUiState(
                    automations = automations,
                    reminders = reminders,
                    events = events
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun toggleAutomation(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val newStatus = if (enabled) AutomationStatus.ACTIVE else AutomationStatus.PAUSED
            automationRepository.updateStatus(id, newStatus)
        }
    }
}

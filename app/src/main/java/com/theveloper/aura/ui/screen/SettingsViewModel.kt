package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.BuildConfig
import com.theveloper.aura.data.repository.AppSettingsRepository
import com.theveloper.aura.engine.classifier.AiExecutionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val syncEnabled: Boolean = false,
    val aiExecutionMode: AiExecutionMode = AiExecutionMode.AUTO,
    val groqConfigured: Boolean = BuildConfig.GROQ_API_KEY.isNotBlank(),
    val developerMockHabitDataEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appSettingsRepository.settingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        syncEnabled = settings.syncEnabled,
                        aiExecutionMode = settings.aiExecutionMode,
                        developerMockHabitDataEnabled = settings.developerMockHabitDataEnabled
                    )
                }
            }
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setSyncEnabled(enabled)
        }
    }

    fun setAiExecutionMode(mode: AiExecutionMode) {
        viewModelScope.launch {
            appSettingsRepository.setAiExecutionMode(mode)
        }
    }

    fun setDeveloperMockHabitDataEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setDeveloperMockHabitDataEnabled(enabled)
        }
    }
}

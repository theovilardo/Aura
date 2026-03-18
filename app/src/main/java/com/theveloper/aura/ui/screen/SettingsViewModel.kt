package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.data.repository.AppSettingsRepository
import com.theveloper.aura.engine.classifier.AiExecutionMode
import com.theveloper.aura.engine.llm.LLMServiceFactory
import com.theveloper.aura.engine.llm.LLMTier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val syncEnabled: Boolean = false,
    val ecosystemEnabled: Boolean = false,
    val aiExecutionMode: AiExecutionMode = AiExecutionMode.AUTO,
    val groqConfigured: Boolean = false,
    val developerMockHabitDataEnabled: Boolean = false,
    val intelligenceStatus: String = "Rules"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val llmServiceFactory: LLMServiceFactory
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appSettingsRepository.settingsFlow.collect { settings ->
                val runtimeStatus = runCatching {
                    llmServiceFactory.getRuntimeStatus(settings.aiExecutionMode)
                }.getOrNull()
                val intelligenceStatus = runtimeStatus
                    ?.activePrimaryTier
                    ?.settingsLabel()
                    ?: LLMTier.RULES_ONLY.settingsLabel()
                _uiState.update {
                    it.copy(
                        syncEnabled = settings.syncEnabled,
                        ecosystemEnabled = settings.ecosystemEnabled,
                        aiExecutionMode = settings.aiExecutionMode,
                        groqConfigured = runtimeStatus?.groqConfigured ?: false,
                        developerMockHabitDataEnabled = settings.developerMockHabitDataEnabled,
                        intelligenceStatus = intelligenceStatus
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

    private fun LLMTier.settingsLabel(): String {
        return when (this) {
            LLMTier.GEMINI_NANO -> "Gemini Nano"
            LLMTier.GEMMA_3N_E2B -> "Gemma 3n"
            LLMTier.GEMMA_3_1B -> "Gemma 1B"
            LLMTier.QWEN_2_5_1_5B -> "Qwen 1.5B"
            LLMTier.QWEN_3_0_6B -> "Qwen 0.6B"
            LLMTier.GROQ_API -> "Groq"
            LLMTier.RULES_ONLY -> "Rules"
        }
    }
}

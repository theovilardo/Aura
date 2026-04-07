package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.data.repository.AppSettingsRepository
import com.theveloper.aura.engine.classifier.AiExecutionMode
import com.theveloper.aura.engine.llm.DownloadState
import com.theveloper.aura.engine.llm.LLMRuntimeStatus
import com.theveloper.aura.engine.llm.LLMServiceFactory
import com.theveloper.aura.engine.llm.LLMTier
import com.theveloper.aura.engine.llm.LocalModelSlot
import com.theveloper.aura.engine.llm.ModelCatalog
import com.theveloper.aura.engine.llm.ModelDownloadManager
import com.theveloper.aura.engine.llm.ModelSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IntelligenceModelUiState(
    val spec: ModelSpec,
    val isDownloaded: Boolean = false,
    val sizeOnDisk: Long = 0L,
    val downloadState: DownloadState = DownloadState.Idle,
    val isActive: Boolean = false,
    val isSupported: Boolean = true,
    val isRuntimeCompatible: Boolean = true,
    val hasCredentials: Boolean = true,
    val isSelected: Boolean = false
)

data class IntelligenceSettingsUiState(
    val isLoading: Boolean = true,
    val executionMode: AiExecutionMode = AiExecutionMode.AUTO,
    val activeTier: LLMTier = LLMTier.RULES_ONLY,
    val recommendedTier: LLMTier = LLMTier.RULES_ONLY,
    val activeReason: String = "",
    val recommendedReason: String = "",
    val groqConfigured: Boolean = false,
    val groqTokenConfigured: Boolean = false,
    val huggingFaceTokenConfigured: Boolean = false,
    val huggingFaceTokenInput: String = "",
    val groqTokenInput: String = "",
    val supportsAdvancedTier: Boolean = false,
    val selectedPrimaryModelId: String = ModelCatalog.defaultPrimary.id,
    val selectedAdvancedModelId: String = ModelCatalog.defaultAdvanced.id,
    val primaryModels: List<IntelligenceModelUiState> = ModelCatalog.primaryModels.map { IntelligenceModelUiState(spec = it) },
    val advancedModels: List<IntelligenceModelUiState> = ModelCatalog.advancedModels.map { IntelligenceModelUiState(spec = it) }
)

@HiltViewModel
class IntelligenceSettingsViewModel @Inject constructor(
    private val llmServiceFactory: LLMServiceFactory,
    private val appSettingsRepository: AppSettingsRepository,
    private val modelDownloadManager: ModelDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntelligenceSettingsUiState())
    val uiState: StateFlow<IntelligenceSettingsUiState> = _uiState.asStateFlow()

    private val downloadJobs = linkedMapOf<String, Job>()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val runtime = llmServiceFactory.getRuntimeStatus()
            val settings = appSettingsRepository.getSnapshot()
            val tokenConfigured = settings.huggingFaceAccessToken.isNotBlank()
            val groqTokenConfigured = settings.groqAccessToken.isNotBlank()
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    executionMode = settings.aiExecutionMode,
                    activeTier = runtime.activePrimaryTier,
                    recommendedTier = runtime.detection.primaryTier,
                    activeReason = runtime.activeReason,
                    recommendedReason = runtime.detection.reasonForTier,
                    groqConfigured = runtime.groqConfigured,
                    groqTokenConfigured = groqTokenConfigured,
                    huggingFaceTokenConfigured = tokenConfigured,
                    supportsAdvancedTier = runtime.detection.supportsAdvancedTier,
                    selectedPrimaryModelId = settings.preferredPrimaryModelId,
                    selectedAdvancedModelId = settings.preferredAdvancedModelId,
                    primaryModels = ModelCatalog.primaryModels.map { spec ->
                        state.modelFor(spec).copyFrom(
                            runtime = runtime,
                            spec = spec,
                            huggingFaceTokenConfigured = tokenConfigured,
                            isSelected = settings.preferredPrimaryModelId == spec.id
                        )
                    },
                    advancedModels = ModelCatalog.advancedModels.map { spec ->
                        state.modelFor(spec).copyFrom(
                            runtime = runtime,
                            spec = spec,
                            huggingFaceTokenConfigured = tokenConfigured,
                            isSelected = settings.preferredAdvancedModelId == spec.id
                        )
                    }
                )
            }
        }
    }

    fun updateHuggingFaceTokenInput(value: String) {
        _uiState.update { it.copy(huggingFaceTokenInput = value) }
    }

    fun saveHuggingFaceToken() {
        viewModelScope.launch {
            appSettingsRepository.setHuggingFaceAccessToken(uiState.value.huggingFaceTokenInput)
            _uiState.update { it.copy(huggingFaceTokenInput = "") }
            refresh()
        }
    }

    fun clearHuggingFaceToken() {
        viewModelScope.launch {
            appSettingsRepository.setHuggingFaceAccessToken("")
            _uiState.update { it.copy(huggingFaceTokenInput = "") }
            refresh()
        }
    }

    fun updateGroqTokenInput(value: String) {
        _uiState.update { it.copy(groqTokenInput = value) }
    }

    fun saveGroqToken() {
        viewModelScope.launch {
            appSettingsRepository.setGroqAccessToken(uiState.value.groqTokenInput)
            _uiState.update { it.copy(groqTokenInput = "") }
            refresh()
        }
    }

    fun clearGroqToken() {
        viewModelScope.launch {
            appSettingsRepository.setGroqAccessToken("")
            _uiState.update { it.copy(groqTokenInput = "") }
            refresh()
        }
    }

    fun setExecutionMode(mode: AiExecutionMode) {
        viewModelScope.launch {
            appSettingsRepository.setAiExecutionMode(mode)
            refresh()
        }
    }

    fun selectPrimaryModel(spec: ModelSpec) {
        viewModelScope.launch {
            appSettingsRepository.setPreferredPrimaryModel(spec.id)
            refresh()
        }
    }

    fun selectAdvancedModel(spec: ModelSpec) {
        viewModelScope.launch {
            appSettingsRepository.setPreferredAdvancedModel(spec.id)
            refresh()
        }
    }

    fun downloadModel(spec: ModelSpec, wifiOnly: Boolean = true) {
        startDownload(spec = spec, wifiOnly = wifiOnly)
    }

    fun cancelDownload(spec: ModelSpec) {
        modelDownloadManager.cancelDownload(spec)
        downloadJobs.remove(spec.id)?.cancel()
        updateModel(spec) { copy(downloadState = DownloadState.Idle) }
    }

    fun deleteModel(spec: ModelSpec) {
        modelDownloadManager.deleteModel(spec)
        refresh()
    }

    private fun startDownload(
        spec: ModelSpec,
        wifiOnly: Boolean
    ) {
        downloadJobs.remove(spec.id)?.cancel()
        val job = viewModelScope.launch {
            modelDownloadManager.downloadModel(spec, wifiOnly).collect { downloadState ->
                updateModel(spec) {
                    copy(downloadState = downloadState)
                }
                if (downloadState is DownloadState.Complete) {
                    refresh()
                }
            }
        }
        downloadJobs[spec.id] = job
    }

    private fun updateModel(
        spec: ModelSpec,
        transform: IntelligenceModelUiState.() -> IntelligenceModelUiState
    ) {
        _uiState.update { state ->
            state.copy(
                primaryModels = state.primaryModels.map { current ->
                    if (current.spec.id == spec.id) current.transform() else current
                },
                advancedModels = state.advancedModels.map { current ->
                    if (current.spec.id == spec.id) current.transform() else current
                }
            )
        }
    }

    private fun IntelligenceModelUiState.copyFrom(
        runtime: LLMRuntimeStatus,
        spec: ModelSpec,
        huggingFaceTokenConfigured: Boolean,
        isSelected: Boolean
    ): IntelligenceModelUiState {
        val currentDownloadState = when (downloadState) {
            is DownloadState.Downloading,
            DownloadState.Processing,
            DownloadState.WaitingForWifi -> downloadState

            else -> DownloadState.Idle
        }
        return copy(
            isDownloaded = modelDownloadManager.isModelDownloaded(spec),
            sizeOnDisk = modelDownloadManager.getModelSizeOnDisk(spec),
            downloadState = currentDownloadState,
            isActive = runtime.activePrimaryTier == spec.tier || runtime.activeAdvancedTier == spec.tier,
            isRuntimeCompatible = spec.isRuntimeCompatible,
            hasCredentials = !spec.requiresAuthentication || huggingFaceTokenConfigured,
            isSelected = isSelected,
            isSupported = when (spec.slot) {
                LocalModelSlot.PRIMARY -> runtime.detection.primaryTier != LLMTier.RULES_ONLY
                LocalModelSlot.ADVANCED -> runtime.detection.supportsAdvancedTier
            }
        )
    }

    private fun IntelligenceSettingsUiState.modelFor(spec: ModelSpec): IntelligenceModelUiState {
        return (primaryModels + advancedModels).firstOrNull { it.spec.id == spec.id }
            ?: IntelligenceModelUiState(spec = spec)
    }
}

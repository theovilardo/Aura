package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.data.repository.AppSettingsRepository
import com.theveloper.aura.engine.llm.DownloadState
import com.theveloper.aura.engine.llm.LLMRuntimeStatus
import com.theveloper.aura.engine.llm.LLMServiceFactory
import com.theveloper.aura.engine.llm.LLMTier
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
    val hasCredentials: Boolean = true
)

data class IntelligenceSettingsUiState(
    val isLoading: Boolean = true,
    val activeTier: LLMTier = LLMTier.RULES_ONLY,
    val recommendedTier: LLMTier = LLMTier.RULES_ONLY,
    val activeReason: String = "",
    val recommendedReason: String = "",
    val executionModeLabel: String = "Auto",
    val groqConfigured: Boolean = false,
    val huggingFaceTokenConfigured: Boolean = false,
    val huggingFaceTokenInput: String = "",
    val supportsAdvancedTier: Boolean = false,
    val primaryModel: IntelligenceModelUiState = IntelligenceModelUiState(spec = ModelCatalog.gemma3_1B),
    val advancedModel: IntelligenceModelUiState = IntelligenceModelUiState(spec = ModelCatalog.gemma3nE2B)
)

@HiltViewModel
class IntelligenceSettingsViewModel @Inject constructor(
    private val llmServiceFactory: LLMServiceFactory,
    private val appSettingsRepository: AppSettingsRepository,
    private val modelDownloadManager: ModelDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntelligenceSettingsUiState())
    val uiState: StateFlow<IntelligenceSettingsUiState> = _uiState.asStateFlow()

    private var primaryDownloadJob: Job? = null
    private var advancedDownloadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val runtime = llmServiceFactory.getRuntimeStatus()
            val settings = appSettingsRepository.getSnapshot()
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    activeTier = runtime.activePrimaryTier,
                    recommendedTier = runtime.detection.primaryTier,
                    activeReason = runtime.activeReason,
                    recommendedReason = runtime.detection.reasonForTier,
                    executionModeLabel = runtime.executionMode.title,
                    groqConfigured = runtime.groqConfigured,
                    huggingFaceTokenConfigured = settings.huggingFaceAccessToken.isNotBlank(),
                    supportsAdvancedTier = runtime.detection.supportsAdvancedTier,
                    primaryModel = state.primaryModel.copyFrom(
                        runtime = runtime,
                        spec = ModelCatalog.gemma3_1B,
                        huggingFaceTokenConfigured = settings.huggingFaceAccessToken.isNotBlank()
                    ),
                    advancedModel = state.advancedModel.copyFrom(
                        runtime = runtime,
                        spec = ModelCatalog.gemma3nE2B,
                        huggingFaceTokenConfigured = settings.huggingFaceAccessToken.isNotBlank()
                    )
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

    fun downloadPrimaryModel(wifiOnly: Boolean = true) {
        startDownload(
            spec = ModelCatalog.gemma3_1B,
            wifiOnly = wifiOnly,
            currentJob = primaryDownloadJob,
            assignJob = { primaryDownloadJob = it },
            update = { state -> copy(primaryModel = state) }
        )
    }

    fun downloadAdvancedModel(wifiOnly: Boolean = true) {
        startDownload(
            spec = ModelCatalog.gemma3nE2B,
            wifiOnly = wifiOnly,
            currentJob = advancedDownloadJob,
            assignJob = { advancedDownloadJob = it },
            update = { state -> copy(advancedModel = state) }
        )
    }

    fun cancelPrimaryDownload() {
        modelDownloadManager.cancelDownload(ModelCatalog.gemma3_1B)
        primaryDownloadJob?.cancel()
        _uiState.update { it.copy(primaryModel = it.primaryModel.copy(downloadState = DownloadState.Idle)) }
    }

    fun cancelAdvancedDownload() {
        modelDownloadManager.cancelDownload(ModelCatalog.gemma3nE2B)
        advancedDownloadJob?.cancel()
        _uiState.update { it.copy(advancedModel = it.advancedModel.copy(downloadState = DownloadState.Idle)) }
    }

    fun deletePrimaryModel() {
        modelDownloadManager.deleteModel(ModelCatalog.gemma3_1B)
        refresh()
    }

    fun deleteAdvancedModel() {
        modelDownloadManager.deleteModel(ModelCatalog.gemma3nE2B)
        refresh()
    }

    private fun startDownload(
        spec: ModelSpec,
        wifiOnly: Boolean,
        currentJob: Job?,
        assignJob: (Job) -> Unit,
        update: IntelligenceSettingsUiState.(IntelligenceModelUiState) -> IntelligenceSettingsUiState
    ) {
        currentJob?.cancel()
        val job = viewModelScope.launch {
            modelDownloadManager.downloadModel(spec, wifiOnly).collect { downloadState ->
                _uiState.update { state ->
                    val current = state.modelFor(spec)
                    state.update(
                        current.copy(
                            downloadState = downloadState
                        )
                    )
                }
                if (downloadState is DownloadState.Complete) {
                    refresh()
                }
            }
        }
        assignJob(job)
    }

    private fun IntelligenceModelUiState.copyFrom(
        runtime: LLMRuntimeStatus,
        spec: ModelSpec,
        huggingFaceTokenConfigured: Boolean
    ): IntelligenceModelUiState {
        val downloadState = when (downloadState) {
            is DownloadState.Downloading,
            DownloadState.Processing,
            DownloadState.WaitingForWifi -> downloadState

            else -> DownloadState.Idle
        }
        return copy(
            isDownloaded = modelDownloadManager.isModelDownloaded(spec),
            sizeOnDisk = modelDownloadManager.getModelSizeOnDisk(spec),
            downloadState = downloadState,
            isActive = runtime.activePrimaryTier == spec.tier || runtime.activeAdvancedTier == spec.tier,
            hasCredentials = !spec.requiresAuthentication || huggingFaceTokenConfigured,
            isSupported = when (spec.tier) {
                LLMTier.GEMMA_3_1B -> runtime.detection.primaryTier != LLMTier.RULES_ONLY
                LLMTier.GEMMA_3N_E2B -> runtime.detection.supportsAdvancedTier
                else -> true
            }
        )
    }

    private fun IntelligenceSettingsUiState.modelFor(spec: ModelSpec): IntelligenceModelUiState {
        return when (spec.id) {
            ModelCatalog.gemma3_1B.id -> primaryModel
            else -> advancedModel
        }
    }
}

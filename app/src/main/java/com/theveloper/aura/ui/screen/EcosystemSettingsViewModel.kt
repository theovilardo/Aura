package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.data.db.PairedDeviceDao
import com.theveloper.aura.data.db.PairedDeviceEntity
import com.theveloper.aura.data.repository.AppSettingsRepository
import com.theveloper.aura.engine.ecosystem.AuraWebSocketClient
import com.theveloper.aura.engine.ecosystem.ConnectedDevice
import com.theveloper.aura.engine.ecosystem.DeviceRegistry
import com.theveloper.aura.engine.ecosystem.PairingManager
import com.theveloper.aura.engine.ecosystem.PairingResult
import com.theveloper.aura.engine.ecosystem.WsConnectionState
import com.theveloper.aura.engine.provider.ProviderAdapter
import com.theveloper.aura.engine.provider.ProviderRegistry
import com.theveloper.aura.engine.router.FallbackNode
import com.theveloper.aura.engine.router.FallbackTreeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EcosystemUiState(
    val ecosystemEnabled: Boolean = false,
    val connectedDevices: Map<String, ConnectedDevice> = emptyMap(),
    val pairedDevices: List<PairedDeviceEntity> = emptyList(),
    val wsConnectionState: WsConnectionState = WsConnectionState.DISCONNECTED,
    val providers: List<ProviderAdapter> = emptyList(),
    val fallbackTree: List<FallbackNode> = emptyList(),
    val pairingInProgress: Boolean = false,
    val pairingResult: PairingResult? = null,
    val pairingUrl: String = ""
)

@HiltViewModel
class EcosystemSettingsViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val deviceRegistry: DeviceRegistry,
    private val webSocketClient: AuraWebSocketClient,
    private val pairingManager: PairingManager,
    private val providerRegistry: ProviderRegistry,
    private val fallbackTreeStore: FallbackTreeStore,
    private val pairedDeviceDao: PairedDeviceDao
) : ViewModel() {

    private val _pairingState = MutableStateFlow(PairingUiState())
    private val _fallbackTree = MutableStateFlow<List<FallbackNode>>(emptyList())

    val uiState: StateFlow<EcosystemUiState> = combine(
        appSettingsRepository.settingsFlow,
        deviceRegistry.devices,
        webSocketClient.connectionState,
        providerRegistry.providers,
        combine(_fallbackTree, _pairingState) { t, p -> t to p }
    ) { settings, devices, wsState, providers, (tree, pairing) ->
        EcosystemUiState(
            ecosystemEnabled = settings.ecosystemEnabled,
            connectedDevices = devices,
            wsConnectionState = wsState,
            providers = providers.values.toList(),
            fallbackTree = tree,
            pairingInProgress = pairing.inProgress,
            pairingResult = pairing.result,
            pairingUrl = pairing.url
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EcosystemUiState())

    init {
        viewModelScope.launch {
            _fallbackTree.value = fallbackTreeStore.getTree()
        }
    }

    fun setEcosystemEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setEcosystemEnabled(enabled)
        }
    }

    fun updatePairingUrl(url: String) {
        _pairingState.value = _pairingState.value.copy(url = url)
    }

    fun startPairing() {
        val url = _pairingState.value.url
        if (url.isBlank()) return

        viewModelScope.launch {
            _pairingState.value = _pairingState.value.copy(inProgress = true, result = null)
            val result = pairingManager.initiatePairing(url)
            _pairingState.value = _pairingState.value.copy(inProgress = false, result = result)

            if (result.success) {
                pairedDeviceDao.insert(
                    PairedDeviceEntity(
                        id = result.deviceId ?: "",
                        name = result.deviceName ?: "Desktop",
                        platform = "MACOS",
                        connectionUrl = url,
                        lastSeenAt = System.currentTimeMillis(),
                        pairedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun disconnectDevice(deviceId: String) {
        webSocketClient.disconnect()
        deviceRegistry.unregisterDevice(deviceId)
    }

    fun reorderFallbackTree(nodes: List<FallbackNode>) {
        val reindexed = nodes.mapIndexed { idx, node -> node.copy(priority = idx) }
        _fallbackTree.value = reindexed
        viewModelScope.launch { fallbackTreeStore.saveTree(reindexed) }
    }

    fun toggleFallbackNode(providerId: String, enabled: Boolean) {
        val updated = _fallbackTree.value.map { node ->
            if (node.providerId == providerId) node.copy(isEnabled = enabled) else node
        }
        _fallbackTree.value = updated
        viewModelScope.launch { fallbackTreeStore.saveTree(updated) }
    }

    private data class PairingUiState(
        val url: String = "",
        val inProgress: Boolean = false,
        val result: PairingResult? = null
    )
}

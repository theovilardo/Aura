package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.data.db.PairedDeviceDao
import com.theveloper.aura.data.db.PairedDeviceEntity
import com.theveloper.aura.data.repository.AppSettingsSnapshot
import com.theveloper.aura.data.repository.AppSettingsRepository
import com.theveloper.aura.engine.ecosystem.AuraWebSocketClient
import com.theveloper.aura.engine.ecosystem.ConnectedDevice
import com.theveloper.aura.engine.ecosystem.DeviceRegistry
import com.theveloper.aura.engine.ecosystem.PairingInvitation
import com.theveloper.aura.engine.ecosystem.PairingManager
import com.theveloper.aura.engine.ecosystem.PairingResult
import com.theveloper.aura.engine.ecosystem.PairingSessionState
import com.theveloper.aura.engine.ecosystem.WsConnectionState
import com.theveloper.aura.engine.provider.ProviderAdapter
import com.theveloper.aura.engine.provider.ProviderRegistry
import com.theveloper.aura.engine.router.FallbackNode
import com.theveloper.aura.engine.router.FallbackTreeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class EcosystemCoreInputs(
    val settings: AppSettingsSnapshot,
    val devices: Map<String, ConnectedDevice>,
    val pairedDevices: List<PairedDeviceEntity>,
    val wsState: WsConnectionState
)

private data class EcosystemUiInputs(
    val providers: Map<String, ProviderAdapter>,
    val fallbackTree: List<FallbackNode>,
    val pairingState: PairingSessionState,
    val pairingPin: String
)

data class EcosystemUiState(
    val ecosystemEnabled: Boolean = false,
    val connectedDevices: Map<String, ConnectedDevice> = emptyMap(),
    val pairedDevices: List<PairedDeviceEntity> = emptyList(),
    val wsConnectionState: WsConnectionState = WsConnectionState.DISCONNECTED,
    val providers: List<ProviderAdapter> = emptyList(),
    val fallbackTree: List<FallbackNode> = emptyList(),
    val pairingInProgress: Boolean = false,
    val pairingResult: PairingResult? = null,
    val pendingInvitation: PairingInvitation? = null,
    val activeInvitation: PairingInvitation? = null,
    val awaitingPin: Boolean = false,
    val pairingPin: String = ""
)

@HiltViewModel
class EcosystemSettingsViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val deviceRegistry: DeviceRegistry,
    private val webSocketClient: AuraWebSocketClient,
    private val pairingManager: PairingManager,
    private val providerRegistry: ProviderRegistry,
    private val fallbackTreeStore: FallbackTreeStore,
    pairedDeviceDao: PairedDeviceDao
) : ViewModel() {

    private val _fallbackTree = MutableStateFlow<List<FallbackNode>>(emptyList())
    private val _pairingPin = MutableStateFlow("")

    val uiState: StateFlow<EcosystemUiState> = combine(
        combine(
            appSettingsRepository.settingsFlow,
            deviceRegistry.devices,
            pairedDeviceDao.observeAll(),
            webSocketClient.connectionState
        ) { settings, devices, pairedDevices, wsState ->
            EcosystemCoreInputs(
                settings = settings,
                devices = devices,
                pairedDevices = pairedDevices,
                wsState = wsState
            )
        },
        combine(
            providerRegistry.providers,
            _fallbackTree,
            pairingManager.sessionState,
            _pairingPin
        ) { providers, tree, pairingState, pairingPin ->
            EcosystemUiInputs(
                providers = providers,
                fallbackTree = tree,
                pairingState = pairingState,
                pairingPin = pairingPin
            )
        }
    ) { core, ui ->
        EcosystemUiState(
            ecosystemEnabled = core.settings.ecosystemEnabled,
            connectedDevices = core.devices,
            pairedDevices = core.pairedDevices,
            wsConnectionState = core.wsState,
            providers = ui.providers.values.toList(),
            fallbackTree = ui.fallbackTree,
            pairingInProgress = ui.pairingState.inProgress,
            pairingResult = ui.pairingState.result,
            pendingInvitation = ui.pairingState.pendingInvitation,
            activeInvitation = ui.pairingState.activeInvitation,
            awaitingPin = ui.pairingState.awaitingPin,
            pairingPin = ui.pairingPin
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

    fun acceptInvitation() {
        viewModelScope.launch {
            pairingManager.acceptInvitation()
        }
    }

    fun dismissInvitation() {
        pairingManager.dismissInvitation()
    }

    fun updatePairingPin(pin: String) {
        _pairingPin.value = pin.filter(Char::isDigit).take(MAX_PIN_LENGTH)
    }

    fun confirmPairing() {
        val pin = _pairingPin.value
        if (pin.isBlank()) return

        viewModelScope.launch {
            pairingManager.confirmPairing(pin)
            _pairingPin.value = ""
        }
    }

    fun cancelActivePairing() {
        _pairingPin.value = ""
        pairingManager.cancelActivePairing()
    }

    fun clearPairingResult() {
        pairingManager.clearResult()
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

    private companion object {
        const val MAX_PIN_LENGTH = 6
    }
}

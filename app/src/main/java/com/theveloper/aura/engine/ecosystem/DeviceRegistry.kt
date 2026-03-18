package com.theveloper.aura.engine.ecosystem

import com.theveloper.aura.protocol.DesktopAction
import com.theveloper.aura.protocol.DeviceCapabilityReport
import com.theveloper.aura.protocol.OllamaModelInfo
import com.theveloper.aura.protocol.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectedDevice(
    val id: String,
    val name: String,
    val platform: Platform,
    val supportedActions: List<DesktopAction>,
    val ollamaModels: List<OllamaModelInfo>,
    val connectionUrl: String,
    val lastHeartbeatAt: Long = System.currentTimeMillis(),
    val connectionState: ConnectionState = ConnectionState.CONNECTED
)

enum class ConnectionState {
    CONNECTED,
    DISCONNECTED,
    PAIRING,
    RECONNECTING
}

@Singleton
class DeviceRegistry @Inject constructor() {

    private val _devices = MutableStateFlow<Map<String, ConnectedDevice>>(emptyMap())
    val devices: StateFlow<Map<String, ConnectedDevice>> = _devices.asStateFlow()

    fun hasDesktopAvailable(): Boolean =
        _devices.value.values.any {
            it.platform != Platform.ANDROID && it.connectionState == ConnectionState.CONNECTED
        }

    fun connectedDesktops(): List<ConnectedDevice> =
        _devices.value.values.filter {
            it.platform != Platform.ANDROID && it.connectionState == ConnectionState.CONNECTED
        }

    fun registerDevice(device: ConnectedDevice) {
        _devices.update { it + (device.id to device) }
    }

    fun unregisterDevice(deviceId: String) {
        _devices.update { it - deviceId }
    }

    fun updateFromCapabilityReport(report: DeviceCapabilityReport, connectionUrl: String) {
        val device = ConnectedDevice(
            id = report.deviceId,
            name = report.deviceName,
            platform = report.platform,
            supportedActions = report.supportedActions,
            ollamaModels = report.ollamaModels,
            connectionUrl = connectionUrl,
            connectionState = ConnectionState.CONNECTED
        )
        _devices.update { it + (device.id to device) }
    }

    fun updateHeartbeat(deviceId: String) {
        _devices.update { map ->
            val device = map[deviceId] ?: return@update map
            map + (deviceId to device.copy(lastHeartbeatAt = System.currentTimeMillis()))
        }
    }

    fun setConnectionState(deviceId: String, state: ConnectionState) {
        _devices.update { map ->
            val device = map[deviceId] ?: return@update map
            map + (deviceId to device.copy(connectionState = state))
        }
    }

    fun getDevice(deviceId: String): ConnectedDevice? = _devices.value[deviceId]
}

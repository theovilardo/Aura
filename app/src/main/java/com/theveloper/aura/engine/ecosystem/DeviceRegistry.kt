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
        _devices.update { devices ->
            val existing = devices[report.deviceId]
            val device = ConnectedDevice(
                id = report.deviceId,
                name = report.deviceName,
                platform = report.platform,
                supportedActions = report.supportedActions,
                ollamaModels = report.ollamaModels,
                connectionUrl = connectionUrl,
                lastHeartbeatAt = existing?.lastHeartbeatAt ?: System.currentTimeMillis(),
                connectionState = ConnectionState.CONNECTED
            )
            devices + (device.id to device)
        }
    }

    fun updateHeartbeat(deviceId: String, connectionUrl: String? = null) {
        _devices.update { map ->
            val resolvedDeviceId = when {
                deviceId.isNotBlank() -> deviceId
                connectionUrl != null -> map.values.firstOrNull { it.connectionUrl == connectionUrl }?.id
                else -> null
            } ?: return@update map

            val device = map[resolvedDeviceId] ?: return@update map
            map + (resolvedDeviceId to device.copy(
                lastHeartbeatAt = System.currentTimeMillis(),
                connectionState = ConnectionState.CONNECTED
            ))
        }
    }

    fun setConnectionState(deviceId: String, state: ConnectionState) {
        _devices.update { map ->
            val device = map[deviceId] ?: return@update map
            map + (deviceId to device.copy(connectionState = state))
        }
    }

    fun markConnectionState(connectionUrl: String, state: ConnectionState) {
        _devices.update { map ->
            map.mapValues { (_, device) ->
                if (device.connectionUrl == connectionUrl) {
                    device.copy(connectionState = state)
                } else {
                    device
                }
            }
        }
    }

    fun getDevice(deviceId: String): ConnectedDevice? = _devices.value[deviceId]
}

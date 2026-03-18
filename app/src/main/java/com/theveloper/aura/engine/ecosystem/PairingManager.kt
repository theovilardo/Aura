package com.theveloper.aura.engine.ecosystem

import com.theveloper.aura.protocol.EcosystemMessage
import com.theveloper.aura.protocol.MessageType
import com.theveloper.aura.protocol.PairingAck
import com.theveloper.aura.protocol.PairingRequest
import com.theveloper.aura.protocol.Platform
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PairingResult(
    val success: Boolean,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val error: String? = null
)

/**
 * Manages the pairing handshake between this Android device and a desktop daemon.
 *
 * Pairing flow:
 * 1. User enters the desktop's connection URL (IP:port or relay URL)
 * 2. Android sends a [PairingRequest] with its device ID and public key
 * 3. Desktop displays a 6-digit pairing code
 * 4. User enters the code on Android
 * 5. Both sides derive a shared secret and the connection is established
 */
@Singleton
class PairingManager @Inject constructor(
    private val webSocketClient: AuraWebSocketClient,
    private val deviceRegistry: DeviceRegistry
) {

    private val localDeviceId: String = UUID.randomUUID().toString()
    private val localDeviceName: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    /**
     * Initiates pairing with a desktop at the given URL.
     * Connects the WebSocket and sends a pairing request.
     */
    suspend fun initiatePairing(desktopUrl: String): PairingResult {
        return runCatching {
            val wsUrl = normalizeWsUrl(desktopUrl)
            webSocketClient.connect(wsUrl)

            // Send pairing request
            webSocketClient.send(
                EcosystemMessage(
                    id = UUID.randomUUID().toString(),
                    type = MessageType.PAIRING_REQUEST,
                    payload = PairingRequest(
                        deviceId = localDeviceId,
                        deviceName = localDeviceName,
                        platform = Platform.ANDROID,
                        publicKey = "" // TODO: ECDH key exchange in future iteration
                    )
                )
            )

            PairingResult(success = true, deviceId = localDeviceId)
        }.getOrElse { e ->
            PairingResult(success = false, error = e.message)
        }
    }

    /**
     * Confirms pairing with the code displayed on the desktop.
     */
    suspend fun confirmPairing(code: String): PairingResult {
        // TODO: Validate the code against the desktop's PairingAck
        // For now, trust the connection if WebSocket is up
        return if (webSocketClient.connectionState.value == WsConnectionState.CONNECTED) {
            PairingResult(success = true, deviceId = localDeviceId, deviceName = localDeviceName)
        } else {
            PairingResult(success = false, error = "Connection lost during pairing")
        }
    }

    private fun normalizeWsUrl(input: String): String {
        val stripped = input.trim()
        return when {
            stripped.startsWith("ws://") || stripped.startsWith("wss://") -> stripped
            else -> "ws://$stripped"
        }.let { url ->
            if (!url.contains("/ws/")) "$url/ws/$localDeviceId" else url
        }
    }
}

package com.theveloper.aura.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Top-level envelope for all messages exchanged between Android and Desktop over WebSocket.
 * Each message carries a [type] discriminator and a unique [id] for request/response correlation.
 */
@Serializable
data class EcosystemMessage(
    val id: String,
    val type: MessageType,
    val payload: MessagePayload
)

@Serializable
enum class MessageType {
    ACTION_REQUEST,
    ACTION_RESPONSE,
    HEARTBEAT,
    CAPABILITY_REPORT,
    PAIRING_REQUEST,
    PAIRING_ACK,
    EVENT
}

@Serializable
sealed class MessagePayload

// ── Action Request (Android → Desktop) ──────────────────────────────────────

@Serializable
@SerialName("action_request")
data class ActionRequest(
    val action: DesktopAction,
    val params: JsonObject = JsonObject(emptyMap()),
    val requiresConfirmation: Boolean = false
) : MessagePayload()

// ── Action Response (Desktop → Android) ─────────────────────────────────────

@Serializable
@SerialName("action_response")
data class ActionResponse(
    val requestId: String,
    val success: Boolean,
    val data: JsonObject? = null,
    val error: String? = null,
    val metadata: ActionMetadata? = null
) : MessagePayload()

@Serializable
data class ActionMetadata(
    val executedAt: Long,
    val deviceId: String,
    val durationMs: Long? = null
)

// ── Device Heartbeat (Desktop → Android, periodic) ──────────────────────────

@Serializable
@SerialName("heartbeat")
data class DeviceHeartbeat(
    val deviceId: String,
    val timestamp: Long,
    val cpuLoadPercent: Float? = null,
    val memoryUsedPercent: Float? = null,
    val thermalState: String? = null,
    val ollamaRunning: Boolean = false
) : MessagePayload()

// ── Capability Report (Desktop → Android, on connect) ───────────────────────

@Serializable
@SerialName("capability_report")
data class DeviceCapabilityReport(
    val deviceId: String,
    val deviceName: String,
    val platform: Platform,
    val supportedActions: List<DesktopAction>,
    val ollamaModels: List<OllamaModelInfo> = emptyList(),
    val protocolVersion: Int = 1
) : MessagePayload()

@Serializable
data class OllamaModelInfo(
    val name: String,
    val sizeBytes: Long? = null,
    val parameterCount: String? = null,
    val quantization: String? = null
)

// ── Pairing (bidirectional handshake) ────────────────────────────────────────

@Serializable
@SerialName("pairing_request")
data class PairingRequest(
    val deviceId: String,
    val deviceName: String,
    val platform: Platform,
    val publicKey: String
) : MessagePayload()

@Serializable
@SerialName("pairing_ack")
data class PairingAck(
    val deviceId: String,
    val deviceName: String,
    val platform: Platform,
    val publicKey: String,
    val accepted: Boolean,
    val pairingCode: String? = null
) : MessagePayload()

// ── Spontaneous Event (Desktop → Android, no prior request) ─────────────────

@Serializable
@SerialName("event")
data class DeviceEvent(
    val event: String,
    val data: JsonObject = JsonObject(emptyMap())
) : MessagePayload()

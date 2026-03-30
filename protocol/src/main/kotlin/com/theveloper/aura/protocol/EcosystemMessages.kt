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
    val id: String = "",
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
    PAIRING_CONFIRM,
    PAIRING_RESULT,
    AUTH_REQUEST,
    AUTH_RESULT,
    EVENT,

    // v3: Multi-Creation-Type sync
    /** Sync any creation type (REMINDER, AUTOMATION, EVENT, TASK) across devices. */
    SYNC_ITEM,
    /** Phone requests desktop to execute an automation step (typically Ollama). */
    AUTOMATION_EXEC,
    /** Desktop returns the result of an automation step execution. */
    AUTOMATION_EXEC_RESULT
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
    val requestId: String? = null,
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
@SerialName("device_heartbeat")
data class DeviceHeartbeat(
    val deviceId: String = "",
    val timestamp: Long = 0L,
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
    val protocolVersion: Int = 3
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
    val platform: Platform
) : MessagePayload()

@Serializable
@SerialName("pairing_ack")
data class PairingAck(
    val deviceId: String,
    val deviceName: String,
    val platform: Platform? = null,
    val accepted: Boolean,
    val pairingCode: String? = null
) : MessagePayload()

@Serializable
@SerialName("pairing_confirm")
data class PairingConfirm(
    val deviceId: String,
    val pairingCode: String
) : MessagePayload()

@Serializable
@SerialName("pairing_result")
data class PairingResultPayload(
    val success: Boolean,
    val trustToken: String? = null,
    val error: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val platform: Platform? = null
) : MessagePayload()

@Serializable
@SerialName("auth_request")
data class AuthRequest(
    val deviceId: String,
    val trustToken: String
) : MessagePayload()

@Serializable
@SerialName("auth_result")
data class AuthResult(
    val success: Boolean,
    val error: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val platform: Platform? = null
) : MessagePayload()

// ── Spontaneous Event (Desktop → Android, no prior request) ─────────────────

@Serializable
@SerialName("event")
data class DeviceEvent(
    val event: String,
    val data: JsonObject = JsonObject(emptyMap())
) : MessagePayload()

// ── v3: Multi-Creation-Type Sync ────────────────────────────────────────────

/**
 * Syncs any creation type across devices.
 * [itemType] discriminates: "TASK", "REMINDER", "AUTOMATION", "EVENT".
 * [operation] is one of: "CREATE", "UPDATE", "DELETE".
 * [payload] carries the full entity JSON.
 */
@Serializable
@SerialName("sync_item")
data class SyncItemPayload(
    val itemType: String,
    val itemId: String,
    val operation: String,
    val payload: JsonObject
) : MessagePayload()

/**
 * Phone requests desktop to execute a single automation step.
 * Typically used for LLM_PROCESS steps routed to desktop Ollama.
 */
@Serializable
@SerialName("automation_exec")
data class AutomationExecRequest(
    val automationId: String,
    val step: JsonObject,
    val context: JsonObject = JsonObject(emptyMap())
) : MessagePayload()

/**
 * Desktop returns the result of an automation step execution.
 */
@Serializable
@SerialName("automation_exec_result")
data class AutomationExecResult(
    val automationId: String,
    val success: Boolean,
    val result: JsonObject? = null,
    val error: String? = null
) : MessagePayload()

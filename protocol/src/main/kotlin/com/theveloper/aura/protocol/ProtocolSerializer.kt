package com.theveloper.aura.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Shared JSON configuration for encoding/decoding ecosystem messages.
 * Both the Android client and the Desktop daemon should use this same config
 * (or its Python equivalent) to ensure wire compatibility.
 */
object ProtocolSerializer {

    private val module = SerializersModule {
        polymorphic(MessagePayload::class) {
            subclass(ActionRequest::class)
            subclass(ActionResponse::class)
            subclass(DeviceHeartbeat::class)
            subclass(DeviceCapabilityReport::class)
            subclass(PairingRequest::class)
            subclass(PairingAck::class)
            subclass(PairingConfirm::class)
            subclass(PairingResultPayload::class)
            subclass(AuthRequest::class)
            subclass(AuthResult::class)
            subclass(DeviceEvent::class)
        }
    }

    val json = Json {
        serializersModule = module
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "payloadType"
    }

    fun encode(message: EcosystemMessage): String = json.encodeToString(EcosystemMessage.serializer(), message)

    fun decode(raw: String): EcosystemMessage = json.decodeFromString(EcosystemMessage.serializer(), raw)
}

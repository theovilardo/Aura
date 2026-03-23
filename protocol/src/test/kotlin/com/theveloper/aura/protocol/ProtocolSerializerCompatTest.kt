package com.theveloper.aura.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolSerializerCompatTest {

    @Test
    fun decodesDesktopPairingAckWithoutEnvelopeId() {
        val raw = """
            {
              "type": "PAIRING_ACK",
              "payload": {
                "payloadType": "pairing_ack",
                "deviceId": "desktop-123",
                "deviceName": "MacBook Pro",
                "platform": "MACOS",
                "accepted": true,
                "pairingCode": "482917"
              }
            }
        """.trimIndent()

        val message = ProtocolSerializer.decode(raw)
        val payload = message.payload as PairingAck

        assertEquals("", message.id)
        assertEquals(MessageType.PAIRING_ACK, message.type)
        assertEquals("desktop-123", payload.deviceId)
        assertEquals("MacBook Pro", payload.deviceName)
        assertEquals("482917", payload.pairingCode)
        assertTrue(payload.accepted)
    }

    @Test
    fun decodesProtocolV2HeartbeatWithoutDeviceMetadata() {
        val raw = """
            {
              "type": "HEARTBEAT",
              "payload": {
                "payloadType": "device_heartbeat",
                "cpuLoadPercent": 23.5,
                "memoryUsedPercent": 67.2,
                "ollamaRunning": true
              }
            }
        """.trimIndent()

        val message = ProtocolSerializer.decode(raw)
        val payload = message.payload as DeviceHeartbeat

        assertEquals(MessageType.HEARTBEAT, message.type)
        assertEquals("", payload.deviceId)
        assertEquals(0L, payload.timestamp)
        assertEquals(23.5f, payload.cpuLoadPercent)
        assertEquals(67.2f, payload.memoryUsedPercent)
        assertTrue(payload.ollamaRunning)
    }

    @Test
    fun roundTripsNewAuthAndPairingMessages() {
        val pairingConfirm = EcosystemMessage(
            id = "confirm-1",
            type = MessageType.PAIRING_CONFIRM,
            payload = PairingConfirm(
                deviceId = "android-1",
                pairingCode = "482917"
            )
        )
        val authRequest = EcosystemMessage(
            id = "auth-1",
            type = MessageType.AUTH_REQUEST,
            payload = AuthRequest(
                deviceId = "android-1",
                trustToken = "secret-token"
            )
        )
        val authResult = EcosystemMessage(
            type = MessageType.AUTH_RESULT,
            payload = AuthResult(success = false, error = "revoked")
        )

        val decodedConfirm = ProtocolSerializer.decode(ProtocolSerializer.encode(pairingConfirm))
        val decodedAuthRequest = ProtocolSerializer.decode(ProtocolSerializer.encode(authRequest))
        val decodedAuthResult = ProtocolSerializer.decode(ProtocolSerializer.encode(authResult))

        assertEquals(MessageType.PAIRING_CONFIRM, decodedConfirm.type)
        assertEquals("482917", (decodedConfirm.payload as PairingConfirm).pairingCode)

        assertEquals(MessageType.AUTH_REQUEST, decodedAuthRequest.type)
        assertEquals("secret-token", (decodedAuthRequest.payload as AuthRequest).trustToken)

        assertEquals("", decodedAuthResult.id)
        assertFalse((decodedAuthResult.payload as AuthResult).success)
    }
}

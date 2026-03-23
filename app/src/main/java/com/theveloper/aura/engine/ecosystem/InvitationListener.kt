package com.theveloper.aura.engine.ecosystem

import android.util.Log
import com.theveloper.aura.protocol.ProtocolSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

data class PairingInvitation(
    val desktopId: String,
    val desktopName: String,
    val wsHost: String,
    val wsPort: Int,
    val timestamp: Long
) {
    val connectionUrl: String
        get() = "ws://$wsHost:$wsPort"
}

@Singleton
class InvitationListener @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenJob: Job? = null
    private var socket: DatagramSocket? = null

    private val _invitations = MutableSharedFlow<PairingInvitation>(extraBufferCapacity = 10)
    val invitations: SharedFlow<PairingInvitation> = _invitations.asSharedFlow()

    @Synchronized
    fun start() {
        if (listenJob?.isActive == true) return
        listenJob = scope.launch { listenLoop() }
    }

    @Synchronized
    fun stop() {
        socket?.close()
        socket = null
        listenJob?.cancel()
        listenJob = null
    }

    private suspend fun listenLoop() {
        val localSocket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(INVITATION_PORT))
            soTimeout = 1000
        }
        socket = localSocket

        val buffer = ByteArray(2048)

        try {
            while (currentCoroutineContext().isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    localSocket.receive(packet)
                    parseInvitation(packet)?.let { invitation ->
                        _invitations.emit(invitation)
                    }
                } catch (_: SocketTimeoutException) {
                    // Keep the socket responsive to coroutine cancellation.
                } catch (socketException: SocketException) {
                    if (currentCoroutineContext().isActive) {
                        Log.e(TAG, "UDP listen error", socketException)
                    }
                } catch (throwable: Throwable) {
                    Log.e(TAG, "Failed to process pairing invitation", throwable)
                }
            }
        } finally {
            if (socket === localSocket) {
                socket = null
            }
            runCatching { localSocket.close() }
        }
    }

    private fun parseInvitation(packet: DatagramPacket): PairingInvitation? {
        val text = packet.data.decodeToString(
            startIndex = 0,
            endIndex = packet.length
        )
        val payload = ProtocolSerializer.json.parseToJsonElement(text).jsonObject

        if (payload["type"]?.jsonPrimitive?.contentOrNull != INVITATION_TYPE) {
            return null
        }

        val desktopId = payload["desktopId"]?.jsonPrimitive?.contentOrNull ?: return null
        val desktopName = payload["desktopName"]?.jsonPrimitive?.contentOrNull ?: "Aura Desktop"
        val wsHost = payload["wsHost"]?.jsonPrimitive?.contentOrNull
            ?: packet.address.hostAddress
            ?: return null
        val wsPort = payload["wsPort"]?.jsonPrimitive?.intOrNull ?: DEFAULT_WS_PORT
        val timestamp = payload["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

        return PairingInvitation(
            desktopId = desktopId,
            desktopName = desktopName,
            wsHost = wsHost,
            wsPort = wsPort,
            timestamp = timestamp
        )
    }

    companion object {
        const val INVITATION_PORT = 8766

        private const val TAG = "InvitationListener"
        private const val INVITATION_TYPE = "PAIRING_INVITATION"
        private const val DEFAULT_WS_PORT = 8765
    }
}

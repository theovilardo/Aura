package com.theveloper.aura.engine.ecosystem

import com.theveloper.aura.protocol.ActionRequest
import com.theveloper.aura.protocol.ActionResponse
import com.theveloper.aura.protocol.DeviceCapabilityReport
import com.theveloper.aura.protocol.DeviceHeartbeat
import com.theveloper.aura.protocol.EcosystemMessage
import com.theveloper.aura.protocol.MessagePayload
import com.theveloper.aura.protocol.MessageType
import com.theveloper.aura.protocol.ProtocolSerializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class WsConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

@Singleton
class AuraWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val deviceRegistry: DeviceRegistry
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var currentUrl: String? = null
    private var shouldReconnect = false

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<EcosystemMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<EcosystemMessage> = _incomingMessages.asSharedFlow()

    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<ActionResponse>>()

    fun connect(url: String) {
        if (_connectionState.value == WsConnectionState.CONNECTED && currentUrl == url) return
        disconnect()

        currentUrl = url
        shouldReconnect = true
        _connectionState.value = WsConnectionState.CONNECTING

        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, createListener())
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = WsConnectionState.DISCONNECTED
    }

    fun send(message: EcosystemMessage) {
        val raw = ProtocolSerializer.encode(message)
        webSocket?.send(raw)
    }

    /**
     * Send an [ActionRequest] and suspend until the desktop responds.
     */
    suspend fun sendAction(request: ActionRequest): ActionResponse {
        val msgId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<ActionResponse>()
        pendingResponses[msgId] = deferred

        send(
            EcosystemMessage(
                id = msgId,
                type = MessageType.ACTION_REQUEST,
                payload = request
            )
        )

        return try {
            deferred.await()
        } finally {
            pendingResponses.remove(msgId)
        }
    }

    private fun createListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionState.value = WsConnectionState.CONNECTED
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = runCatching { ProtocolSerializer.decode(text) }.getOrNull() ?: return

            // Route responses to pending request coroutines
            when (val payload = message.payload) {
                is ActionResponse -> {
                    pendingResponses[payload.requestId]?.complete(payload)
                }
                is DeviceCapabilityReport -> {
                    deviceRegistry.updateFromCapabilityReport(payload, currentUrl ?: "")
                }
                is DeviceHeartbeat -> {
                    deviceRegistry.updateHeartbeat(payload.deviceId)
                }
                else -> {}
            }

            // Also emit to the shared flow for other observers
            scope.launch { _incomingMessages.emit(message) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            handleDisconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handleDisconnect()
        }
    }

    private fun handleDisconnect() {
        _connectionState.value = WsConnectionState.DISCONNECTED
        // Cancel all pending requests
        pendingResponses.values.forEach {
            it.completeExceptionally(WebSocketDisconnectedException())
        }
        pendingResponses.clear()

        if (shouldReconnect) {
            scope.launch {
                _connectionState.value = WsConnectionState.RECONNECTING
                delay(3000)
                currentUrl?.let { connect(it) }
            }
        }
    }
}

class WebSocketDisconnectedException : RuntimeException("WebSocket connection lost")

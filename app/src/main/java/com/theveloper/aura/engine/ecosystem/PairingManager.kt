package com.theveloper.aura.engine.ecosystem

import android.util.Log
import com.theveloper.aura.data.db.PairedDeviceDao
import com.theveloper.aura.data.db.PairedDeviceEntity
import com.theveloper.aura.protocol.AuthResult
import com.theveloper.aura.protocol.EcosystemMessage
import com.theveloper.aura.protocol.MessageType
import com.theveloper.aura.protocol.PairingAck
import com.theveloper.aura.protocol.PairingConfirm
import com.theveloper.aura.protocol.PairingRequest
import com.theveloper.aura.protocol.PairingResultPayload
import com.theveloper.aura.protocol.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PairingResult(
    val success: Boolean,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val error: String? = null
)

data class PairingSessionState(
    val pendingInvitation: PairingInvitation? = null,
    val activeInvitation: PairingInvitation? = null,
    val pairingAck: PairingAck? = null,
    val inProgress: Boolean = false,
    val result: PairingResult? = null
) {
    val awaitingPin: Boolean
        get() = activeInvitation != null && pairingAck?.accepted == true
}

private sealed interface ActiveConnectionSession {
    data class Pairing(val invitation: PairingInvitation) : ActiveConnectionSession

    data class Authenticated(
        val device: PairedDeviceEntity,
        val trustToken: String
    ) : ActiveConnectionSession
}

@Singleton
class PairingManager @Inject constructor(
    private val webSocketClient: AuraWebSocketClient,
    private val deviceRegistry: DeviceRegistry,
    invitationListener: InvitationListener,
    private val pairedDeviceDao: PairedDeviceDao,
    private val localDeviceIdentityStore: LocalDeviceIdentityStore,
    private val trustTokenStore: TrustTokenStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessionState = MutableStateFlow(PairingSessionState())
    val sessionState: StateFlow<PairingSessionState> = _sessionState.asStateFlow()

    private var activeConnectionSession: ActiveConnectionSession? = null
    private var authRequestInFlight = false

    init {
        scope.launch {
            invitationListener.invitations.collect { invitation ->
                _sessionState.update { state ->
                    if (state.inProgress || state.activeInvitation != null) {
                        state
                    } else {
                        state.copy(
                            pendingInvitation = invitation,
                            result = null
                        )
                    }
                }
            }
        }

        scope.launch {
            webSocketClient.connectionState
                .collect { connectionState ->
                    val session = activeConnectionSession as? ActiveConnectionSession.Authenticated
                        ?: return@collect

                    when (connectionState) {
                        WsConnectionState.CONNECTED -> sendAuthRequest(session)
                        WsConnectionState.DISCONNECTED -> authRequestInFlight = false
                        WsConnectionState.CONNECTING,
                        WsConnectionState.RECONNECTING -> Unit
                    }
                }
        }

        scope.launch {
            webSocketClient.incomingMessages.collect { message ->
                when (val payload = message.payload) {
                    is PairingAck -> handlePairingAck(payload)
                    is PairingResultPayload -> handlePairingResult(payload)
                    is AuthResult -> handleAuthResult(payload)
                    else -> Unit
                }
            }
        }
    }

    suspend fun acceptInvitation() {
        val invitation = _sessionState.value.pendingInvitation ?: return

        runCatching {
            _sessionState.update {
                it.copy(
                    pendingInvitation = null,
                    activeInvitation = invitation,
                    pairingAck = null,
                    inProgress = true,
                    result = null
                )
            }
            activeConnectionSession = ActiveConnectionSession.Pairing(invitation)

            val ackDeferred = scope.async(start = CoroutineStart.UNDISPATCHED) {
                webSocketClient.awaitPayload<PairingAck>(timeoutMs = PAIRING_TIMEOUT_MS) {
                    it.deviceId == invitation.desktopId || it.deviceName == invitation.desktopName
                }
            }

            webSocketClient.connectAndAwait(invitation.connectionUrl)
            webSocketClient.send(
                EcosystemMessage(
                    id = newMessageId(),
                    type = MessageType.PAIRING_REQUEST,
                    payload = PairingRequest(
                        deviceId = localDeviceIdentityStore.deviceId(),
                        deviceName = localDeviceIdentityStore.deviceName(),
                        platform = Platform.ANDROID
                    )
                )
            )

            ackDeferred.await()
        }.onFailure { error ->
            Log.e(TAG, "Unable to start pairing", error)
            failPairing(error.message ?: "Couldn't reach the desktop.")
        }
    }

    suspend fun confirmPairing(pairingCode: String) {
        val activeInvitation = _sessionState.value.activeInvitation
        val pairingAck = _sessionState.value.pairingAck
        if (activeInvitation == null || pairingAck == null) return

        runCatching {
            _sessionState.update {
                it.copy(
                    inProgress = true,
                    result = null
                )
            }

            val resultDeferred = scope.async(start = CoroutineStart.UNDISPATCHED) {
                webSocketClient.awaitPayload<PairingResultPayload>(timeoutMs = PAIRING_TIMEOUT_MS)
            }

            webSocketClient.send(
                EcosystemMessage(
                    id = newMessageId(),
                    type = MessageType.PAIRING_CONFIRM,
                    payload = PairingConfirm(
                        deviceId = localDeviceIdentityStore.deviceId(),
                        pairingCode = pairingCode.trim()
                    )
                )
            )

            val resultPayload = resultDeferred.await()
            if (!resultPayload.success || resultPayload.trustToken.isNullOrBlank()) {
                throw IllegalStateException(resultPayload.error ?: "The desktop rejected the PIN.")
            }
            val trustToken = resultPayload.trustToken
                ?: throw IllegalStateException("The desktop did not return a trust token.")

            val pairedDevice = persistSuccessfulPairing(
                invitation = activeInvitation,
                pairingAck = pairingAck,
                resultPayload = resultPayload
            )

            activeConnectionSession = ActiveConnectionSession.Authenticated(
                device = pairedDevice,
                trustToken = trustToken
            )

            _sessionState.update {
                it.copy(
                    activeInvitation = null,
                    pairingAck = null,
                    inProgress = false,
                    result = PairingResult(
                        success = true,
                        deviceId = pairedDevice.id,
                        deviceName = pairedDevice.name
                    )
                )
            }
        }.onFailure { error ->
            Log.e(TAG, "Unable to confirm pairing", error)
            _sessionState.update {
                it.copy(
                    inProgress = false,
                    result = PairingResult(
                        success = false,
                        deviceId = pairingAck.deviceId,
                        deviceName = pairingAck.deviceName,
                        error = error.message ?: "Couldn't confirm the PIN."
                    )
                )
            }
        }
    }

    suspend fun attemptReconnectIfNeeded() {
        if (webSocketClient.connectionState.value != WsConnectionState.DISCONNECTED) return

        val pairedDevice = pairedDeviceDao.getAll().firstOrNull() ?: return
        val trustToken = trustTokenStore.getToken(pairedDevice.id) ?: return

        activeConnectionSession = ActiveConnectionSession.Authenticated(
            device = pairedDevice,
            trustToken = trustToken
        )
        authRequestInFlight = false
        deviceRegistry.markConnectionState(pairedDevice.connectionUrl, ConnectionState.RECONNECTING)
        webSocketClient.connect(pairedDevice.connectionUrl)
    }

    fun dismissInvitation() {
        if (_sessionState.value.inProgress) return
        _sessionState.update {
            it.copy(pendingInvitation = null, result = null)
        }
    }

    fun cancelActivePairing() {
        activeConnectionSession = null
        authRequestInFlight = false
        webSocketClient.disconnect()
        _sessionState.update {
            it.copy(
                pendingInvitation = null,
                activeInvitation = null,
                pairingAck = null,
                inProgress = false
            )
        }
    }

    fun clearResult() {
        _sessionState.update { it.copy(result = null) }
    }

    private fun handlePairingAck(payload: PairingAck) {
        if (activeConnectionSession !is ActiveConnectionSession.Pairing) return

        if (!payload.accepted) {
            failPairing("The desktop refused this pairing request.")
            return
        }

        _sessionState.update {
            it.copy(
                pairingAck = payload,
                inProgress = false,
                result = null
            )
        }
    }

    private fun handlePairingResult(payload: PairingResultPayload) {
        if (activeConnectionSession !is ActiveConnectionSession.Pairing || payload.success) return

        _sessionState.update {
            it.copy(
                inProgress = false,
                result = PairingResult(
                    success = false,
                    error = payload.error ?: "Pairing failed."
                )
            )
        }
    }

    private fun handleAuthResult(payload: AuthResult) {
        authRequestInFlight = false

        val session = activeConnectionSession as? ActiveConnectionSession.Authenticated ?: return
        if (payload.success) {
            scope.launch {
                pairedDeviceDao.updateLastSeen(session.device.id, System.currentTimeMillis())
            }
            return
        }

        trustTokenStore.removeToken(session.device.id)
        activeConnectionSession = null
        webSocketClient.disconnect()
        _sessionState.update {
            it.copy(
                result = PairingResult(
                    success = false,
                    deviceId = session.device.id,
                    deviceName = session.device.name,
                    error = payload.error ?: "The saved desktop token is no longer valid. Pair again."
                )
            )
        }
    }

    private suspend fun persistSuccessfulPairing(
        invitation: PairingInvitation,
        pairingAck: PairingAck,
        resultPayload: PairingResultPayload
    ): PairedDeviceEntity {
        val pairedAt = System.currentTimeMillis()
        val deviceId = resultPayload.deviceId ?: pairingAck.deviceId
        val deviceName = resultPayload.deviceName ?: pairingAck.deviceName
        val platform = (resultPayload.platform ?: pairingAck.platform ?: Platform.MACOS).name
        val trustToken = resultPayload.trustToken.orEmpty()

        trustTokenStore.putToken(deviceId, trustToken)

        val pairedDevice = PairedDeviceEntity(
            id = deviceId,
            name = deviceName,
            platform = platform,
            connectionUrl = invitation.connectionUrl,
            lastSeenAt = pairedAt,
            pairedAt = pairedAt
        )
        pairedDeviceDao.insert(pairedDevice)
        return pairedDevice
    }

    private fun sendAuthRequest(session: ActiveConnectionSession.Authenticated) {
        if (authRequestInFlight) return
        authRequestInFlight = true

        webSocketClient.send(
            EcosystemMessage(
                id = newMessageId(),
                type = MessageType.AUTH_REQUEST,
                payload = com.theveloper.aura.protocol.AuthRequest(
                    deviceId = localDeviceIdentityStore.deviceId(),
                    trustToken = session.trustToken
                )
            )
        )
    }

    private fun failPairing(message: String) {
        activeConnectionSession = null
        authRequestInFlight = false
        webSocketClient.disconnect()
        _sessionState.update {
            it.copy(
                activeInvitation = null,
                pairingAck = null,
                inProgress = false,
                result = PairingResult(success = false, error = message)
            )
        }
    }

    private fun newMessageId(): String = UUID.randomUUID().toString()

    private companion object {
        const val TAG = "PairingManager"
        const val PAIRING_TIMEOUT_MS = 15_000L
    }
}

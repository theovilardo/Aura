package com.theveloper.aura.engine.sync

import com.theveloper.aura.data.db.PairedDeviceDao
import com.theveloper.aura.engine.ecosystem.AuraWebSocketClient
import com.theveloper.aura.engine.ecosystem.WsConnectionState
import com.theveloper.aura.protocol.ActionRequest
import com.theveloper.aura.protocol.DesktopAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages real-time bidirectional sync between phone and connected desktop.
 * Complements the existing [SyncWorker] (Supabase cloud sync) with a direct
 * WebSocket path that works on LAN without internet.
 *
 * When a desktop is connected:
 * - Task changes on the phone are pushed as deltas over WebSocket
 * - Task changes from the desktop are received and applied to Room
 *
 * Encrypted with [SharedSecretCrypto] using the pairing shared secret.
 */
@Singleton
class DirectSyncManager @Inject constructor(
    private val webSocketClient: AuraWebSocketClient,
    private val pairedDeviceDao: PairedDeviceDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncCrypto: SharedSecretCrypto? = null

    fun initialize() {
        scope.launch {
            // Load shared secret from the first paired device
            val device = pairedDeviceDao.getAll().firstOrNull() ?: return@launch
            if (device.sharedSecret.isNotBlank()) {
                syncCrypto = SharedSecretCrypto(device.sharedSecret)
            }

            // Listen for connection state and start sync when connected
            webSocketClient.connectionState
                .filter { it == WsConnectionState.CONNECTED }
                .collect { onDesktopConnected() }
        }
    }

    private suspend fun onDesktopConnected() {
        // TODO: Subscribe to Room InvalidationTracker for task/component changes
        // and push encrypted deltas to the desktop via WebSocket.
        // For now, this is a stub that can be wired up when the desktop
        // implements its side of the sync protocol.
    }

    /**
     * Push a task change delta to the connected desktop.
     */
    suspend fun pushDelta(entityType: String, entityId: String, payloadJson: String) {
        val crypto = syncCrypto ?: return
        val encrypted = crypto.encrypt(payloadJson)

        webSocketClient.sendAction(
            ActionRequest(
                action = DesktopAction.FILE_WRITE,
                params = buildJsonObject {
                    put("type", "SYNC_DELTA")
                    put("entity_type", entityType)
                    put("entity_id", entityId)
                    put("payload", encrypted)
                }
            )
        )
    }
}

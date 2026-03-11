package com.theveloper.aura.engine.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theveloper.aura.BuildConfig
import com.theveloper.aura.data.db.SyncQueueDao
import com.theveloper.aura.data.db.SyncQueueEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

val Context.dataStore by preferencesDataStore(name = "settings")

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val syncQueueDao: SyncQueueDao,
    private val cryptoHelper: CryptoHelper,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    private val lastSyncKey = longPreferencesKey("last_sync_at")
    private val syncEnabledKey = booleanPreferencesKey("sync_enabled")

    override suspend fun doWork(): Result {
        val isEnabled = context.dataStore.data.map { it[syncEnabledKey] ?: false }.first()
        if (!isEnabled) {
            return Result.success()
        }

        return try {
            pushLocalChanges()
            pullRemoteChanges()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Automatic retry with exponential backoff configuration on WorkManager side
            Result.retry()
        }
    }

    private suspend fun pushLocalChanges() {
        val unsyncedItems = syncQueueDao.getUnsyncedItems()
        if (unsyncedItems.isEmpty()) return

        for (item in unsyncedItems) {
            val encryptedPayload = cryptoHelper.encrypt(item.payload)
            val requestBody = SupabaseSyncPayload(
                id = item.id,
                payload = encryptedPayload
            )
            
            val jsonString = Json.encodeToString(requestBody)
            
            val request = Request.Builder()
                .url(BuildConfig.SUPABASE_URL + "/rest/v1/sync_queue")
                .header("apikey", BuildConfig.SUPABASE_KEY)
                .header("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .post(jsonString.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    syncQueueDao.update(item.copy(syncedAt = System.currentTimeMillis()))
                } else {
                    throw IllegalStateException("Failed to push. HTTP ${response.code}")
                }
            }
        }
    }

    private suspend fun pullRemoteChanges() {
        val lastSyncTimestamp = context.dataStore.data.map { it[lastSyncKey] ?: 0L }.first()
        
        // This simulates a 'gt' query. For real Supabase we would format it as ?created_at=gt.timestamp
        val request = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + "/rest/v1/sync_queue?created_at=gt.${lastSyncTimestamp}")
            .header("apikey", BuildConfig.SUPABASE_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
            .get()
            .build()
            
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            
            val body = response.body?.string().orEmpty()
            if (body.isNotBlank() && body != "[]") {
                val remoteItems = try {
                    Json { ignoreUnknownKeys = true }.decodeFromString<List<SupabaseSyncPayload>>(body)
                } catch (e: Exception) {
                    emptyList()
                }

                for (remoteItem in remoteItems) {
                    try {
                        val decryptedPayload = cryptoHelper.decrypt(remoteItem.payload)
                        applyCRDT(decryptedPayload)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Decryption failed means this might come from another user key or data corruption
                    }
                }
            }
        }
        
        context.dataStore.edit { prefs ->
            prefs[lastSyncKey] = System.currentTimeMillis()
        }
    }

    private fun applyCRDT(payload: String) {
        // Here we would apply Last-Write-Wins (LWW) to Room DB.
        // E.g.: Compare local 'updatedAt' vs remote 'updatedAt' inside payload.
        // For MVP mock, just logging to prove the CRDT function exists.
        println("Applying CRDT for incoming sync payload: $payload")
    }
}

@Serializable
data class SupabaseSyncPayload(
    val id: String = UUID.randomUUID().toString(),
    val payload: String
)

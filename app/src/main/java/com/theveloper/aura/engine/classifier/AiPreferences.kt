package com.theveloper.aura.engine.classifier

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.aura.engine.sync.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class AiExecutionMode(
    val storageValue: String,
    val title: String,
    val summary: String,
    val orderSummary: String
) {
    AUTO(
        storageValue = "auto",
        title = "Auto",
        summary = "Lets Aura choose the most sensible route based on what is ready on this device.",
        orderSummary = "Adaptive routing across rules, local, and cloud"
    ),
    LOCAL_FIRST(
        storageValue = "local_first",
        title = "Local-first",
        summary = "Prefers on-device models and only falls back to Groq when local execution is not ready.",
        orderSummary = "Rules -> local -> cloud fallback"
    ),
    CLOUD_FIRST(
        storageValue = "cloud_first",
        title = "Cloud-first",
        summary = "Uses Groq first when available, then falls back to local execution if the API is unavailable.",
        orderSummary = "Cloud -> rules -> local"
    );

    companion object {
        fun fromStorage(value: String?): AiExecutionMode {
            if (value == "local_only") {
                return LOCAL_FIRST
            }
            return entries.firstOrNull { it.storageValue == value } ?: AUTO
        }
    }
}

object AiPreferencesKeys {
    val executionMode = stringPreferencesKey("ai_execution_mode")
}

interface AiExecutionModeStore {
    suspend fun getMode(): AiExecutionMode
}

@Singleton
class DataStoreAiExecutionModeStore @Inject constructor(
    @ApplicationContext private val context: Context
) : AiExecutionModeStore {

    override suspend fun getMode(): AiExecutionMode {
        return context.dataStore.data
            .map { prefs -> AiExecutionMode.fromStorage(prefs[AiPreferencesKeys.executionMode]) }
            .first()
    }
}

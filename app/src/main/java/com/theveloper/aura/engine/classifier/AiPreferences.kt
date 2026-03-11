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
        summary = "Usa reglas, compositor local y solo sube a la nube si el prompt queda ambiguo.",
        orderSummary = "Reglas -> IA local -> API si hace falta"
    ),
    LOCAL_ONLY(
        storageValue = "local_only",
        title = "Solo local",
        summary = "Nunca usa red. Prioriza privacidad y funciona offline.",
        orderSummary = "Reglas -> IA local"
    ),
    CLOUD_FIRST(
        storageValue = "cloud_first",
        title = "Nube primero",
        summary = "Consulta Groq primero si está configurado y vuelve a local si falla.",
        orderSummary = "API -> reglas -> IA local"
    );

    companion object {
        fun fromStorage(value: String?): AiExecutionMode {
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

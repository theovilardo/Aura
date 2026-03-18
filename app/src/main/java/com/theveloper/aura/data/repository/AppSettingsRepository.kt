package com.theveloper.aura.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.aura.engine.classifier.AiExecutionMode
import com.theveloper.aura.engine.classifier.AiPreferencesKeys
import com.theveloper.aura.engine.llm.ModelCatalog
import com.theveloper.aura.engine.sync.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettingsSnapshot(
    val syncEnabled: Boolean = false,
    val aiExecutionMode: AiExecutionMode = AiExecutionMode.AUTO,
    val developerMockHabitDataEnabled: Boolean = false,
    val huggingFaceAccessToken: String = "",
    val groqAccessToken: String = "",
    val preferredPrimaryModelId: String = ModelCatalog.defaultPrimary.id,
    val preferredAdvancedModelId: String = ModelCatalog.defaultAdvanced.id,
    val ecosystemEnabled: Boolean = false
)

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val syncEnabledKey = booleanPreferencesKey("sync_enabled")
    private val ecosystemEnabledKey = booleanPreferencesKey("ecosystem_enabled")
    private val developerMockHabitDataEnabledKey = booleanPreferencesKey("developer_mock_habit_data_enabled")
    private val huggingFaceAccessTokenKey = stringPreferencesKey("huggingface_access_token")
    private val groqAccessTokenKey = stringPreferencesKey("groq_access_token")
    private val preferredPrimaryModelKey = stringPreferencesKey("preferred_primary_local_model")
    private val preferredAdvancedModelKey = stringPreferencesKey("preferred_advanced_local_model")

    val settingsFlow: Flow<AppSettingsSnapshot> = context.dataStore.data.map { prefs ->
        AppSettingsSnapshot(
            syncEnabled = prefs[syncEnabledKey] ?: false,
            aiExecutionMode = AiExecutionMode.fromStorage(prefs[AiPreferencesKeys.executionMode]),
            developerMockHabitDataEnabled = prefs[developerMockHabitDataEnabledKey] ?: false,
            huggingFaceAccessToken = prefs[huggingFaceAccessTokenKey].orEmpty(),
            groqAccessToken = prefs[groqAccessTokenKey].orEmpty(),
            preferredPrimaryModelId = ModelCatalog.primaryById(prefs[preferredPrimaryModelKey]).id,
            preferredAdvancedModelId = ModelCatalog.advancedById(prefs[preferredAdvancedModelKey]).id,
            ecosystemEnabled = prefs[ecosystemEnabledKey] ?: false
        )
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[syncEnabledKey] = enabled
        }
    }

    suspend fun setAiExecutionMode(mode: AiExecutionMode) {
        context.dataStore.edit { prefs ->
            prefs[AiPreferencesKeys.executionMode] = mode.storageValue
        }
    }

    suspend fun setDeveloperMockHabitDataEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[developerMockHabitDataEnabledKey] = enabled
        }
    }

    suspend fun setHuggingFaceAccessToken(token: String) {
        context.dataStore.edit { prefs ->
            if (token.isBlank()) {
                prefs.remove(huggingFaceAccessTokenKey)
            } else {
                prefs[huggingFaceAccessTokenKey] = token.trim()
            }
        }
    }

    suspend fun setGroqAccessToken(token: String) {
        context.dataStore.edit { prefs ->
            if (token.isBlank()) {
                prefs.remove(groqAccessTokenKey)
            } else {
                prefs[groqAccessTokenKey] = token.trim()
            }
        }
    }

    suspend fun setPreferredPrimaryModel(modelId: String) {
        context.dataStore.edit { prefs ->
            prefs[preferredPrimaryModelKey] = ModelCatalog.primaryById(modelId).id
        }
    }

    suspend fun setPreferredAdvancedModel(modelId: String) {
        context.dataStore.edit { prefs ->
            prefs[preferredAdvancedModelKey] = ModelCatalog.advancedById(modelId).id
        }
    }

    suspend fun setEcosystemEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ecosystemEnabledKey] = enabled
        }
    }

    suspend fun getSnapshot(): AppSettingsSnapshot = settingsFlow.first()
}

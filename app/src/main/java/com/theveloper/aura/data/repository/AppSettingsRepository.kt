package com.theveloper.aura.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.theveloper.aura.engine.classifier.AiExecutionMode
import com.theveloper.aura.engine.classifier.AiPreferencesKeys
import com.theveloper.aura.engine.sync.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettingsSnapshot(
    val syncEnabled: Boolean = false,
    val aiExecutionMode: AiExecutionMode = AiExecutionMode.AUTO,
    val developerMockHabitDataEnabled: Boolean = false
)

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val syncEnabledKey = booleanPreferencesKey("sync_enabled")
    private val developerMockHabitDataEnabledKey = booleanPreferencesKey("developer_mock_habit_data_enabled")

    val settingsFlow: Flow<AppSettingsSnapshot> = context.dataStore.data.map { prefs ->
        AppSettingsSnapshot(
            syncEnabled = prefs[syncEnabledKey] ?: false,
            aiExecutionMode = AiExecutionMode.fromStorage(prefs[AiPreferencesKeys.executionMode]),
            developerMockHabitDataEnabled = prefs[developerMockHabitDataEnabledKey] ?: false
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
}

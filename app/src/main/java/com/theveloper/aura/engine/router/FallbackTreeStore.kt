package com.theveloper.aura.engine.router

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.aura.engine.sync.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class FallbackNode(
    val providerId: String,
    val minComplexity: Float = 0f,
    val maxComplexity: Float = 10f,
    val priority: Int,
    val isEnabled: Boolean = true,
    val customLabel: String? = null
)

/**
 * Persists the user's provider priority ordering (the "Fallback Tree").
 * Default tree mirrors current LLMServiceFactory logic:
 * local models first → cloud → rules-only.
 */
@Singleton
class FallbackTreeStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val key = stringPreferencesKey("fallback_tree")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getTree(): List<FallbackNode> {
        val raw = context.dataStore.data
            .map { prefs -> prefs[key] }
            .first()
        return if (raw != null) {
            runCatching { json.decodeFromString<List<FallbackNode>>(raw) }
                .getOrElse { defaultTree() }
        } else {
            defaultTree()
        }
    }

    suspend fun saveTree(nodes: List<FallbackNode>) {
        context.dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(nodes)
        }
    }

    companion object {
        fun defaultTree(): List<FallbackNode> = listOf(
            FallbackNode(providerId = "qwen3-0.6b", priority = 0, maxComplexity = 4f),
            FallbackNode(providerId = "gemma3-1b", priority = 1, maxComplexity = 5f),
            FallbackNode(providerId = "qwen2.5-1.5b", priority = 2, maxComplexity = 7f),
            FallbackNode(providerId = "gemma3n-e2b", priority = 3),
            FallbackNode(providerId = "groq-api", priority = 4),
            FallbackNode(providerId = "rules-only", priority = 5)
        )
    }
}

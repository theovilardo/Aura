package com.theveloper.aura.engine.provider

import com.theveloper.aura.engine.llm.Gemma1BLLMService
import com.theveloper.aura.engine.llm.Gemma3nE2BLLMService
import com.theveloper.aura.engine.llm.GroqLLMService
import com.theveloper.aura.engine.llm.LLMTier
import com.theveloper.aura.engine.llm.ModelCatalog
import com.theveloper.aura.engine.llm.Qwen25InstructLLMService
import com.theveloper.aura.engine.llm.Qwen3SmallLLMService
import com.theveloper.aura.engine.llm.RulesOnlyLLMService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central registry of all available AI providers.
 *
 * On initialization, it wraps every existing [LLMService] into an [LLMServiceAdapter].
 * Desktop-side providers (e.g. Ollama via WebSocket) are registered/unregistered
 * dynamically as devices connect and disconnect.
 */
@Singleton
class ProviderRegistry @Inject constructor(
    groqLLMService: GroqLLMService,
    gemma1BLLMService: Gemma1BLLMService,
    gemma3nE2BLLMService: Gemma3nE2BLLMService,
    qwen25InstructLLMService: Qwen25InstructLLMService,
    qwen3SmallLLMService: Qwen3SmallLLMService,
    rulesOnlyLLMService: RulesOnlyLLMService
) {
    private val _providers = MutableStateFlow<Map<String, ProviderAdapter>>(emptyMap())

    /** Observable snapshot of all registered providers, keyed by providerId. */
    val providers: StateFlow<Map<String, ProviderAdapter>> = _providers.asStateFlow()

    init {
        val builtins = listOf(
            LLMServiceAdapter(
                service = groqLLMService,
                providerId = "groq-api",
                displayName = "Groq Cloud",
                location = ProviderLocation.CLOUD
            ),
            LLMServiceAdapter(
                service = gemma1BLLMService,
                providerId = ModelCatalog.gemma3_1B.id,
                displayName = ModelCatalog.gemma3_1B.displayName,
                location = ProviderLocation.LOCAL_PHONE
            ),
            LLMServiceAdapter(
                service = gemma3nE2BLLMService,
                providerId = ModelCatalog.gemma3nE2B.id,
                displayName = ModelCatalog.gemma3nE2B.displayName,
                location = ProviderLocation.LOCAL_PHONE
            ),
            LLMServiceAdapter(
                service = qwen25InstructLLMService,
                providerId = ModelCatalog.qwen2_5_1_5B.id,
                displayName = ModelCatalog.qwen2_5_1_5B.displayName,
                location = ProviderLocation.LOCAL_PHONE
            ),
            LLMServiceAdapter(
                service = qwen3SmallLLMService,
                providerId = ModelCatalog.qwen3_0_6B.id,
                displayName = ModelCatalog.qwen3_0_6B.displayName,
                location = ProviderLocation.LOCAL_PHONE
            ),
            LLMServiceAdapter(
                service = rulesOnlyLLMService,
                providerId = "rules-only",
                displayName = "Heuristic Rules",
                location = ProviderLocation.LOCAL_PHONE
            )
        )
        _providers.value = builtins.associateBy { it.providerId }
    }

    /** All registered providers. */
    fun all(): List<ProviderAdapter> = _providers.value.values.toList()

    /** Only providers that are currently available for use. */
    fun available(): List<ProviderAdapter> = all().filter { it.isAvailable() }

    /** Available providers filtered by location. */
    fun available(location: ProviderLocation): List<ProviderAdapter> =
        available().filter { it.location == location }

    /** Available providers filtered by capability. */
    fun available(capability: ProviderCapability): List<ProviderAdapter> =
        available().filter { capability in it.capabilities }

    /** Find a provider by its ID, or null if not registered. */
    fun byId(id: String): ProviderAdapter? = _providers.value[id]

    /** Dynamically register a provider (e.g. desktop Ollama on connect). */
    fun register(provider: ProviderAdapter) {
        _providers.update { it + (provider.providerId to provider) }
    }

    /** Unregister a provider (e.g. desktop disconnected). */
    fun unregister(providerId: String) {
        _providers.update { it - providerId }
    }
}

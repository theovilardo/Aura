package com.theveloper.aura.engine.provider

import com.theveloper.aura.engine.classifier.LLMClassificationContext
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.llm.LLMTier
import com.theveloper.aura.protocol.OllamaModelInfo

/**
 * Provider that forwards AI requests to an Ollama instance running on a connected desktop.
 * Communication goes over WebSocket via [DesktopActionClient] (injected in Fase 3).
 *
 * Each instance represents a single Ollama model on a specific desktop device.
 * Multiple instances may be registered when a desktop reports several available models.
 */
class DesktopOllamaProvider(
    private val deviceId: String,
    private val modelInfo: OllamaModelInfo,
    private val sendAction: suspend (action: String, params: Map<String, String>) -> String
) : ProviderAdapter {

    override val providerId: String = "ollama-${deviceId}-${modelInfo.name}"
    override val displayName: String = "Ollama: ${modelInfo.name}"
    override val location: ProviderLocation = ProviderLocation.REMOTE_DESKTOP
    override val tier: LLMTier = LLMTier.GROQ_API // Desktop Ollama models are comparable to cloud tier

    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.CLASSIFY,
        ProviderCapability.COMPLETE,
        ProviderCapability.DAY_RESCUE
    )

    private var available = true

    override fun isAvailable(): Boolean = available

    fun setAvailable(value: Boolean) {
        available = value
    }

    override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
        // TODO: Wire through DesktopActionClient in Fase 3
        // Will send OLLAMA_COMPLETE action with the system prompt + input, parse JSON response
        throw UnsupportedOperationException(
            "DesktopOllamaProvider.classify() requires Fase 3 (WebSocket) to be implemented."
        )
    }

    override suspend fun complete(prompt: String): String {
        return sendAction("OLLAMA_COMPLETE", mapOf("model" to modelInfo.name, "prompt" to prompt))
    }

    override suspend fun getDayRescuePlan(tasksJson: String, patternsJson: String, currentTime: String): String {
        val prompt = buildString {
            appendLine("Given these tasks: $tasksJson")
            appendLine("User patterns: $patternsJson")
            appendLine("Current time: $currentTime")
            appendLine("Generate a rescue plan as a JSON array.")
        }
        return sendAction("OLLAMA_COMPLETE", mapOf("model" to modelInfo.name, "prompt" to prompt))
    }
}

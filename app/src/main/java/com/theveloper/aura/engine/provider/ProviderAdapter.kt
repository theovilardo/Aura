package com.theveloper.aura.engine.provider

import com.theveloper.aura.engine.classifier.LLMClassificationContext
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.llm.LLMTier

/**
 * Unified abstraction over all AI providers in the Aura ecosystem.
 * Wraps both on-device LLMService implementations and remote providers
 * (desktop Ollama, future cloud APIs) behind a single contract.
 */
interface ProviderAdapter {
    val providerId: String
    val displayName: String
    val location: ProviderLocation
    val tier: LLMTier
    val capabilities: Set<ProviderCapability>

    fun isAvailable(): Boolean

    suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput

    suspend fun complete(prompt: String): String =
        throw UnsupportedOperationException("Provider $providerId does not support free completion.")

    suspend fun getDayRescuePlan(tasksJson: String, patternsJson: String, currentTime: String): String =
        "[]"
}

enum class ProviderLocation {
    LOCAL_PHONE,
    REMOTE_DESKTOP,
    CLOUD
}

enum class ProviderCapability {
    CLASSIFY,
    COMPLETE,
    DAY_RESCUE,
    CLARIFICATION,
    MEMORY_WRITER
}

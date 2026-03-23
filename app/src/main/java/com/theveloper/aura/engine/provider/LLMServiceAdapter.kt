package com.theveloper.aura.engine.provider

import com.theveloper.aura.engine.classifier.LLMClassificationContext
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.llm.LLMService
import com.theveloper.aura.engine.llm.LLMTier

/**
 * Bridges an existing [LLMService] into the [ProviderAdapter] interface.
 * This allows the legacy LLM providers (Groq, LiteRT models, RulesOnly)
 * to participate in the new ecosystem routing without any changes to their code.
 */
class LLMServiceAdapter(
    private val service: LLMService,
    override val providerId: String,
    override val displayName: String,
    override val location: ProviderLocation
) : ProviderAdapter {

    override val tier: LLMTier get() = service.tier

    override val capabilities: Set<ProviderCapability> = buildSet {
        add(ProviderCapability.CLASSIFY)
        if (service.supportsDayRescue) {
            add(ProviderCapability.DAY_RESCUE)
            add(ProviderCapability.COMPLETE)
        }
        if (service.supportsMemoryWriter) add(ProviderCapability.MEMORY_WRITER)
    }

    override fun isAvailable(): Boolean = service.isAvailable()

    override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput =
        service.classify(input, context)

    override suspend fun complete(prompt: String): String =
        service.complete(prompt)

    override suspend fun getDayRescuePlan(tasksJson: String, patternsJson: String, currentTime: String): String =
        service.getDayRescuePlan(tasksJson, patternsJson, currentTime)

    /** Access the underlying LLMService for backward-compatible code paths. */
    fun unwrap(): LLMService = service
}

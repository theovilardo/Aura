package com.theveloper.aura.engine.llm

import com.theveloper.aura.engine.classifier.LLMClassificationContext
import com.theveloper.aura.engine.classifier.MissingField
import com.theveloper.aura.engine.dsl.TaskDSLOutput

interface LLMService {
    val tier: LLMTier

    fun isAvailable(): Boolean

    val supportsMemoryWriter: Boolean
        get() = tier in setOf(
            LLMTier.GEMMA_3N_E2B,
            LLMTier.GEMINI_NANO,
            LLMTier.GROQ_API
        )

    val supportsDayRescue: Boolean
        get() = tier != LLMTier.RULES_ONLY

    suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput

    suspend fun getDayRescuePlan(tasksJson: String, patternsJson: String, currentTime: String): String =
        "[]"

    suspend fun complete(prompt: String): String =
        throw UnsupportedOperationException("El tier ${tier.name} no soporta completion libre.")

    suspend fun generateClarification(taskContext: String, missingField: MissingField): String =
        missingField.question
}

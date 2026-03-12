package com.theveloper.aura.engine.llm

enum class LLMTier {
    GEMINI_NANO,
    GEMMA_3N_E2B,
    GEMMA_3_1B,
    GROQ_API,
    RULES_ONLY
}

data class TierDetectionResult(
    val primaryTier: LLMTier,
    val supportsAdvancedTier: Boolean,
    val reasonForTier: String
)

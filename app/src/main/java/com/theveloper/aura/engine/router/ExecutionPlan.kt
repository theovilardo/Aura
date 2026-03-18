package com.theveloper.aura.engine.router

import com.theveloper.aura.engine.provider.ProviderAdapter

data class ExecutionPlan(
    val primaryProvider: ProviderAdapter,
    val fallbackChain: List<ProviderAdapter>,
    val complexity: ComplexityScore,
    val reasoning: ExecutionReasoning,
    val requiresUserApproval: Boolean = false
)

data class ExecutionReasoning(
    val providerReason: String,
    val targetReason: String,
    val complexityFactors: List<String>
)

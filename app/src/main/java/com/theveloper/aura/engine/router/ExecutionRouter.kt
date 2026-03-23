package com.theveloper.aura.engine.router

import com.theveloper.aura.engine.provider.ProviderAdapter
import com.theveloper.aura.engine.provider.ProviderLocation
import com.theveloper.aura.engine.provider.ProviderRegistry
import com.theveloper.aura.protocol.ExecutionTarget
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central routing engine for the Aura ecosystem.
 * Decides WHICH provider on WHICH device should handle each request,
 * based on input complexity, device availability, user preferences,
 * and the configured fallback tree.
 */
@Singleton
class ExecutionRouter @Inject constructor(
    private val providerRegistry: ProviderRegistry,
    private val complexityScorer: ComplexityScorer,
    private val fallbackTreeStore: FallbackTreeStore
) {

    suspend fun route(request: ExecutionRequest): ExecutionPlan {
        val complexity = complexityScorer.score(
            input = request.input,
            entities = request.entities,
            context = request.context
        )

        // User explicitly chose a provider → use it
        request.preferredProviderId?.let { prefId ->
            providerRegistry.byId(prefId)?.let { provider ->
                if (provider.isAvailable()) {
                    return buildPlan(
                        primary = provider,
                        complexity = complexity,
                        providerReason = "User-selected provider",
                        targetReason = "Manual selection"
                    )
                }
            }
        }

        // Get the configured fallback tree
        val tree = fallbackTreeStore.getTree()
        val available = providerRegistry.available(request.capability)

        // Filter and sort candidates based on tree + complexity
        val candidates = resolveCandidates(tree, available, complexity, request.preferredTarget)

        if (candidates.isEmpty()) {
            // Last resort: any available provider
            val fallback = available.firstOrNull()
                ?: throw NoProviderAvailableException(
                    "No providers available for capability ${request.capability}"
                )
            return buildPlan(
                primary = fallback,
                complexity = complexity,
                providerReason = "Last resort — no providers matched complexity ${complexity.score}",
                targetReason = "Only available option"
            )
        }

        val primary = candidates.first()
        val fallbacks = candidates.drop(1)
        val targetReason = resolveTargetReason(primary, request.preferredTarget, complexity)

        return ExecutionPlan(
            primaryProvider = primary,
            fallbackChain = fallbacks,
            complexity = complexity,
            reasoning = ExecutionReasoning(
                providerReason = "Complexity ${complexity.score} (${complexity.tier}) → ${primary.displayName}",
                targetReason = targetReason,
                complexityFactors = complexity.reasons
            ),
            requiresUserApproval = false
        )
    }

    private fun resolveCandidates(
        tree: List<FallbackNode>,
        available: List<ProviderAdapter>,
        complexity: ComplexityScore,
        preferredTarget: ExecutionTarget?
    ): List<ProviderAdapter> {
        val availableById = available.associateBy { it.providerId }

        // Match tree nodes to available providers
        val fromTree = tree
            .filter { it.isEnabled }
            .filter { complexity.score >= it.minComplexity && complexity.score <= it.maxComplexity }
            .sortedBy { it.priority }
            .mapNotNull { node -> availableById[node.providerId] }

        // Apply target preference filter
        val filtered = if (preferredTarget != null && preferredTarget != ExecutionTarget.ANY) {
            val targetLocation = preferredTarget.toProviderLocation()
            val matching = fromTree.filter { it.location == targetLocation }
            // If target filter eliminates everything, fall back to unfiltered
            matching.ifEmpty { fromTree }
        } else {
            fromTree
        }

        // Add any available providers not in the tree (e.g. newly connected desktop Ollama)
        val treeIds = filtered.map { it.providerId }.toSet()
        val extras = available.filter { it.providerId !in treeIds }

        return filtered + extras
    }

    private fun resolveTargetReason(
        provider: ProviderAdapter,
        preferredTarget: ExecutionTarget?,
        complexity: ComplexityScore
    ): String = when {
        preferredTarget != null && preferredTarget != ExecutionTarget.ANY ->
            "Target preference: ${preferredTarget.name}"
        provider.location == ProviderLocation.REMOTE_DESKTOP ->
            "Routed to desktop (complexity ${complexity.score}, desktop has more resources)"
        provider.location == ProviderLocation.CLOUD ->
            "Routed to cloud API"
        else ->
            "Executing on this device"
    }

    private fun buildPlan(
        primary: ProviderAdapter,
        complexity: ComplexityScore,
        providerReason: String,
        targetReason: String
    ): ExecutionPlan {
        val fallbacks = providerRegistry.available()
            .filter { it.providerId != primary.providerId }
        return ExecutionPlan(
            primaryProvider = primary,
            fallbackChain = fallbacks,
            complexity = complexity,
            reasoning = ExecutionReasoning(
                providerReason = providerReason,
                targetReason = targetReason,
                complexityFactors = complexity.reasons
            )
        )
    }
}

private fun ExecutionTarget.toProviderLocation(): ProviderLocation = when (this) {
    ExecutionTarget.LOCAL_PHONE -> ProviderLocation.LOCAL_PHONE
    ExecutionTarget.REMOTE_DESKTOP -> ProviderLocation.REMOTE_DESKTOP
    ExecutionTarget.CLOUD -> ProviderLocation.CLOUD
    ExecutionTarget.ANY -> ProviderLocation.LOCAL_PHONE // fallback
}

class NoProviderAvailableException(message: String) : RuntimeException(message)

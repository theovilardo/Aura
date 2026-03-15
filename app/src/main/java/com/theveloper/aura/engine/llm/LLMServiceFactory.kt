package com.theveloper.aura.engine.llm

import com.theveloper.aura.data.repository.AppSettingsRepository
import com.theveloper.aura.engine.classifier.AiExecutionMode
import com.theveloper.aura.engine.classifier.TaskGenerationSource
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedLLMRoute(
    val service: LLMService,
    val tier: LLMTier,
    val source: TaskGenerationSource,
    val reason: String
)

data class LLMRuntimeStatus(
    val detection: TierDetectionResult,
    val activePrimaryTier: LLMTier,
    val activeAdvancedTier: LLMTier,
    val executionMode: AiExecutionMode,
    val groqConfigured: Boolean,
    val activeReason: String
)

@Singleton
class LLMServiceFactory @Inject constructor(
    private val detector: LLMTierDetector,
    private val appSettingsRepository: AppSettingsRepository,
    private val modelDownloadManager: ModelDownloadManager,
    private val rulesOnlyLLMService: RulesOnlyLLMService,
    private val groqLLMService: GroqLLMService,
    private val gemma1BLLMService: Gemma1BLLMService,
    private val gemma3nE2BLLMService: Gemma3nE2BLLMService,
    private val qwen25InstructLLMService: Qwen25InstructLLMService,
    private val qwen3SmallLLMService: Qwen3SmallLLMService
) {

    suspend fun resolvePrimaryService(modeOverride: AiExecutionMode? = null): ResolvedLLMRoute {
        val settings = appSettingsRepository.getSnapshot()
        val executionMode = modeOverride ?: settings.aiExecutionMode
        val detection = detector.detect(
            cloudFallbackAllowed = executionMode != AiExecutionMode.LOCAL_ONLY,
            groqConfigured = groqLLMService.isAvailable()
        )
        val localRoute = resolveBestLocalRoute(
            detection = detection,
            preferredPrimaryModelId = settings.preferredPrimaryModelId,
            preferredAdvancedModelId = settings.preferredAdvancedModelId
        )
        val cloudRoute = resolveCloudRoute()

        return when (executionMode) {
            AiExecutionMode.CLOUD_FIRST -> cloudRoute ?: localRoute ?: buildRulesRoute(
                detection = detection,
                reason = "Groq is not ready and no local model has been downloaded yet."
            )

            AiExecutionMode.LOCAL_ONLY -> localRoute ?: buildRulesRoute(
                detection = detection,
                reason = "Local-only mode active without a downloaded model. Using rules-based composer."
            )

            AiExecutionMode.AUTO -> localRoute ?: cloudRoute ?: buildRulesRoute(
                detection = detection,
                reason = when (detection.primaryTier) {
                    LLMTier.GEMMA_3_1B,
                    LLMTier.GEMINI_NANO,
                    LLMTier.GEMMA_3N_E2B,
                    LLMTier.QWEN_2_5_1_5B,
                    LLMTier.QWEN_3_0_6B -> "The recommended local model for this device has not been downloaded yet."

                    LLMTier.GROQ_API -> "Groq would be the recommended tier, but it is not configured."
                    LLMTier.RULES_ONLY -> detection.reasonForTier
                }
            )
        }
    }

    suspend fun resolveAdvancedService(modeOverride: AiExecutionMode? = null): ResolvedLLMRoute {
        val settings = appSettingsRepository.getSnapshot()
        val executionMode = modeOverride ?: settings.aiExecutionMode
        val detection = detector.detect(
            cloudFallbackAllowed = executionMode != AiExecutionMode.LOCAL_ONLY,
            groqConfigured = groqLLMService.isAvailable()
        )
        val advancedRoute = resolveAdvancedLocalRoute(
            detection = detection,
            preferredAdvancedModelId = settings.preferredAdvancedModelId
        )
        val cloudRoute = resolveCloudRoute()

        return advancedRoute
            ?: cloudRoute
            ?: resolvePrimaryService(executionMode)
    }

    suspend fun getRuntimeStatus(modeOverride: AiExecutionMode? = null): LLMRuntimeStatus {
        val settings = appSettingsRepository.getSnapshot()
        val executionMode = modeOverride ?: settings.aiExecutionMode
        val detection = detector.detect(
            cloudFallbackAllowed = executionMode != AiExecutionMode.LOCAL_ONLY,
            groqConfigured = groqLLMService.isAvailable()
        )
        val primaryRoute = resolvePrimaryService(executionMode)
        val advancedRoute = resolveAdvancedService(executionMode)
        return LLMRuntimeStatus(
            detection = detection,
            activePrimaryTier = primaryRoute.tier,
            activeAdvancedTier = advancedRoute.tier,
            executionMode = executionMode,
            groqConfigured = groqLLMService.isAvailable(),
            activeReason = primaryRoute.reason
        )
    }

    private fun resolveBestLocalRoute(
        detection: TierDetectionResult,
        preferredPrimaryModelId: String,
        preferredAdvancedModelId: String
    ): ResolvedLLMRoute? {
        return resolveAdvancedLocalRoute(detection, preferredAdvancedModelId)
            ?: resolvePrimaryLocalRoute(preferredPrimaryModelId)
    }

    private fun resolvePrimaryLocalRoute(preferredPrimaryModelId: String): ResolvedLLMRoute? {
        val preferred = ModelCatalog.primaryById(preferredPrimaryModelId)
        return resolvePreferredRoute(
            preferred = preferred,
            candidates = ModelCatalog.primaryModels,
            activeReason = { spec -> "${spec.displayName} is active for local generation." }
        )
    }

    private fun resolveAdvancedLocalRoute(
        detection: TierDetectionResult,
        preferredAdvancedModelId: String
    ): ResolvedLLMRoute? {
        if (!detection.supportsAdvancedTier) return null

        val preferred = ModelCatalog.advancedById(preferredAdvancedModelId)
        return resolvePreferredRoute(
            preferred = preferred,
            candidates = ModelCatalog.advancedModels,
            activeReason = { spec -> "${spec.displayName} is active for richer local tasks." }
        )
    }

    private fun resolveCloudRoute(): ResolvedLLMRoute? {
        return if (groqLLMService.isAvailable()) {
            ResolvedLLMRoute(
                service = groqLLMService,
                tier = LLMTier.GROQ_API,
                source = TaskGenerationSource.GROQ_API,
                reason = "Groq is the active backend for this execution."
            )
        } else {
            null
        }
    }

    private fun buildRulesRoute(
        detection: TierDetectionResult,
        reason: String
    ): ResolvedLLMRoute {
        return ResolvedLLMRoute(
            service = rulesOnlyLLMService,
            tier = LLMTier.RULES_ONLY,
            source = TaskGenerationSource.RULES,
            reason = reason.ifBlank { detection.reasonForTier }
        )
    }

    private fun resolvePreferredRoute(
        preferred: ModelSpec,
        candidates: List<ModelSpec>,
        activeReason: (ModelSpec) -> String
    ): ResolvedLLMRoute? {
        if (modelDownloadManager.isModelDownloaded(preferred)) {
            return buildLocalRoute(
                spec = preferred,
                reason = activeReason(preferred)
            )
        }

        val fallback = candidates.firstOrNull { spec ->
            spec.id != preferred.id && modelDownloadManager.isModelDownloaded(spec)
        } ?: return null

        return buildLocalRoute(
            spec = fallback,
            reason = "Using downloaded ${fallback.displayName} while ${preferred.displayName} is not ready yet."
        )
    }

    private fun buildLocalRoute(
        spec: ModelSpec,
        reason: String
    ): ResolvedLLMRoute {
        return ResolvedLLMRoute(
            service = serviceFor(spec),
            tier = spec.tier,
            source = TaskGenerationSource.LOCAL_AI,
            reason = reason
        )
    }

    private fun serviceFor(spec: ModelSpec): LLMService {
        return when (spec.id) {
            ModelCatalog.gemma3_1B.id -> gemma1BLLMService
            ModelCatalog.gemma3nE2B.id -> gemma3nE2BLLMService
            ModelCatalog.qwen2_5_1_5B.id -> qwen25InstructLLMService
            ModelCatalog.qwen3_0_6B.id -> qwen3SmallLLMService
            else -> rulesOnlyLLMService
        }
    }
}

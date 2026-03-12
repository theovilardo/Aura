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
    val primaryModelDownloaded: Boolean,
    val advancedModelDownloaded: Boolean,
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
    private val gemma3nE2BLLMService: Gemma3nE2BLLMService
) {

    suspend fun resolvePrimaryService(modeOverride: AiExecutionMode? = null): ResolvedLLMRoute {
        val settings = appSettingsRepository.getSnapshot()
        val executionMode = modeOverride ?: settings.aiExecutionMode
        val detection = detector.detect(
            cloudFallbackAllowed = executionMode != AiExecutionMode.LOCAL_ONLY,
            groqConfigured = groqLLMService.isAvailable()
        )
        val localRoute = resolveBestLocalRoute(detection)
        val cloudRoute = resolveCloudRoute()

        return when (executionMode) {
            AiExecutionMode.CLOUD_FIRST -> cloudRoute ?: localRoute ?: buildRulesRoute(
                detection = detection,
                reason = "Groq no está listo y todavía no hay un modelo local descargado."
            )

            AiExecutionMode.LOCAL_ONLY -> localRoute ?: buildRulesRoute(
                detection = detection,
                reason = "Modo local activo sin modelo descargado. Se usa el compositor por reglas."
            )

            AiExecutionMode.AUTO -> localRoute ?: cloudRoute ?: buildRulesRoute(
                detection = detection,
                reason = when (detection.primaryTier) {
                    LLMTier.GEMMA_3_1B,
                    LLMTier.GEMINI_NANO,
                    LLMTier.GEMMA_3N_E2B -> "Falta descargar el modelo local recomendado para este dispositivo."

                    LLMTier.GROQ_API -> "Groq sería el tier recomendado, pero no está configurado."
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
        val advancedRoute = resolveAdvancedLocalRoute(detection)
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
            primaryModelDownloaded = modelDownloadManager.isModelDownloaded(ModelCatalog.gemma3_1B),
            advancedModelDownloaded = modelDownloadManager.isModelDownloaded(ModelCatalog.gemma3nE2B),
            activeReason = primaryRoute.reason
        )
    }

    private fun resolveBestLocalRoute(detection: TierDetectionResult): ResolvedLLMRoute? {
        return resolveAdvancedLocalRoute(detection)
            ?: if (modelDownloadManager.isModelDownloaded(ModelCatalog.gemma3_1B)) {
                ResolvedLLMRoute(
                    service = gemma1BLLMService,
                    tier = LLMTier.GEMMA_3_1B,
                    source = TaskGenerationSource.LOCAL_AI,
                    reason = "Gemma 3 1B está listo para clasificación local."
                )
            } else {
                null
            }
    }

    private fun resolveAdvancedLocalRoute(detection: TierDetectionResult): ResolvedLLMRoute? {
        return if (
            detection.supportsAdvancedTier &&
            modelDownloadManager.isModelDownloaded(ModelCatalog.gemma3nE2B)
        ) {
            ResolvedLLMRoute(
                service = gemma3nE2BLLMService,
                tier = LLMTier.GEMMA_3N_E2B,
                source = TaskGenerationSource.LOCAL_AI,
                reason = "Gemma 3n E2B está activo para tareas avanzadas."
            )
        } else {
            null
        }
    }

    private fun resolveCloudRoute(): ResolvedLLMRoute? {
        return if (groqLLMService.isAvailable()) {
            ResolvedLLMRoute(
                service = groqLLMService,
                tier = LLMTier.GROQ_API,
                source = TaskGenerationSource.GROQ_API,
                reason = "Groq quedó como backend activo para esta ejecución."
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
}

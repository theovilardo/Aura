package com.theveloper.aura.engine.classifier

import com.theveloper.aura.data.repository.AppSettingsRepository
import com.theveloper.aura.domain.model.MemorySlot
import com.theveloper.aura.domain.repository.MemoryRepository
import com.theveloper.aura.engine.dsl.TaskDSLValidator
import com.theveloper.aura.engine.llm.LLMServiceFactory
import com.theveloper.aura.engine.llm.ResolvedLLMRoute
import com.theveloper.aura.engine.memory.MemoryContextBuilder
import com.theveloper.aura.engine.provider.LLMServiceAdapter
import com.theveloper.aura.engine.router.ExecutionRequest
import com.theveloper.aura.engine.router.ExecutionRouter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskClassifier @Inject constructor(
    private val entityExtractorService: EntityExtractorService,
    private val intentClassifier: IntentClassifier,
    private val onDeviceTaskDslService: OnDeviceTaskDslService,
    private val llmServiceFactory: LLMServiceFactory,
    private val aiExecutionModeStore: AiExecutionModeStore,
    private val completenessValidator: CompletenessValidator,
    private val qualityGate: TaskDSLQualityGate,
    private val memoryRepository: MemoryRepository,
    private val memoryContextBuilder: MemoryContextBuilder,
    private val executionRouter: ExecutionRouter,
    private val appSettingsRepository: AppSettingsRepository
) {

    suspend fun classify(
        input: String,
        allowClarification: Boolean = true
    ): TaskGenerationResult {
        val normalizedInput = input.trim()
        require(normalizedInput.isNotBlank()) { "Task text cannot be empty." }

        val extractedEntities = entityExtractorService.extract(normalizedInput)
        val intentResult = intentClassifier.classify(normalizedInput)
        val memorySlots = memoryRepository.getSlots()
        val context = LLMClassificationContext(
            intentConfidence = intentResult.confidence,
            detectedTaskType = intentResult.taskType,
            extractedDates = extractedEntities.dateTimes,
            extractedNumbers = extractedEntities.numbers,
            extractedLocations = extractedEntities.locations,
            memoryContext = memoryContextBuilder.buildContextForClassifier(
                detectedType = intentResult.taskType,
                memorySlots = memorySlots
            )
        )
        val executionMode = aiExecutionModeStore.getMode()
        val warnings = mutableListOf<String>()
        val ecosystemEnabled = appSettingsRepository.getSnapshot().ecosystemEnabled

        // When ecosystem mode is enabled, use the ExecutionRouter to resolve
        // providers across all devices (phone + desktop + cloud).
        // Otherwise, use the legacy LLMServiceFactory path unchanged.
        val route = if (ecosystemEnabled) {
            resolveViaRouter(normalizedInput, extractedEntities, context, executionMode)
        } else {
            llmServiceFactory.resolvePrimaryService(executionMode)
        }

        if (route.reason.isNotBlank() && route.source == TaskGenerationSource.RULES) {
            warnings += route.reason
        }

        buildRouteResult(
            input = normalizedInput,
            context = context,
            executionMode = executionMode,
            route = route,
            intentResult = intentResult,
            extractedEntities = extractedEntities,
            warnings = warnings,
            memorySlots = memorySlots,
            allowClarification = allowClarification
        )?.let { return it }

        // Primary route failed — try the advanced model before falling to heuristics.
        val advancedRoute = if (ecosystemEnabled) {
            // In ecosystem mode, the fallback chain is already in the ExecutionPlan.
            // Try the next provider in the chain instead of the factory's advanced service.
            llmServiceFactory.resolveAdvancedService(executionMode)
        } else {
            llmServiceFactory.resolveAdvancedService(executionMode)
        }
        if (advancedRoute.service !== route.service && advancedRoute.source != TaskGenerationSource.RULES) {
            buildRouteResult(
                input = normalizedInput,
                context = context,
                executionMode = executionMode,
                route = advancedRoute,
                intentResult = intentResult,
                extractedEntities = extractedEntities,
                warnings = warnings,
                memorySlots = memorySlots,
                allowClarification = allowClarification
            )?.let { return it }
        }

        return buildFallbackResult(
            input = normalizedInput,
            context = context,
            executionMode = executionMode,
            intentResult = intentResult,
            extractedEntities = extractedEntities,
            warnings = warnings,
            memorySlots = memorySlots,
            allowClarification = allowClarification
        )
    }

    private suspend fun buildRouteResult(
        input: String,
        context: LLMClassificationContext,
        executionMode: AiExecutionMode,
        route: com.theveloper.aura.engine.llm.ResolvedLLMRoute,
        intentResult: IntentResult,
        extractedEntities: ExtractedEntities,
        warnings: MutableList<String>,
        memorySlots: List<MemorySlot>,
        allowClarification: Boolean
    ): TaskGenerationResult? {
        val routedDsl = runCatching {
            route.service.classify(input, context)
        }.getOrElse {
            warnings += when (route.source) {
                TaskGenerationSource.GROQ_API -> "Groq did not respond correctly. Local route was used."
                TaskGenerationSource.LOCAL_AI -> "Local model did not respond correctly. Heuristic route was used."
                TaskGenerationSource.RULES,
                TaskGenerationSource.MANUAL -> "Configured route failed. Local composition was used."
            }
            return null
        }

        // Quality gate: repair incomplete LLM output before structural validation.
        // This prevents empty-component tasks from either passing through or triggering
        // a full fallback when the LLM got the type/title right but missed components.
        val gatedDsl = when (val gate = qualityGate.enforce(routedDsl, input, extractedEntities)) {
            is TaskDSLQualityGate.GateResult.Passed -> {
                warnings += gate.repairs
                gate.dsl
            }
            is TaskDSLQualityGate.GateResult.Rejected -> {
                warnings += gate.reason
                return null
            }
        }

        return when (val validation = TaskDSLValidator.validate(gatedDsl)) {
            TaskDSLValidator.ValidationResult.Valid -> {
                postProcess(
                    result = TaskGenerationResult(
                        dsl = gatedDsl,
                        source = route.source,
                        executionMode = executionMode,
                        intentConfidence = intentResult.confidence,
                        localConfidence = route.source.confidenceHint(intentResult.confidence),
                        warnings = warnings.distinct()
                    ),
                    input = input,
                    memorySlots = memorySlots,
                    allowClarification = allowClarification
                )
            }

            is TaskDSLValidator.ValidationResult.Invalid -> {
                warnings += "The intelligence layer returned an invalid structure. Local composition was used."
                buildFallbackResult(
                    input = input,
                    context = context,
                    executionMode = executionMode,
                    intentResult = intentResult,
                    extractedEntities = extractedEntities,
                    warnings = warnings,
                    memorySlots = memorySlots,
                    allowClarification = allowClarification
                )
            }
        }
    }

    private suspend fun buildFallbackResult(
        input: String,
        context: LLMClassificationContext,
        executionMode: AiExecutionMode,
        intentResult: IntentResult,
        extractedEntities: ExtractedEntities,
        warnings: MutableList<String>,
        memorySlots: List<MemorySlot>,
        allowClarification: Boolean
    ): TaskGenerationResult {
        val onDeviceResult = runCatching {
            onDeviceTaskDslService.compose(
                OnDeviceTaskDslRequest(
                    input = input,
                    intentResult = intentResult,
                    extractedEntities = extractedEntities,
                    llmContext = context
                )
            )
        }.getOrElse {
            warnings += "Heuristic composition failed. Deterministic preset was used."
            OnDeviceTaskDslResult(
                dsl = TaskDSLBuilder.buildDeterministic(input, intentResult, extractedEntities),
                confidence = intentResult.confidence.coerceAtLeast(0.42f),
                source = TaskGenerationSource.RULES
            )
        }

        // Quality gate on heuristic output too — ensures no empty tasks slip through.
        val gatedFallback = when (val gate = qualityGate.enforce(onDeviceResult.dsl, input, extractedEntities)) {
            is TaskDSLQualityGate.GateResult.Passed -> {
                warnings += gate.repairs
                gate.dsl
            }
            is TaskDSLQualityGate.GateResult.Rejected -> {
                // Gate rejected even after repair — force deterministic
                onDeviceResult.dsl
            }
        }

        val fallbackDsl = when (TaskDSLValidator.validate(gatedFallback)) {
            TaskDSLValidator.ValidationResult.Valid -> {
                gatedFallback
            }

            is TaskDSLValidator.ValidationResult.Invalid -> {
                warnings += "Heuristic composition was invalid. Deterministic preset was used."
                TaskDSLBuilder.buildDeterministic(input, intentResult, extractedEntities)
            }
        }

        return postProcess(
            result = TaskGenerationResult(
                dsl = fallbackDsl,
                source = if (fallbackDsl == onDeviceResult.dsl) onDeviceResult.source else TaskGenerationSource.RULES,
                executionMode = executionMode,
                intentConfidence = intentResult.confidence,
                localConfidence = onDeviceResult.confidence,
                warnings = warnings.distinct()
            ),
            input = input,
            memorySlots = memorySlots,
            allowClarification = allowClarification
        )
    }

    private fun postProcess(
        result: TaskGenerationResult,
        input: String,
        memorySlots: List<MemorySlot>,
        allowClarification: Boolean
    ): TaskGenerationResult {
        val completeness = completenessValidator.enrich(
            input = input,
            dsl = result.dsl,
            memorySlots = memorySlots
        )
        val nonBlockerWarnings = completeness.check.missingFields
            .filterNot { it.isBlocker }
            .map { it.question }
        val fallbackWarning = if (!allowClarification && completeness.clarifications.isNotEmpty()) {
            listOf("Some fields were left undefined. You can complete them later.")
        } else {
            emptyList()
        }

        return result.copy(
            dsl = completeness.dsl,
            warnings = (result.warnings + nonBlockerWarnings + fallbackWarning).distinct(),
            completenessCheck = completeness.check,
            clarifications = if (allowClarification) completeness.clarifications else emptyList()
        )
    }

    /**
     * Resolves a [ResolvedLLMRoute] via the [ExecutionRouter] when ecosystem mode is active.
     * Bridges the router's [ExecutionPlan] back into the legacy [ResolvedLLMRoute] format
     * so the rest of the classification pipeline works unchanged.
     */
    private suspend fun resolveViaRouter(
        input: String,
        entities: ExtractedEntities,
        context: LLMClassificationContext,
        executionMode: AiExecutionMode
    ): ResolvedLLMRoute {
        return runCatching {
            val plan = executionRouter.route(
                ExecutionRequest(
                    input = input,
                    entities = entities,
                    context = context
                )
            )
            val provider = plan.primaryProvider
            val service = if (provider is LLMServiceAdapter) {
                provider.unwrap()
            } else {
                // For non-LLMService providers (e.g. DesktopOllamaProvider),
                // wrap their classify() call through a lightweight bridge.
                object : com.theveloper.aura.engine.llm.LLMService {
                    override val tier = provider.tier
                    override fun isAvailable() = provider.isAvailable()
                    override suspend fun classify(input: String, context: LLMClassificationContext) =
                        provider.classify(input, context)
                    override suspend fun complete(prompt: String) = provider.complete(prompt)
                    override suspend fun getDayRescuePlan(tasksJson: String, patternsJson: String, currentTime: String) =
                        provider.getDayRescuePlan(tasksJson, patternsJson, currentTime)
                }
            }
            val source = when (provider.location) {
                com.theveloper.aura.engine.provider.ProviderLocation.CLOUD -> TaskGenerationSource.GROQ_API
                com.theveloper.aura.engine.provider.ProviderLocation.LOCAL_PHONE -> TaskGenerationSource.LOCAL_AI
                com.theveloper.aura.engine.provider.ProviderLocation.REMOTE_DESKTOP -> TaskGenerationSource.LOCAL_AI
            }
            ResolvedLLMRoute(
                service = service,
                tier = provider.tier,
                source = source,
                reason = plan.reasoning.providerReason
            )
        }.getOrElse {
            // Router failed — fall back to legacy factory
            llmServiceFactory.resolvePrimaryService(executionMode)
        }
    }

    private fun TaskGenerationSource.confidenceHint(intentConfidence: Float): Float {
        return when (this) {
            TaskGenerationSource.GROQ_API -> 0.92f
            TaskGenerationSource.LOCAL_AI -> 0.86f
            TaskGenerationSource.RULES -> intentConfidence.coerceAtLeast(0.64f)
            TaskGenerationSource.MANUAL -> 1f
        }
    }
}

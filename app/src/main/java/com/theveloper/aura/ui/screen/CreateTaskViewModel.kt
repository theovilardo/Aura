package com.theveloper.aura.ui.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.capability.CapabilityRegistry
import com.theveloper.aura.engine.capability.CapabilityRequest
import com.theveloper.aura.engine.capability.CapabilityResponse
import com.theveloper.aura.engine.classifier.AiExecutionMode
import com.theveloper.aura.engine.classifier.ClarificationRequest
import com.theveloper.aura.engine.classifier.TaskClassifier
import com.theveloper.aura.engine.classifier.TaskGenerationResult
import com.theveloper.aura.engine.classifier.TaskGenerationSource
import com.theveloper.aura.engine.dsl.TaskComponentCatalog
import com.theveloper.aura.engine.dsl.TaskComponentContext
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.classifier.TaskDSLBuilder
import com.theveloper.aura.engine.ecosystem.ConnectedDevice
import com.theveloper.aura.engine.ecosystem.ConnectionState
import com.theveloper.aura.engine.ecosystem.DeviceRegistry
import com.theveloper.aura.engine.provider.ProviderAdapter
import com.theveloper.aura.engine.provider.ProviderLocation
import com.theveloper.aura.engine.provider.ProviderRegistry
import com.theveloper.aura.engine.router.ComplexityScorer
import com.theveloper.aura.engine.router.ComplexityScore
import com.theveloper.aura.engine.router.ComplexityTier
import com.theveloper.aura.engine.router.ExecutionMode
import com.theveloper.aura.protocol.ExecutionTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject

@HiltViewModel
class CreateTaskViewModel @Inject constructor(
    private val taskClassifier: TaskClassifier,
    private val capabilityRegistry: CapabilityRegistry,
    private val providerRegistry: ProviderRegistry,
    private val deviceRegistry: DeviceRegistry,
    private val complexityScorer: ComplexityScorer,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialMode = TaskCreationMode.fromNavValue(savedStateHandle["mode"])
    private val initialInput = savedStateHandle.get<String>("input").orEmpty()
    private val autoSubmit = savedStateHandle.get<Boolean>("autoSubmit") ?: false

    private val _uiState = MutableStateFlow(
        CreateTaskUiState(
            mode = initialMode,
            input = initialInput,
            manual = ManualTaskDraft.default()
        )
    )
    val uiState: StateFlow<CreateTaskUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CreateTaskEffect>()
    val effects: SharedFlow<CreateTaskEffect> = _effects.asSharedFlow()

    init {
        // Observe providers and devices to keep ecosystem state in sync
        combine(
            providerRegistry.providers,
            deviceRegistry.devices
        ) { providers, devices ->
            val providerList = providers.values.toList()
            val deviceList = devices.values.toList()
            val selectedProvider = _uiState.value.ecosystem.selectedProviderId
                ?.let { providers[it] }
            val resolvedProvider = selectedProvider ?: resolveAutoProvider(providerList)
            val complexity = computeCurrentComplexity()

            _uiState.update { state ->
                state.copy(
                    ecosystem = state.ecosystem.copy(
                        availableProviders = providerList,
                        connectedDevices = deviceList,
                        resolvedProvider = resolvedProvider,
                        complexityScore = complexity
                    )
                )
            }
        }.launchIn(viewModelScope)

        if (autoSubmit && initialInput.isNotBlank()) {
            submit()
        }
    }

    private fun resolveAutoProvider(providers: List<ProviderAdapter>): ProviderAdapter? {
        return providers
            .filter { it.isAvailable() }
            .minByOrNull { provider ->
                when (provider.location) {
                    ProviderLocation.LOCAL_PHONE -> 0
                    ProviderLocation.REMOTE_DESKTOP -> 1
                    ProviderLocation.CLOUD -> 2
                }
            }
    }

    private fun computeCurrentComplexity(): ComplexityScore {
        val input = _uiState.value.input.ifBlank { _uiState.value.manual.title }
        return if (input.isBlank()) {
            ComplexityScore(0f, ComplexityTier.SIMPLE, emptyMap(), emptyList())
        } else {
            complexityScorer.score(input)
        }
    }

    fun updateInput(value: String) {
        _uiState.update { state ->
            state.copy(
                input = value,
                errorMessage = null,
                clarification = null,
                preview = null
            )
        }
    }

    fun selectMode(mode: TaskCreationMode) {
        _uiState.update { state ->
            state.copy(
                mode = mode,
                errorMessage = null
            )
        }
    }

    fun updateManualTitle(value: String) {
        _uiState.update { state ->
            state.copy(
                manual = state.manual.copy(title = value),
                errorMessage = null
            )
        }
    }

    fun selectManualTaskType(taskType: TaskType?) {
        _uiState.update { state ->
            state.copy(
                manual = state.manual.withTaskTypeOverride(taskType),
                errorMessage = null
            )
        }
    }

    fun toggleTemplate(templateId: String) {
        _uiState.update { state ->
            val current = state.manual.selectedTemplateIds
            val updated = if (templateId in current) current - templateId else current + templateId
            state.copy(
                manual = state.manual.copy(selectedTemplateIds = updated),
                errorMessage = null
            )
        }
    }

    // ── Ecosystem controls ──────────────────────────────────────────

    fun selectEnvironment(target: ExecutionTarget) {
        _uiState.update { state ->
            val newEcosystem = state.ecosystem.copy(selectedEnvironment = target)
            state.copy(ecosystem = newEcosystem)
        }
    }

    fun selectProvider(providerId: String?) {
        _uiState.update { state ->
            val resolved = if (providerId != null) {
                providerRegistry.byId(providerId)
            } else {
                resolveAutoProvider(state.ecosystem.availableProviders)
            }
            state.copy(
                ecosystem = state.ecosystem.copy(
                    selectedProviderId = providerId,
                    resolvedProvider = resolved
                )
            )
        }
    }

    fun selectExecutionMode(mode: ExecutionMode) {
        _uiState.update { state ->
            state.copy(ecosystem = state.ecosystem.copy(executionMode = mode))
        }
    }

    fun setShowComponentsSheet(show: Boolean) {
        _uiState.update { state ->
            state.copy(showComponentsSheet = show)
        }
    }

    fun setShowExecutionModeSheet(show: Boolean) {
        _uiState.update { state ->
            state.copy(showExecutionModeSheet = show)
        }
    }

    fun setShowProviderSheet(show: Boolean) {
        _uiState.update { state ->
            state.copy(showProviderSheet = show)
        }
    }

    fun updateClarificationAnswer(value: String) {
        _uiState.update { state ->
            state.copy(
                clarification = state.clarification?.copy(answer = value),
                errorMessage = null
            )
        }
    }

    fun submit() {
        if (uiState.value.isClassifying || uiState.value.isSaving) {
            return
        }
        if (uiState.value.input.isNotBlank()) {
            submitPrompt()
        } else {
            submitManual()
        }
    }

    fun submitClarification() {
        val state = uiState.value
        val pending = state.clarification ?: return
        val answer = pending.answer.trim()
        if (answer.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please answer the question or skip to create the task.") }
            return
        }

        val updatedAnswers = pending.accumulatedAnswers +
            (pending.currentRequest.fieldName to answer)

        // If more questions remain, advance to the next one without a new LLM call
        if (pending.remainingRequests.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    clarification = pending.copy(
                        currentRequest = pending.remainingRequests.first(),
                        remainingRequests = pending.remainingRequests.drop(1),
                        accumulatedAnswers = updatedAnswers,
                        answer = ""
                    ),
                    errorMessage = null
                )
            }
            return
        }

        // All questions answered — re-classify once with all accumulated answers
        viewModelScope.launch {
            _uiState.update { it.copy(isClassifying = true, errorMessage = null) }

            val enrichedInput = buildString {
                append(pending.classifierInput)
                for ((_, ans) in updatedAnswers) {
                    appendLine()
                    append("User clarification: ")
                    append(ans)
                }
            }

            runCatching { taskClassifier.classify(enrichedInput, allowClarification = false) }
                .onSuccess { result ->
                    val mergedPreview = mergeManualSelections(
                        result = result,
                        manual = state.manual,
                        rawInput = state.input.trim()
                    ).withShapeGuidance(hasExplicitTypeOverride = state.manual.taskTypeOverride != null)
                    _uiState.update {
                        it.copy(
                            isClassifying = false,
                            clarification = null,
                            preview = mergedPreview
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isClassifying = false,
                            errorMessage = error.message ?: "Could not apply the clarification."
                        )
                    }
                }
        }
    }

    fun skipClarification() {
        val pending = uiState.value.clarification ?: return
        _uiState.update {
            it.copy(
                clarification = null,
                preview = pending.baseResult.copy(
                    warnings = (pending.baseResult.warnings +
                        "Some details were left undefined. You can complete them later.").distinct(),
                    clarifications = emptyList()
                ),
                errorMessage = null
            )
        }
    }

    private fun submitPrompt() {
        val state = uiState.value
        val input = state.input.trim()
        if (input.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Write a task before classifying.") }
            return
        }
        val manual = state.manual
        val classifierInput = buildClassifierInput(
            input = input,
            manual = manual
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isClassifying = true,
                    errorMessage = null,
                    preview = null,
                    clarification = null
                )
            }

            runCatching { taskClassifier.classify(classifierInput) }
                .onSuccess { result ->
                    val mergedPreview = mergeManualSelections(
                        result = result,
                        manual = manual,
                        rawInput = input
                    ).withShapeGuidance(hasExplicitTypeOverride = manual.taskTypeOverride != null)
                    val pendingClarifications = mergedPreview.clarifications
                    _uiState.update {
                        it.copy(
                            isClassifying = false,
                            preview = if (pendingClarifications.isEmpty()) mergedPreview else null,
                            clarification = pendingClarifications.firstOrNull()?.let { firstRequest ->
                                PendingClarification(
                                    currentRequest = firstRequest,
                                    remainingRequests = pendingClarifications.drop(1),
                                    classifierInput = classifierInput,
                                    baseResult = mergedPreview
                                )
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isClassifying = false,
                            errorMessage = error.message ?: "Could not interpret the task."
                        )
                    }
                }
        }
    }

    private fun submitManual() {
        val draft = uiState.value.manual
        if (draft.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Add a title for the manual task.") }
            return
        }
        if (draft.selectedTemplateIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Select at least one component.") }
            return
        }

        val now = System.currentTimeMillis()
        val (components, reminders) = TaskComponentCatalog.buildSelection(
            templateIds = draft.selectedTemplateIds,
            now = now,
            context = TaskComponentContext(
                title = draft.title.trim(),
                taskType = draft.resolvedTaskType
            )
        )
        val targetDateMs = components.firstOrNull { component ->
            component.type == com.theveloper.aura.domain.model.ComponentType.COUNTDOWN
        }?.config?.get("targetDate")?.jsonPrimitive?.longOrNull

        _uiState.update {
            it.copy(
                preview = TaskGenerationResult(
                    dsl = TaskDSLOutput(
                        title = draft.title.trim(),
                        type = draft.resolvedTaskType,
                        priority = 1,
                        targetDateMs = targetDateMs,
                        components = components,
                        reminders = reminders
                    ),
                    source = TaskGenerationSource.MANUAL,
                    executionMode = AiExecutionMode.LOCAL_FIRST,
                    intentConfidence = 1f,
                    localConfidence = 1f
                ),
                clarification = null,
                errorMessage = null
            )
        }
    }

    private fun buildClassifierInput(
        input: String,
        manual: ManualTaskDraft
    ): String {
        val hints = buildList {
            manual.title.trim()
                .takeIf { it.isNotBlank() }
                ?.let { add("Preferred title: $it") }
            manual.taskTypeOverride?.let { taskType ->
                add("Task type hint: ${taskType.name.lowercase()}")
            }
        }

        return if (hints.isEmpty()) {
            input
        } else {
            buildString {
                appendLine(input)
                appendLine()
                hints.forEach(::appendLine)
            }.trim()
        }
    }

    private fun mergeManualSelections(
        result: TaskGenerationResult,
        manual: ManualTaskDraft,
        rawInput: String
    ): TaskGenerationResult {
        val preview = result.dsl
        val resolvedTitle = manual.title.trim().ifBlank { preview.title }
        val resolvedType = manual.taskTypeOverride ?: preview.type

        if (manual.selectedTemplateIds.isEmpty()) {
            return result.copy(
                dsl = preview.copy(
                    title = resolvedTitle,
                    type = resolvedType
                )
            )
        }

        val now = System.currentTimeMillis()
        val (manualComponents, manualReminders) = TaskComponentCatalog.buildSelection(
            templateIds = manual.selectedTemplateIds,
            now = now,
            context = TaskComponentContext(
                input = rawInput,
                title = resolvedTitle,
                taskType = resolvedType,
                targetDateMs = preview.targetDateMs
            )
        )
        val existingSignatures = preview.components.map { component ->
            component.type to component.config.toString()
        }.toSet()
        val appendedComponents = manualComponents.filter { component ->
            (component.type to component.config.toString()) !in existingSignatures
        }
        val mergedComponents = (preview.components + appendedComponents)
            .mapIndexed { index, component -> component.copy(sortOrder = index) }
        val mergedReminders = (preview.reminders + manualReminders)
            .distinctBy { it.scheduledAtMs to it.intervalDays }

        return result.copy(
            dsl = preview.copy(
                title = resolvedTitle,
                type = resolvedType,
                components = mergedComponents,
                reminders = mergedReminders
            )
        )
    }

    fun selectPreviewTaskType(taskType: TaskType) {
        val state = uiState.value
        val preview = state.preview ?: return
        val updatedManual = state.manual.withTaskTypeOverride(taskType)
        val rawInput = state.input.trim().ifBlank { preview.dsl.title }
        val rebuiltBase = rebuildPreviewForTaskType(
            preview = preview,
            taskType = taskType,
            rawInput = rawInput
        )
        val rebuiltPreview = mergeManualSelections(
            result = rebuiltBase,
            manual = updatedManual,
            rawInput = rawInput
        ).copy(
            warnings = (rebuiltBase.warnings + "Shape adjusted manually.").distinct()
        ).withShapeGuidance(hasExplicitTypeOverride = true)

        _uiState.update {
            it.copy(
                manual = updatedManual,
                preview = rebuiltPreview,
                clarification = null,
                errorMessage = null
            )
        }
    }

    private fun rebuildPreviewForTaskType(
        preview: TaskGenerationResult,
        taskType: TaskType,
        rawInput: String
    ): TaskGenerationResult {
        val taskTitle = preview.dsl.title
        val targetDateMs = preview.dsl.targetDateMs
        val (components, reminders) = TaskComponentCatalog.buildSelection(
            templateIds = TaskDSLBuilder.defaultTemplateIdsFor(taskType, com.theveloper.aura.engine.classifier.ExtractedEntities(), rawInput),
            now = System.currentTimeMillis(),
            context = TaskComponentContext(
                input = rawInput,
                title = taskTitle,
                taskType = taskType,
                targetDateMs = targetDateMs
            )
        )

        return preview.copy(
            dsl = preview.dsl.copy(
                title = taskTitle,
                type = taskType,
                targetDateMs = targetDateMs,
                components = components,
                reminders = reminders
            )
        )
    }

    private fun TaskGenerationResult.withShapeGuidance(hasExplicitTypeOverride: Boolean): TaskGenerationResult {
        val guidancePrefixes = listOf(
            "Could not detect the shape with confidence.",
            "Built an initial shape using local rules.",
            "The detected shape is tentative.",
            "Shape adjusted manually."
        )
        val preservedWarnings = warnings.filterNot { warning ->
            guidancePrefixes.any(warning::startsWith)
        }
        val guidanceWarnings = buildList {
            when {
                hasExplicitTypeOverride ->
                    add("Shape adjusted manually.")
                source == TaskGenerationSource.RULES && dsl.type == TaskType.GENERAL ->
                    add("Could not detect the shape with confidence. Choose one before saving if the free note doesn't fit.")
                source == TaskGenerationSource.RULES ->
                    add("Built an initial shape using local rules. Review it before saving.")
                localConfidence < 0.62f || intentConfidence < 0.55f ->
                    add("The detected shape is tentative. You can change it before saving.")
            }
        }
        return copy(warnings = (preservedWarnings + guidanceWarnings).distinct())
    }

    fun dismissPreview() {
        _uiState.update { it.copy(preview = null) }
    }

    fun confirmPreview() {
        val state = uiState.value
        val preview = state.preview ?: return
        val eco = state.ecosystem

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = capabilityRegistry.execute(
                CapabilityRequest.CreateTask(
                    dsl = preview.dsl,
                    preferredTarget = eco.selectedEnvironment,
                    preferredProviderId = eco.selectedProviderId,
                    executionMode = eco.executionMode
                )
            )
            when (val response = result.response) {
                is CapabilityResponse.TaskCreated -> {
                    _uiState.value = CreateTaskUiState(
                        mode = uiState.value.mode,
                        manual = ManualTaskDraft.default()
                    )
                    _effects.emit(CreateTaskEffect.TaskCreated(response.taskId))
                }

                CapabilityResponse.Success,
                null -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = result.errorMessage ?: "Could not save the task."
                        )
                    }
                }
            }
        }
    }
}

data class CreateTaskUiState(
    val mode: TaskCreationMode = TaskCreationMode.PROMPT,
    val input: String = "",
    val manual: ManualTaskDraft = ManualTaskDraft.default(),
    val isClassifying: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val preview: TaskGenerationResult? = null,
    val clarification: PendingClarification? = null,
    val ecosystem: CreateTaskEcosystemUiState = CreateTaskEcosystemUiState(),
    val showComponentsSheet: Boolean = false,
    val showExecutionModeSheet: Boolean = false,
    val showProviderSheet: Boolean = false
)

data class CreateTaskEcosystemUiState(
    val selectedEnvironment: ExecutionTarget = ExecutionTarget.ANY,
    val selectedProviderId: String? = null,
    val resolvedProvider: ProviderAdapter? = null,
    val executionMode: ExecutionMode = ExecutionMode.AUTO_DECIDE,
    val availableProviders: List<ProviderAdapter> = emptyList(),
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val complexityScore: ComplexityScore = ComplexityScore(0f, ComplexityTier.SIMPLE, emptyMap(), emptyList())
)

data class PendingClarification(
    val currentRequest: ClarificationRequest,
    val remainingRequests: List<ClarificationRequest> = emptyList(),
    val classifierInput: String,
    val baseResult: TaskGenerationResult,
    val accumulatedAnswers: List<Pair<String, String>> = emptyList(),
    val answer: String = ""
)

data class ManualTaskDraft(
    val title: String,
    val taskTypeOverride: TaskType?,
    val selectedTemplateIds: List<String>
) {
    val resolvedTaskType: TaskType
        get() = taskTypeOverride ?: TaskType.GENERAL

    fun withTaskTypeOverride(taskType: TaskType?): ManualTaskDraft {
        val resolvedTaskType = taskType ?: TaskType.GENERAL
        val supportedSelection = selectedTemplateIds.filter { templateId ->
            TaskComponentCatalog.find(templateId)?.supportedTaskTypes?.contains(resolvedTaskType) == true
        }
        val newSelection = when {
            supportedSelection.isNotEmpty() -> supportedSelection
            taskType != null -> TaskComponentCatalog.recommended(resolvedTaskType).take(2).map { it.id }
            else -> emptyList()
        }
        return copy(
            taskTypeOverride = taskType,
            selectedTemplateIds = newSelection
        )
    }

    companion object {
        fun default(): ManualTaskDraft {
            return ManualTaskDraft(
                title = "",
                taskTypeOverride = null,
                selectedTemplateIds = emptyList()
            )
        }
    }
}

sealed interface CreateTaskEffect {
    data class TaskCreated(val taskId: String) : CreateTaskEffect
}

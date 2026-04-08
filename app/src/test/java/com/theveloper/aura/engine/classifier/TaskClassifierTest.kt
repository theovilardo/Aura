package com.theveloper.aura.engine.classifier

import com.theveloper.aura.data.repository.AppSettingsRepository
import com.theveloper.aura.data.repository.AppSettingsSnapshot
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.repository.MemoryRepository
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.llm.LLMService
import com.theveloper.aura.engine.llm.LLMServiceFactory
import com.theveloper.aura.engine.llm.LLMTier
import com.theveloper.aura.engine.llm.ResolvedLLMRoute
import com.theveloper.aura.engine.memory.MemoryContextBuilder
import com.theveloper.aura.engine.router.ExecutionRouter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskClassifierTest {

    private val entityExtractorService = mockk<EntityExtractorService>()
    private val llmServiceFactory = mockk<LLMServiceFactory>()
    private val routedService = mockk<LLMService>()
    private val intentClassifier = IntentClassifier()
    private val onDeviceTaskDslService = mockk<OnDeviceTaskDslService>()
    private val aiExecutionModeStore = mockk<AiExecutionModeStore>()
    private val memoryRepository = mockk<MemoryRepository>()
    private val completenessValidator = CompletenessValidator()
    private val qualityGate = TaskDSLQualityGate()
    private val taskAgentOrchestrator = mockk<TaskAgentOrchestrator>()
    private val taskDraftRescueService = mockk<TaskDraftRescueService>()
    private val memoryContextBuilder = MemoryContextBuilder()
    private val executionRouter = mockk<ExecutionRouter>()
    private val appSettingsRepository = mockk<AppSettingsRepository>()

    private val subject = TaskClassifier(
        entityExtractorService = entityExtractorService,
        intentClassifier = intentClassifier,
        onDeviceTaskDslService = onDeviceTaskDslService,
        llmServiceFactory = llmServiceFactory,
        aiExecutionModeStore = aiExecutionModeStore,
        completenessValidator = completenessValidator,
        qualityGate = qualityGate,
        taskAgentOrchestrator = taskAgentOrchestrator,
        taskDraftRescueService = taskDraftRescueService,
        memoryRepository = memoryRepository,
        memoryContextBuilder = memoryContextBuilder,
        executionRouter = executionRouter,
        appSettingsRepository = appSettingsRepository
    )

    init {
        coEvery { appSettingsRepository.getSnapshot() } returns AppSettingsSnapshot(ecosystemEnabled = false)
        coEvery { llmServiceFactory.resolveAdvancedService(any()) } returns route(
            source = TaskGenerationSource.RULES,
            tier = LLMTier.RULES_ONLY
        )
        coEvery { routedService.generateClarification(any(), any()) } answers {
            when (secondArg<MissingField>().fieldName) {
                "items" -> "¿Qué elementos deberían incluirse?"
                "targetDate" -> "¿Cuándo vence esto?"
                "goal" -> "¿Tienes un objetivo numérico?"
                "targetCount" -> "¿Cuántas veces debería repetirse?"
                else -> secondArg<MissingField>().question
            }
        }
        coEvery { taskAgentOrchestrator.orchestrate(any(), any(), any()) } returns null
        coEvery { taskDraftRescueService.rescue(any(), any(), any(), any()) } returns null
        every { taskDraftRescueService.needsRescue(any()) } returns false
    }

    @Test
    fun `resolved local route is used as primary backend`() = runTest {
        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities(locations = listOf("Madrid"))
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.AUTO
        coEvery { memoryRepository.getSlots() } returns emptyList()
        coEvery { llmServiceFactory.resolvePrimaryService(AiExecutionMode.AUTO) } returns route(
            source = TaskGenerationSource.LOCAL_AI,
            tier = LLMTier.GEMMA_3_1B
        )
        coEvery { routedService.classify(any(), any()) } returns travelDsl()

        val result = subject.classify("quiero viajar a Madrid en agosto con presupuesto")

        assertEquals(TaskGenerationSource.LOCAL_AI, result.source)
        assertEquals(TaskType.TRAVEL, result.dsl.type)
        assertTrue(result.dsl.components.any { it.type == ComponentType.COUNTDOWN })
        coVerify(exactly = 0) { onDeviceTaskDslService.compose(any()) }
    }

    @Test
    fun `orchestration layer can satisfy task generation before single shot classify`() = runTest {
        val orchestratedDsl = TaskDSLOutput(
            title = "Shopping list for pasta puttanesca",
            type = TaskType.GENERAL,
            components = listOf(
                checklistComponent(
                    sortOrder = 0,
                    label = "Shopping list",
                    items = listOf("tomatoes", "toothpaste", "milk", "pasta", "olives", "capers", "garlic")
                )
            )
        )

        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities()
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.AUTO
        coEvery { memoryRepository.getSlots() } returns emptyList()
        coEvery { llmServiceFactory.resolvePrimaryService(AiExecutionMode.AUTO) } returns route(
            source = TaskGenerationSource.LOCAL_AI,
            tier = LLMTier.GEMMA_4_E4B
        )
        coEvery { taskAgentOrchestrator.orchestrate(any(), any(), any()) } returns orchestratedDsl

        val result = subject.classify(
            "i need a shopping list for tomatoes, toothpaste, milk and also the ingredients to make an italian pasta with salsa puttanesca"
        )

        assertEquals(orchestratedDsl.title, result.dsl.title)
        assertEquals(listOf(ComponentType.CHECKLIST), result.dsl.components.map { it.type })
        assertTrue(result.warnings.any { it.contains("orchestration layer") })
        coVerify(exactly = 0) { routedService.classify(any(), any()) }
    }

    @Test
    fun `failed groq route falls back to local composition`() = runTest {
        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities()
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.CLOUD_FIRST
        coEvery { memoryRepository.getSlots() } returns emptyList()
        coEvery { llmServiceFactory.resolvePrimaryService(AiExecutionMode.CLOUD_FIRST) } returns route(
            source = TaskGenerationSource.GROQ_API,
            tier = LLMTier.GROQ_API
        )
        coEvery { routedService.classify(any(), any()) } throws IllegalStateException("boom")
        coEvery { onDeviceTaskDslService.compose(any()) } returns OnDeviceTaskDslResult(
            dsl = groqFinanceDsl(),
            confidence = 0.73f,
            source = TaskGenerationSource.LOCAL_AI
        )

        val result = subject.classify("pagar alquiler el viernes")

        assertEquals(TaskGenerationSource.RULES, result.source)
        assertEquals(TaskType.FINANCE, result.dsl.type)
        assertTrue(result.warnings.any { it.contains("Groq did not respond correctly") })
        coVerify(exactly = 1) { onDeviceTaskDslService.compose(any()) }
    }

    @Test
    fun `rules route surfaces reason from intelligence layer`() = runTest {
        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities()
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.AUTO
        coEvery { memoryRepository.getSlots() } returns emptyList()
        coEvery { llmServiceFactory.resolvePrimaryService(AiExecutionMode.AUTO) } returns route(
            source = TaskGenerationSource.RULES,
            tier = LLMTier.RULES_ONLY,
            reason = "The recommended local model has not been downloaded yet."
        )
        coEvery { routedService.classify(any(), any()) } returns localThinDsl()

        val result = subject.classify("necesito ordenar papeles")

        assertEquals(TaskGenerationSource.RULES, result.source)
        assertTrue(result.warnings.any { it.contains("recommended local model has not been downloaded") })
    }

    @Test
    fun `shopping prompt asks for clarification after routed failure`() = runTest {
        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities()
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.LOCAL_FIRST
        coEvery { memoryRepository.getSlots() } returns emptyList()
        coEvery { llmServiceFactory.resolvePrimaryService(AiExecutionMode.LOCAL_FIRST) } returns route(
            source = TaskGenerationSource.LOCAL_AI,
            tier = LLMTier.GEMMA_3_1B
        )
        coEvery { routedService.classify(any(), any()) } throws IllegalStateException("local model unavailable")
        coEvery { onDeviceTaskDslService.compose(any()) } returns OnDeviceTaskDslResult(
            dsl = shoppingDsl(),
            confidence = 0.8f,
            source = TaskGenerationSource.LOCAL_AI
        )

        val result = subject.classify("lista de compras")

        assertEquals("¿Qué elementos deberían incluirse?", result.clarification?.question)
        assertTrue(result.dsl.components.any { it.needsClarification })
    }

    @Test
    fun `shopping prompt with bullet point items populates checklist without clarification`() = runTest {
        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities()
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.LOCAL_FIRST
        coEvery { memoryRepository.getSlots() } returns emptyList()
        coEvery { llmServiceFactory.resolvePrimaryService(AiExecutionMode.LOCAL_FIRST) } returns route(
            source = TaskGenerationSource.LOCAL_AI,
            tier = LLMTier.GEMMA_3_1B
        )
        coEvery { routedService.classify(any(), any()) } throws IllegalStateException("local model unavailable")
        coEvery { onDeviceTaskDslService.compose(any()) } returns OnDeviceTaskDslResult(
            dsl = shoppingDsl(),
            confidence = 0.8f,
            source = TaskGenerationSource.LOCAL_AI
        )

        val result = subject.classify("shopping list\n- milk\n- bread\n- tomatoes")
        val checklist = result.dsl.components.first { it.type == ComponentType.CHECKLIST }
        val items = checklist.config["items"].toString()

        assertEquals(null, result.clarification)
        assertTrue(items.contains("milk"))
        assertTrue(items.contains("bread"))
        assertTrue(items.contains("tomatoes"))
    }

    @Test
    fun `underfilled routed draft is improved by rescue pass before quality gate`() = runTest {
        val rescuedDsl = TaskDSLOutput(
            title = "Brotrezept",
            type = TaskType.GENERAL,
            components = listOf(
                checklistComponent(
                    sortOrder = 0,
                    label = "Zutaten",
                    items = listOf("Mehl", "Backpulver", "Salz")
                ),
                notesComponent(
                    sortOrder = 1,
                    text = "## Schritte\n1. Mischen\n2. Backen"
                )
            )
        )

        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities()
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.LOCAL_FIRST
        coEvery { memoryRepository.getSlots() } returns emptyList()
        coEvery { llmServiceFactory.resolvePrimaryService(AiExecutionMode.LOCAL_FIRST) } returns route(
            source = TaskGenerationSource.LOCAL_AI,
            tier = LLMTier.GEMMA_3_1B
        )
        coEvery { routedService.classify(any(), any()) } returns localThinDsl()
        coEvery { taskDraftRescueService.rescue(any(), any(), any(), any()) } returns rescuedDsl

        val result = subject.classify("Ich brauche ein schnelles Brotrezept")

        assertEquals(listOf(ComponentType.CHECKLIST, ComponentType.NOTES), result.dsl.components.map { it.type })
        assertTrue(result.warnings.any { it.contains("second intelligence pass") })
    }

    @Test
    fun `fallback scaffold can be repaired by ai after shallow routed draft`() = runTest {
        val teaserDraft = TaskDSLOutput(
            title = "Chocolate pudding recipe",
            type = TaskType.GENERAL,
            components = listOf(
                notesComponent(0, "Here is a chocolate pudding recipe. Please wait for the full recipe content.")
            )
        )
        val repairedFallback = TaskDSLOutput(
            title = "Chocolate pudding recipe",
            type = TaskType.GENERAL,
            components = listOf(
                checklistComponent(
                    sortOrder = 0,
                    label = "Ingredients",
                    items = listOf("milk", "cocoa powder", "sugar", "cornstarch")
                ),
                notesComponent(
                    sortOrder = 1,
                    text = "## Steps\n1. Heat the milk.\n2. Whisk in cocoa, sugar, and cornstarch.\n3. Cook until thick, then chill."
                )
            )
        )

        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities()
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.LOCAL_FIRST
        coEvery { memoryRepository.getSlots() } returns emptyList()
        coEvery { llmServiceFactory.resolvePrimaryService(AiExecutionMode.LOCAL_FIRST) } returns route(
            source = TaskGenerationSource.LOCAL_AI,
            tier = LLMTier.GEMMA_4_E4B
        )
        coEvery { llmServiceFactory.resolveAdvancedService(AiExecutionMode.LOCAL_FIRST) } returns route(
            source = TaskGenerationSource.RULES,
            tier = LLMTier.RULES_ONLY
        )
        coEvery { routedService.classify(any(), any()) } returns teaserDraft
        coEvery { onDeviceTaskDslService.compose(any()) } returns OnDeviceTaskDslResult(
            dsl = TaskDSLOutput(
                title = "Chocolate pudding recipe",
                type = TaskType.GENERAL,
                components = listOf(notesComponent(0, ""))
            ),
            confidence = 0.41f,
            source = TaskGenerationSource.RULES
        )
        coEvery { taskDraftRescueService.rescue(any(), any(), any(), any()) } answers {
            if (args[3] as TaskDSLOutput == teaserDraft) teaserDraft else repairedFallback
        }
        every { taskDraftRescueService.needsRescue(any()) } returns true
        every { taskDraftRescueService.needsRescue(teaserDraft) } returns true
        every {
            taskDraftRescueService.needsRescue(match { draft ->
                draft.components.size == 1 &&
                    draft.components.first().type == ComponentType.NOTES &&
                    draft.components.first().config["text"]?.toString()?.trim('"').orEmpty().isBlank()
            })
        } returns true
        every { taskDraftRescueService.needsRescue(repairedFallback) } returns false

        val result = subject.classify("I need a chocolate pudding recipe")

        assertEquals(listOf(ComponentType.CHECKLIST, ComponentType.NOTES), result.dsl.components.map { it.type })
        assertTrue(result.warnings.any { it.contains("heuristic fallback was upgraded by an AI repair pass") })
    }

    @Test
    fun `failed ai fallback repair still avoids returning hollow notes`() = runTest {
        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities()
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.LOCAL_FIRST
        coEvery { memoryRepository.getSlots() } returns emptyList()
        coEvery { llmServiceFactory.resolvePrimaryService(AiExecutionMode.LOCAL_FIRST) } returns route(
            source = TaskGenerationSource.LOCAL_AI,
            tier = LLMTier.GEMMA_4_E4B
        )
        coEvery { llmServiceFactory.resolveAdvancedService(AiExecutionMode.LOCAL_FIRST) } returns route(
            source = TaskGenerationSource.RULES,
            tier = LLMTier.RULES_ONLY
        )
        coEvery { routedService.classify(any(), any()) } throws IllegalStateException("boom")
        coEvery { onDeviceTaskDslService.compose(any()) } returns OnDeviceTaskDslResult(
            dsl = TaskDSLOutput(
                title = "Chocolate pudding recipe",
                type = TaskType.GENERAL,
                components = listOf(notesComponent(0, ""))
            ),
            confidence = 0.41f,
            source = TaskGenerationSource.RULES
        )
        coEvery { taskDraftRescueService.rescue(any(), any(), any(), any()) } returns null
        every { taskDraftRescueService.needsRescue(any()) } returns true

        val result = subject.classify("I need a chocolate pudding recipe")

        assertTrue(result.dsl.components.any { it.type == ComponentType.CHECKLIST })
        assertTrue(
            result.dsl.components.none { component ->
                component.type == ComponentType.NOTES &&
                    component.config["text"]?.toString()?.trim('"').orEmpty().isBlank()
            }
        )
    }

    private fun route(
        source: TaskGenerationSource,
        tier: LLMTier,
        reason: String = ""
    ) = ResolvedLLMRoute(
        service = routedService,
        tier = tier,
        source = source,
        reason = reason
    )

    private fun travelDsl() = TaskDSLOutput(
        title = "Viaje a Madrid",
        type = TaskType.TRAVEL,
        components = listOf(
            countdownComponent(0, "Trip date"),
            checklistComponent(1, "Travel prep")
        )
    )

    private fun localThinDsl() = TaskDSLOutput(
        title = "Organizar algo importante",
        type = TaskType.GENERAL,
        components = listOf(notesComponent(0, ""))
    )

    private fun groqFinanceDsl() = TaskDSLOutput(
        title = "Pagar alquiler",
        type = TaskType.FINANCE,
        components = listOf(
            countdownComponent(0, "Payment due"),
            checklistComponent(1, "Payment steps")
        )
    )

    private fun shoppingDsl() = TaskDSLOutput(
        title = "Lista de compras",
        type = TaskType.GENERAL,
        components = listOf(
            checklistComponent(
                sortOrder = 0,
                label = "Key steps",
                items = listOf("Define next step", "Execute", "Review result")
            )
        )
    )

    private fun countdownComponent(sortOrder: Int, label: String) = ComponentDSL(
        type = ComponentType.COUNTDOWN,
        sortOrder = sortOrder,
        config = buildJsonObject {
            put("config_type", "COUNTDOWN")
            put("targetDate", 1_726_000_000_000)
            put("label", label)
        }
    )

    private fun checklistComponent(
        sortOrder: Int,
        label: String,
        items: List<String> = emptyList()
    ) = ComponentDSL(
        type = ComponentType.CHECKLIST,
        sortOrder = sortOrder,
        config = buildJsonObject {
            put("config_type", "CHECKLIST")
            put("label", label)
            put("allowAddItems", true)
            if (items.isNotEmpty()) {
                putJsonArray("items") {
                    items.forEach { add(JsonPrimitive(it)) }
                }
            }
        }
    )

    private fun notesComponent(sortOrder: Int, text: String) = ComponentDSL(
        type = ComponentType.NOTES,
        sortOrder = sortOrder,
        config = buildJsonObject {
            put("config_type", "NOTES")
            put("text", text)
            put("isMarkdown", true)
        }
    )
}

package com.theveloper.aura.engine.classifier

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
    private val memoryContextBuilder = MemoryContextBuilder()

    private val subject = TaskClassifier(
        entityExtractorService = entityExtractorService,
        intentClassifier = intentClassifier,
        onDeviceTaskDslService = onDeviceTaskDslService,
        llmServiceFactory = llmServiceFactory,
        aiExecutionModeStore = aiExecutionModeStore,
        completenessValidator = completenessValidator,
        memoryRepository = memoryRepository,
        memoryContextBuilder = memoryContextBuilder
    )

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

        assertEquals(TaskGenerationSource.LOCAL_AI, result.source)
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
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.LOCAL_ONLY
        coEvery { memoryRepository.getSlots() } returns emptyList()
        coEvery { llmServiceFactory.resolvePrimaryService(AiExecutionMode.LOCAL_ONLY) } returns route(
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

        assertEquals("What items should be included?", result.clarification?.question)
        assertTrue(result.dsl.components.any { it.needsClarification })
    }

    @Test
    fun `shopping prompt with bullet point items populates checklist without clarification`() = runTest {
        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities()
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.LOCAL_ONLY
        coEvery { memoryRepository.getSlots() } returns emptyList()
        coEvery { llmServiceFactory.resolvePrimaryService(AiExecutionMode.LOCAL_ONLY) } returns route(
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

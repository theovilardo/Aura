package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskClassifierTest {

    private val entityExtractorService = mockk<EntityExtractorService>()
    private val llmService = mockk<LLMService>()
    private val intentClassifier = IntentClassifier()
    private val onDeviceTaskDslService = mockk<OnDeviceTaskDslService>()
    private val aiExecutionModeStore = mockk<AiExecutionModeStore>()

    private val subject = TaskClassifier(
        entityExtractorService = entityExtractorService,
        intentClassifier = intentClassifier,
        onDeviceTaskDslService = onDeviceTaskDslService,
        llmService = llmService,
        aiExecutionModeStore = aiExecutionModeStore
    )

    @Test
    fun `auto mode keeps local result for strong prompt`() = runTest {
        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities(
            locations = listOf("Madrid")
        )
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.AUTO
        every { llmService.isAvailable() } returns true
        coEvery { onDeviceTaskDslService.compose(any()) } returns OnDeviceTaskDslResult(
            dsl = travelDsl(),
            confidence = 0.91f,
            source = TaskGenerationSource.LOCAL_AI
        )

        val result = subject.classify("quiero viajar a Madrid en agosto con presupuesto")

        assertEquals(TaskGenerationSource.LOCAL_AI, result.source)
        assertEquals(TaskType.TRAVEL, result.dsl.type)
        assertTrue(result.dsl.components.any { it.type == ComponentType.COUNTDOWN })
        coVerify(exactly = 0) { llmService.classify(any(), any()) }
    }

    @Test
    fun `auto mode escalates to groq for weak local result`() = runTest {
        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities()
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.AUTO
        every { llmService.isAvailable() } returns true
        coEvery { onDeviceTaskDslService.compose(any()) } returns OnDeviceTaskDslResult(
            dsl = localThinDsl(),
            confidence = 0.45f,
            source = TaskGenerationSource.RULES
        )
        coEvery { llmService.classify(any(), any()) } returns groqProjectDsl()

        val result = subject.classify("organizar algo importante para el equipo")

        assertEquals(TaskGenerationSource.GROQ_API, result.source)
        assertEquals(TaskType.PROJECT, result.dsl.type)
        coVerify(exactly = 1) { llmService.classify(any(), any()) }
    }

    @Test
    fun `cloud first uses groq before local fallback`() = runTest {
        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities()
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.CLOUD_FIRST
        every { llmService.isAvailable() } returns true
        coEvery { onDeviceTaskDslService.compose(any()) } returns OnDeviceTaskDslResult(
            dsl = travelDsl(),
            confidence = 0.9f,
            source = TaskGenerationSource.LOCAL_AI
        )
        coEvery { llmService.classify(any(), any()) } returns groqFinanceDsl()

        val result = subject.classify("pagar alquiler el viernes")

        assertEquals(TaskGenerationSource.GROQ_API, result.source)
        assertEquals(TaskType.FINANCE, result.dsl.type)
        coVerify(exactly = 1) { llmService.classify(any(), any()) }
    }

    @Test
    fun `missing groq config stays on local and surfaces warning`() = runTest {
        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities()
        coEvery { aiExecutionModeStore.getMode() } returns AiExecutionMode.CLOUD_FIRST
        every { llmService.isAvailable() } returns false
        coEvery { onDeviceTaskDslService.compose(any()) } returns OnDeviceTaskDslResult(
            dsl = localThinDsl(),
            confidence = 0.71f,
            source = TaskGenerationSource.RULES
        )

        val result = subject.classify("necesito ordenar papeles")

        assertEquals(TaskGenerationSource.RULES, result.source)
        assertTrue(result.warnings.any { it.contains("Groq no esta configurado") })
        coVerify(exactly = 0) { llmService.classify(any(), any()) }
    }

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

    private fun groqProjectDsl() = TaskDSLOutput(
        title = "Organizar entrega",
        type = TaskType.PROJECT,
        components = listOf(
            progressComponent(0, "Avance general"),
            notesComponent(1, "### Summary")
        )
    )

    private fun groqFinanceDsl() = TaskDSLOutput(
        title = "Pagar alquiler",
        type = TaskType.FINANCE,
        components = listOf(
            countdownComponent(0, "Payment due"),
            checklistComponent(1, "Payment steps")
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

    private fun checklistComponent(sortOrder: Int, label: String) = ComponentDSL(
        type = ComponentType.CHECKLIST,
        sortOrder = sortOrder,
        config = buildJsonObject {
            put("config_type", "CHECKLIST")
            put("label", label)
            put("allowAddItems", true)
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

    private fun progressComponent(sortOrder: Int, label: String) = ComponentDSL(
        type = ComponentType.PROGRESS_BAR,
        sortOrder = sortOrder,
        config = buildJsonObject {
            put("config_type", "PROGRESS_BAR")
            put("source", "MANUAL")
            put("label", label)
            put("manualProgress", 0.2)
        }
    )
}

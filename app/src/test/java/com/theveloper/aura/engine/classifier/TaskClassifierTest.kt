package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import io.mockk.coEvery
import io.mockk.coVerify
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

    private val subject = TaskClassifier(
        entityExtractorService = entityExtractorService,
        intentClassifier = intentClassifier,
        llmService = llmService
    )

    @Test
    fun `high confidence input uses deterministic path`() = runTest {
        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities(
            locations = listOf("Madrid")
        )

        val result = subject.classify("quiero viajar a Madrid en agosto")

        assertEquals(TaskType.TRAVEL, result.type)
        assertTrue(result.components.any { it.type == ComponentType.COUNTDOWN })
        assertTrue(result.components.any { it.type == ComponentType.CHECKLIST })
        coVerify(exactly = 0) { llmService.classify(any(), any()) }
    }

    @Test
    fun `low confidence input falls back to llm`() = runTest {
        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities()
        coEvery { llmService.classify(any(), any()) } returns llmProjectDsl()

        val result = subject.classify("organizar algo importante")

        assertEquals(TaskType.PROJECT, result.type)
        coVerify(exactly = 1) { llmService.classify(any(), any()) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty input throws`() = runTest {
        subject.classify("   ")
    }

    @Test
    fun `other language input still resolves through fallback without crash`() = runTest {
        coEvery { entityExtractorService.extract(any()) } returns ExtractedEntities()
        coEvery { llmService.classify(any(), any()) } returns TaskDSLOutput(
            title = "Pay rent next Friday",
            type = TaskType.FINANCE,
            components = listOf(
                ComponentDSL(
                    type = ComponentType.NOTES,
                    sortOrder = 0,
                    config = buildJsonObject {
                        put("config_type", "NOTES")
                        put("text", "")
                        put("isMarkdown", true)
                    }
                )
            )
        )

        val result = subject.classify("Need to pay rent next Friday")

        assertEquals(TaskType.FINANCE, result.type)
        coVerify(exactly = 1) { llmService.classify(any(), any()) }
    }

    private fun llmProjectDsl() = TaskDSLOutput(
        title = "Organizar entrega",
        type = TaskType.PROJECT,
        components = listOf(
            ComponentDSL(
                type = ComponentType.PROGRESS_BAR,
                sortOrder = 0,
                config = buildJsonObject {
                    put("config_type", "PROGRESS_BAR")
                    put("source", "MANUAL")
                    put("label", "Avance general")
                    put("manualProgress", 0.2)
                }
            )
        )
    )
}

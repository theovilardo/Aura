package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicOnDeviceTaskDslServiceTest {

    private val subject = HeuristicOnDeviceTaskDslService()

    // --- Finance: currency signals ---

    @Test
    fun `currency symbol bumps type to FINANCE and adds budget components`() = runTest {
        val result = subject.compose(
            OnDeviceTaskDslRequest(
                input = "Save \$500 USD per month for vacation",
                intentResult = IntentResult(TaskType.FINANCE, 0.55f),
                extractedEntities = ExtractedEntities(numbers = listOf(500.0)),
                llmContext = LLMClassificationContext(
                    intentConfidence = 0.55f,
                    extractedNumbers = listOf(500.0)
                )
            )
        )

        assertEquals(TaskType.FINANCE, result.dsl.type)
        assertTrue(
            result.dsl.components.any {
                it.type == ComponentType.METRIC_TRACKER || it.type == ComponentType.PROGRESS_BAR
            }
        )
        assertEquals(TaskGenerationSource.LOCAL_AI, result.source)
    }

    // --- Travel: location + multiple dates ---

    @Test
    fun `location plus multiple extracted dates resolves to travel shape`() = runTest {
        val result = subject.compose(
            OnDeviceTaskDslRequest(
                input = "Trip to Paris",
                intentResult = IntentResult(TaskType.GENERAL, 0.30f),
                extractedEntities = ExtractedEntities(
                    dateTimes = listOf(1_756_000_000_000L, 1_758_000_000_000L),
                    locations = listOf("Paris")
                ),
                llmContext = LLMClassificationContext(
                    intentConfidence = 0.30f,
                    extractedDates = listOf(1_756_000_000_000L, 1_758_000_000_000L),
                    extractedLocations = listOf("Paris")
                )
            )
        )

        assertEquals(TaskType.TRAVEL, result.dsl.type)
        assertTrue(result.dsl.components.any { it.type == ComponentType.COUNTDOWN })
        assertEquals(TaskGenerationSource.LOCAL_AI, result.source)
    }

    // --- Health: set×rep notation ---

    @Test
    fun `set-rep notation bumps type to HEALTH and adds fitness components`() = runTest {
        val result = subject.compose(
            OnDeviceTaskDslRequest(
                input = "Squats 3x10 bench press 4x8",
                intentResult = IntentResult(TaskType.HEALTH, 0.48f),
                extractedEntities = ExtractedEntities(numbers = listOf(3.0, 10.0, 4.0, 8.0)),
                llmContext = LLMClassificationContext(intentConfidence = 0.48f)
            )
        )

        assertEquals(TaskType.HEALTH, result.dsl.type)
        assertTrue(result.dsl.components.any { it.type == ComponentType.METRIC_TRACKER })
        assertEquals(TaskGenerationSource.LOCAL_AI, result.source)
    }

    // --- Event: single extracted date ---

    @Test
    fun `single extracted date resolves to event shape with countdown`() = runTest {
        val result = subject.compose(
            OnDeviceTaskDslRequest(
                input = "Meeting at 14:30",
                intentResult = IntentResult(TaskType.EVENT, 0.44f),
                extractedEntities = ExtractedEntities(dateTimes = listOf(1_762_000_000_000L)),
                llmContext = LLMClassificationContext(
                    intentConfidence = 0.44f,
                    extractedDates = listOf(1_762_000_000_000L)
                )
            )
        )

        assertEquals(TaskType.EVENT, result.dsl.type)
        assertTrue(result.dsl.components.any { it.type == ComponentType.COUNTDOWN })
        assertTrue(result.dsl.components.any { it.type == ComponentType.NOTES })
    }

    // --- General: list structure adds checklist ---

    @Test
    fun `multi-line list input adds action checklist to general task`() = runTest {
        val listInput = "My plan:\n- Step one\n- Step two\n- Step three"
        val result = subject.compose(
            OnDeviceTaskDslRequest(
                input = listInput,
                intentResult = IntentResult(TaskType.GENERAL, 0.30f),
                extractedEntities = ExtractedEntities(),
                llmContext = LLMClassificationContext(intentConfidence = 0.30f)
            )
        )

        assertTrue(result.dsl.components.any { it.type == ComponentType.CHECKLIST })
        assertEquals(TaskGenerationSource.LOCAL_AI, result.source)
    }
}

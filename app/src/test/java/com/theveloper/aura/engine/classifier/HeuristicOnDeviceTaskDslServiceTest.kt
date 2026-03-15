package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicOnDeviceTaskDslServiceTest {

    private val subject = HeuristicOnDeviceTaskDslService()

    @Test
    fun `travel budget prompt builds richer travel ui locally`() = runTest {
        val result = subject.compose(
            OnDeviceTaskDslRequest(
                input = "Viajar a Madrid en agosto, controlar presupuesto y tener el itinerario",
                intentResult = IntentResult(TaskType.TRAVEL, 0.92f),
                extractedEntities = ExtractedEntities(
                    locations = listOf("Madrid")
                ),
                llmContext = LLMClassificationContext(
                    intentHint = TaskType.TRAVEL,
                    intentConfidence = 0.92f,
                    extractedLocations = listOf("Madrid")
                )
            )
        )

        assertEquals(TaskType.TRAVEL, result.dsl.type)
        assertTrue(result.dsl.components.count { it.type == ComponentType.NOTES } >= 1)
        assertTrue(result.dsl.components.any { it.type == ComponentType.DATA_FEED })
        assertTrue(result.dsl.components.any { component ->
            component.type == ComponentType.PROGRESS_BAR &&
                component.config["label"]?.jsonPrimitive?.contentOrNull == "Budget target"
        })
        assertEquals(TaskGenerationSource.LOCAL_AI, result.source)
    }

    @Test
    fun `medication prompt builds checklist and health notes locally`() = runTest {
        val result = subject.compose(
            OnDeviceTaskDslRequest(
                input = "Seguir la medicacion y registrar sintomas toda la semana",
                intentResult = IntentResult(TaskType.HEALTH, 0.74f),
                extractedEntities = ExtractedEntities(),
                llmContext = LLMClassificationContext(
                    intentHint = TaskType.HEALTH,
                    intentConfidence = 0.74f
                )
            )
        )

        assertEquals(TaskType.HEALTH, result.dsl.type)
        assertTrue(result.dsl.components.any { component ->
            component.type == ComponentType.CHECKLIST &&
                component.config["label"]?.jsonPrimitive?.contentOrNull == "Medication cycle"
        })
        assertTrue(result.dsl.components.any { component ->
            component.type == ComponentType.NOTES &&
                component.config["text"]?.jsonPrimitive?.contentOrNull?.contains("Health follow-up") == true
        })
    }

    @Test
    fun `fixed-date prompt resolves to event shape`() = runTest {
        val result = subject.compose(
            OnDeviceTaskDslRequest(
                input = "Reunion con el cliente el jueves a las 15",
                intentResult = IntentResult(TaskType.GENERAL, 0.44f),
                extractedEntities = ExtractedEntities(
                    dateTimes = listOf(1_762_000_000_000L)
                ),
                llmContext = LLMClassificationContext(
                    intentHint = TaskType.GENERAL,
                    intentConfidence = 0.44f,
                    extractedDates = listOf(1_762_000_000_000L)
                )
            )
        )

        assertEquals(TaskType.EVENT, result.dsl.type)
        assertTrue(result.dsl.components.any { it.type == ComponentType.COUNTDOWN })
        assertTrue(result.dsl.components.any { it.type == ComponentType.NOTES })
    }

    @Test
    fun `medium-term learning prompt resolves to goal shape`() = runTest {
        val result = subject.compose(
            OnDeviceTaskDslRequest(
                input = "Quiero terminar el curso de Kotlin y seguir mi progreso",
                intentResult = IntentResult(TaskType.GENERAL, 0.46f),
                extractedEntities = ExtractedEntities(),
                llmContext = LLMClassificationContext(
                    intentHint = TaskType.GENERAL,
                    intentConfidence = 0.46f
                )
            )
        )

        assertEquals(TaskType.GOAL, result.dsl.type)
        assertTrue(result.dsl.components.any { it.type == ComponentType.PROGRESS_BAR })
        assertTrue(result.dsl.components.any { it.type == ComponentType.CHECKLIST })
    }
}

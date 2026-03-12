package com.theveloper.aura.engine.llm

import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.dsl.TaskDSLValidator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class LLMParsingTest {

    @Test
    fun `extractLikelyJsonBlock keeps raw json object`() {
        val raw = """{"title":"Demo","type":"GENERAL"}"""

        assertEquals(raw, raw.extractLikelyJsonBlock())
    }

    @Test
    fun `extractLikelyJsonBlock strips markdown fences`() {
        val raw = """
            ```json
            {"title":"Demo","type":"GENERAL"}
            ```
        """.trimIndent()

        assertEquals("""{"title":"Demo","type":"GENERAL"}""", raw.extractLikelyJsonBlock())
    }

    @Test
    fun `extractLikelyJsonBlock recovers object from explanatory text`() {
        val raw = """Respuesta: {"title":"Demo","type":"GENERAL","components":[]} gracias"""

        assertEquals(
            """{"title":"Demo","type":"GENERAL","components":[]}""",
            raw.extractLikelyJsonBlock()
        )
    }

    @Test
    fun `normalizeTaskDslJson repairs near miss local model output`() {
        val raw = """
            ```json
            {
              "title": "Shopping List",
              "type": "GENERAL",
              "priority": 2,
              "targetDateMs": "2024-10-27",
              "components": [
                {
                  "type": "CHECKLIST",
                  "sortOrder": "0",
                  "config": {
                    "label": "Shopping List",
                    "allowAddItems": true
                  },
                  "populatedFromInput": "2024-10-27",
                  "needsClarification": true
                },
                {
                  "type": "NOTES",
                  "config_type": "NOTES",
                  "text": "need tomatoes",
                  "isMarkdown": true
                },
                {
                  "type": "CHECKLIST",
                  "sortOrder": "0",
                  "config": {
                    "label": "Shopping List",
                    "allowAddItems": true
                  },
                  "populatedFromInput": "2024-10-27",
                  "needsClarification": true
                }
              ],
              "reminders": [],
              "fetchers": []
            }
            ```
        """.trimIndent()

        val normalized = raw.normalizeTaskDslJson()
        val dsl = auraJson.decodeFromString<TaskDSLOutput>(normalized)

        assertEquals(TaskDSLValidator.ValidationResult.Valid, TaskDSLValidator.validate(dsl))
        assertEquals(listOf(ComponentType.CHECKLIST, ComponentType.NOTES), dsl.components.map { it.type })
        assertEquals(listOf(0, 1), dsl.components.map { it.sortOrder })
        assertEquals("CHECKLIST", dsl.components.first().config["config_type"]?.toString()?.trim('"'))
        assertEquals("NOTES", dsl.components.last().config["config_type"]?.toString()?.trim('"'))
        assertNotNull(dsl.targetDateMs)
        assertEquals(
            LocalDate.of(2024, 10, 27),
            Instant.ofEpochMilli(dsl.targetDateMs!!)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        )
    }

    @Test
    fun `stabilizeLocalClassification prunes noisy shopping ui extras`() {
        val dsl = TaskDSLOutput(
            title = "Shopping List",
            type = TaskType.GENERAL,
            components = listOf(
                component(ComponentType.CHECKLIST, 0),
                component(ComponentType.NOTES, 1),
                component(ComponentType.PROGRESS_BAR, 2),
                component(ComponentType.HABIT_RING, 3),
                component(ComponentType.METRIC_TRACKER, 4)
            )
        )

        val stabilized = dsl.stabilizeLocalClassification("shopping list for groceries")

        assertEquals(listOf(ComponentType.CHECKLIST, ComponentType.NOTES), stabilized.components.map { it.type })
        assertEquals(listOf(0, 1), stabilized.components.map { it.sortOrder })
    }

    private fun component(type: ComponentType, sortOrder: Int): ComponentDSL {
        return ComponentDSL(
            type = type,
            sortOrder = sortOrder,
            config = JsonObject(mapOf("config_type" to JsonPrimitive(type.name)))
        )
    }
}

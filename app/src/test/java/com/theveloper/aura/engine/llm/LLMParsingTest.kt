package com.theveloper.aura.engine.llm

import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.dsl.TaskDSLValidator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlinx.serialization.decodeFromString

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
}

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.theveloper.aura.engine.dsl.ChecklistDslItems

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
    fun `extractLikelyJsonBlock prefers repaired root object over inner array`() {
        val raw = """
            {
              "analysis": {
                "intent": "create roadmap",
                "constraints": ["need sources"],
                "skills_needed": ["notes"]
              },
              "task": {
                "title": "Kotlin roadmap",
                "type": "GOAL",
                "skills": [
                  {
                    "skill": "notes",
                    "config": {
                      "config_type": "NOTES",
                      "text": "hello"
                    }
                  }
                ]
        """.trimIndent()

        val extracted = raw.extractLikelyJsonBlock()

        assertEquals('{', extracted.first())
        val normalized = extracted.normalizeTaskDslJson()
        val dsl = auraJson.decodeFromString<TaskDSLOutput>(normalized)
        assertEquals(TaskType.GOAL, dsl.type)
        assertEquals(listOf(ComponentType.NOTES), dsl.components.map { it.type })
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

    @Test
    fun `normalizeTaskDslJson applies semantic items to checklist`() {
        val raw = """
            {
              "semantic": {
                "action": "buy groceries",
                "items": ["tomatoes", "cheese", "milk", "bread"],
                "subject": "Grocery Store"
              },
              "title": "Shopping List",
              "type": "GENERAL",
              "components": [
                {
                  "type": "CHECKLIST",
                  "sortOrder": 0,
                  "config": {
                    "config_type": "CHECKLIST",
                    "label": "Shopping List",
                    "allowAddItems": true,
                    "items": ["i want to buy tomatoes", "milk and cheese"]
                  }
                }
              ]
            }
        """.trimIndent()

        val normalized = raw.normalizeTaskDslJson()
        val dsl = auraJson.decodeFromString<TaskDSLOutput>(normalized)

        val checklist = dsl.components.first { it.type == ComponentType.CHECKLIST }
        val items = ChecklistDslItems.parse(checklist.config).map { it.label }

        assertEquals(listOf("tomatoes", "cheese", "milk", "bread"), items)
        assertEquals(true, checklist.populatedFromInput)
        assertFalse(checklist.needsClarification)
    }

    @Test
    fun `normalizeTaskDslJson discards semantic section from output`() {
        val raw = """
            {
              "semantic": {"action": "test", "items": [], "subject": ""},
              "title": "Test",
              "type": "GENERAL",
              "components": []
            }
        """.trimIndent()

        val normalized = raw.normalizeTaskDslJson()
        val jsonObject = auraJson.parseToJsonElement(normalized) as JsonObject

        assertNull(jsonObject["semantic"])
    }

    @Test
    fun `normalizeTaskDslJson gracefully handles absent semantic`() {
        val raw = """
            {
              "title": "No Semantic",
              "type": "GENERAL",
              "components": [
                {
                  "type": "CHECKLIST",
                  "sortOrder": 0,
                  "config": {
                    "config_type": "CHECKLIST",
                    "label": "Things",
                    "allowAddItems": true,
                    "items": ["item1", "item2"]
                  }
                }
              ]
            }
        """.trimIndent()

        val normalized = raw.normalizeTaskDslJson()
        val dsl = auraJson.decodeFromString<TaskDSLOutput>(normalized)

        val items = ChecklistDslItems.parse(dsl.components.first().config).map { it.label }
        assertEquals(listOf("item1", "item2"), items)
        assertFalse(dsl.components.first().populatedFromInput)
    }

    @Test
    fun `normalizeTaskDslJson bridges ui skills schema into legacy components`() {
        val raw = """
            {
              "analysis": {
                "intent": "plan a trip",
                "constraints": ["budget"],
                "skills_needed": ["countdown", "checklist", "notes"]
              },
              "semantic": {
                "action": "travel",
                "items": ["passport", "tickets"],
                "subject": "Madrid",
                "goal": "",
                "frequency": ""
              },
              "task": {
                "title": "Trip to Madrid",
                "type": "TRAVEL",
                "priority": 1,
                "targetDateMs": 1760000000000,
                "skills": [
                  {
                    "skill": "countdown",
                    "sortOrder": 0,
                    "config": {
                      "label": "Trip date",
                      "targetDate": 1760000000000
                    }
                  },
                  {
                    "skill": "checklist",
                    "sortOrder": 1,
                    "config": {
                      "label": "Packing",
                      "allowAddItems": true,
                      "items": []
                    }
                  },
                  {
                    "skill": "notes",
                    "sortOrder": 2,
                    "config": {
                      "text": "## Travel notes\n- Book hotel",
                      "isMarkdown": true
                    },
                    "populatedFromInput": true
                  }
                ],
                "reminders": [],
                "fetchers": []
              }
            }
        """.trimIndent()

        val normalized = raw.normalizeTaskDslJson()
        val dsl = auraJson.decodeFromString<TaskDSLOutput>(normalized)

        assertEquals(TaskDSLValidator.ValidationResult.Valid, TaskDSLValidator.validate(dsl))
        assertEquals(listOf(ComponentType.COUNTDOWN, ComponentType.CHECKLIST, ComponentType.NOTES), dsl.components.map { it.type })
        assertEquals("countdown", dsl.components[0].skillId)
        assertEquals("checklist", dsl.components[1].skillId)
        assertEquals("notes", dsl.components[2].skillId)
        val checklistItems = ChecklistDslItems.parse(dsl.components[1].config).map { it.label }
        assertEquals(listOf("passport", "tickets"), checklistItems)
        assertEquals(true, dsl.components[1].populatedFromInput)
    }

    @Test
    fun `normalizeTaskDslJson does not turn abstract roadmap semantics into visible checklist items`() {
        val raw = """
            {
              "semantic": {
                "action": "learn programming",
                "items": ["Kotlin programming", "roadmap", "guide", "references"],
                "subject": "Kotlin programming",
                "goal": "roadmap and guide",
                "frequency": ""
              },
              "task": {
                "title": "Roadmap and Guide for Kotlin Programming",
                "type": "GOAL",
                "skills": [
                  {
                    "skill": "notes",
                    "config": {}
                  },
                  {
                    "skill": "checklist",
                    "config": {}
                  }
                ]
              }
            }
        """.trimIndent()

        val normalized = raw.normalizeTaskDslJson()
        val dsl = auraJson.decodeFromString<TaskDSLOutput>(normalized)

        val notes = dsl.components.first { it.type == ComponentType.NOTES }
        val checklist = dsl.components.first { it.type == ComponentType.CHECKLIST }

        assertEquals("", notes.config["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
        assertTrue(ChecklistDslItems.parse(checklist.config).isEmpty())
    }

    @Test
    fun `normalizeTaskDslJson uses semantic subject as checklist label`() {
        val raw = """
            {
              "semantic": {
                "action": "buy food",
                "items": ["rice", "beans"],
                "subject": "Weekly Groceries"
              },
              "title": "Shopping",
              "type": "GENERAL",
              "components": [
                {
                  "type": "CHECKLIST",
                  "sortOrder": 0,
                  "config": {
                    "config_type": "CHECKLIST",
                    "label": "Checklist",
                    "allowAddItems": true
                  }
                }
              ]
            }
        """.trimIndent()

        val normalized = raw.normalizeTaskDslJson()
        val dsl = auraJson.decodeFromString<TaskDSLOutput>(normalized)

        val config = dsl.components.first().config
        assertEquals("Weekly Groceries", config["label"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `normalizeTaskDslJson parses goal and frequency from semantic and discards from output`() {
        val raw = """
            {
              "semantic": {
                "action": "hacer rutina",
                "items": ["sentadillas", "flexiones", "burpees"],
                "subject": "gym",
                "goal": "perder grasa",
                "frequency": "rutina"
              },
              "title": "Gym Routine",
              "type": "HEALTH",
              "components": [
                {
                  "type": "CHECKLIST",
                  "sortOrder": 0,
                  "config": {
                    "config_type": "CHECKLIST",
                    "label": "Ejercicios",
                    "allowAddItems": true,
                    "items": []
                  }
                }
              ]
            }
        """.trimIndent()

        val normalized = raw.normalizeTaskDslJson()
        val jsonObject = auraJson.parseToJsonElement(normalized) as JsonObject

        // Semantic section must not leak into persisted output
        assertNull(jsonObject["semantic"])

        // Items from semantic.items should populate the checklist
        val dsl = auraJson.decodeFromString<TaskDSLOutput>(normalized)
        val items = ChecklistDslItems.parse(dsl.components.first().config).map { it.label }
        assertEquals(listOf("sentadillas", "flexiones", "burpees"), items)
    }

    @Test
    fun `extractLikelyJsonBlock recovers truncated json from small model`() {
        val truncated = """{"title":"Gym workout","type":"HEALTH","priority":0,"targetDateMs":0,"components":[{"type":"CHECKLIST","sortOrder":0,"config":{"config_type":"CHECKLIST","label":"Exercises","allowAddItems":true,"items":[{"label":"crunches","isSuggested":true},{"label":"planks","isSuggested":true}]},"populatedFromInput":true,"needsClarification":false},{"type":"NOTES","sortOrder":1,"config":{"config_type":"NOTES","text":"## Tips","is"""

        val recovered = truncated.extractLikelyJsonBlock()

        // Should recover a parseable JSON object
        val parsed = auraJson.parseToJsonElement(recovered) as? JsonObject
        assertNotNull(parsed)
        assertEquals("Gym workout", parsed!!["title"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `extractLikelyJsonBlock returns raw when no json present`() {
        val noJson = "This is just plain text with no JSON"
        assertEquals(noJson, noJson.extractLikelyJsonBlock())
    }

    private fun component(type: ComponentType, sortOrder: Int): ComponentDSL {
        return ComponentDSL(
            type = type,
            sortOrder = sortOrder,
            config = JsonObject(mapOf("config_type" to JsonPrimitive(type.name)))
        )
    }
}

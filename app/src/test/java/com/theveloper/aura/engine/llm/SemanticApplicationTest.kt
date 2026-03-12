package com.theveloper.aura.engine.llm

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.engine.dsl.SemanticFrame
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticApplicationTest {

    @Test
    fun `replaces empty checklist items with semantic items`() {
        val components = JsonArray(
            listOf(
                checklistComponent(label = "Shopping List", items = emptyList())
            )
        )
        val frame = SemanticFrame(
            action = "buy groceries",
            items = listOf("tomatoes", "cheese", "milk"),
            subject = "Grocery Store"
        )

        val result = applySemanticToComponents(components, frame)

        val checklist = result.first() as JsonObject
        val config = checklist["config"] as JsonObject
        val items = (config["items"] as JsonArray).map {
            (it as JsonObject)["label"]?.jsonPrimitive?.contentOrNull
        }

        assertEquals(listOf("tomatoes", "cheese", "milk"), items)
        assertEquals("true", checklist["populatedFromInput"]?.jsonPrimitive?.contentOrNull)
        assertEquals("false", checklist["needsClarification"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `replaces noisy checklist items with semantic items`() {
        val components = JsonArray(
            listOf(
                checklistComponent(
                    label = "Shopping List",
                    items = listOf(
                        "i want to buy tomatoes from the store",
                        "milk and cheese for the week"
                    )
                )
            )
        )
        val frame = SemanticFrame(
            action = "buy groceries",
            items = listOf("tomatoes", "cheese", "milk"),
            subject = "Grocery Store"
        )

        val result = applySemanticToComponents(components, frame)

        val config = (result.first() as JsonObject)["config"] as JsonObject
        val items = (config["items"] as JsonArray).map {
            (it as JsonObject)["label"]?.jsonPrimitive?.contentOrNull
        }

        assertEquals(listOf("tomatoes", "cheese", "milk"), items)
    }

    @Test
    fun `uses subject as checklist label`() {
        val components = JsonArray(
            listOf(
                checklistComponent(label = "Checklist", items = emptyList())
            )
        )
        val frame = SemanticFrame(
            action = "buy",
            items = listOf("bread"),
            subject = "Grocery Store"
        )

        val result = applySemanticToComponents(components, frame)

        val config = (result.first() as JsonObject)["config"] as JsonObject
        assertEquals("Grocery Store", config["label"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `preserves non-CHECKLIST components untouched`() {
        val notesConfig = JsonObject(
            mapOf(
                "config_type" to JsonPrimitive("NOTES"),
                "text" to JsonPrimitive("some notes"),
                "isMarkdown" to JsonPrimitive(true)
            )
        )
        val components = JsonArray(
            listOf(
                JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("NOTES"),
                        "sortOrder" to JsonPrimitive(0),
                        "config" to notesConfig,
                        "populatedFromInput" to JsonPrimitive(false),
                        "needsClarification" to JsonPrimitive(false)
                    )
                )
            )
        )
        val frame = SemanticFrame(
            action = "buy",
            items = listOf("bread", "milk"),
            subject = "store"
        )

        val result = applySemanticToComponents(components, frame)

        val notes = result.first() as JsonObject
        assertEquals("NOTES", notes["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals("false", notes["populatedFromInput"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `is no-op when SemanticFrame is EMPTY`() {
        val components = JsonArray(
            listOf(
                checklistComponent(label = "Test", items = listOf("existing item"))
            )
        )

        val result = applySemanticToComponents(components, SemanticFrame.EMPTY)

        // Should be the exact same reference
        assertEquals(components, result)
    }

    @Test
    fun `is no-op when semantic has no items`() {
        val components = JsonArray(
            listOf(
                checklistComponent(label = "Test", items = listOf("existing item"))
            )
        )
        val frame = SemanticFrame(
            action = "relax",
            items = emptyList(),
            subject = "home"
        )

        val result = applySemanticToComponents(components, frame)

        assertEquals(components, result)
    }

    @Test
    fun `handles multiple CHECKLIST components`() {
        val components = JsonArray(
            listOf(
                checklistComponent(label = "List 1", items = emptyList()),
                checklistComponent(label = "List 2", items = emptyList())
            )
        )
        val frame = SemanticFrame(
            action = "prepare",
            items = listOf("item A", "item B"),
            subject = "event"
        )

        val result = applySemanticToComponents(components, frame)

        // Both checklists should get semantic items
        result.forEach { element ->
            val config = (element as JsonObject)["config"] as JsonObject
            val items = (config["items"] as JsonArray).map {
                (it as JsonObject)["label"]?.jsonPrimitive?.contentOrNull
            }
            assertEquals(listOf("item A", "item B"), items)
        }
    }

    private fun checklistComponent(
        label: String,
        items: List<String>
    ): JsonObject {
        val itemsArray = if (items.isNotEmpty()) {
            JsonArray(
                items.map { item ->
                    JsonObject(
                        mapOf(
                            "label" to JsonPrimitive(item),
                            "isSuggested" to JsonPrimitive(false)
                        )
                    )
                }
            )
        } else {
            JsonArray(emptyList())
        }

        val config = JsonObject(
            mapOf(
                "config_type" to JsonPrimitive("CHECKLIST"),
                "label" to JsonPrimitive(label),
                "allowAddItems" to JsonPrimitive(true),
                "items" to itemsArray
            )
        )

        return JsonObject(
            mapOf(
                "type" to JsonPrimitive(ComponentType.CHECKLIST.name),
                "sortOrder" to JsonPrimitive(0),
                "config" to config,
                "populatedFromInput" to JsonPrimitive(false),
                "needsClarification" to JsonPrimitive(false)
            )
        )
    }
}

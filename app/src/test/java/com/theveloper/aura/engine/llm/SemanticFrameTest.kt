package com.theveloper.aura.engine.llm

import com.theveloper.aura.engine.dsl.SemanticFrame
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticFrameTest {

    @Test
    fun `extractSemanticFrame returns EMPTY when no semantic key`() {
        val root = JsonObject(mapOf("title" to JsonPrimitive("Test")))

        assertEquals(SemanticFrame.EMPTY, extractSemanticFrame(root))
    }

    @Test
    fun `extractSemanticFrame parses well-formed semantic`() {
        val root = JsonObject(
            mapOf(
                "semantic" to JsonObject(
                    mapOf(
                        "action" to JsonPrimitive("buy groceries"),
                        "items" to JsonArray(
                            listOf(
                                JsonPrimitive("tomatoes"),
                                JsonPrimitive("cheese"),
                                JsonPrimitive("milk"),
                                JsonPrimitive("bread")
                            )
                        ),
                        "subject" to JsonPrimitive("Grocery Store")
                    )
                )
            )
        )

        val frame = extractSemanticFrame(root)

        assertEquals("buy groceries", frame.action)
        assertEquals(listOf("tomatoes", "cheese", "milk", "bread"), frame.items)
        assertEquals("Grocery Store", frame.subject)
    }

    @Test
    fun `extractSemanticFrame handles missing fields gracefully`() {
        val root = JsonObject(
            mapOf(
                "semantic" to JsonObject(
                    mapOf("action" to JsonPrimitive("study"))
                )
            )
        )

        val frame = extractSemanticFrame(root)

        assertEquals("study", frame.action)
        assertEquals(emptyList<String>(), frame.items)
        assertEquals("", frame.subject)
    }

    @Test
    fun `extractSemanticFrame deduplicates items`() {
        val root = JsonObject(
            mapOf(
                "semantic" to JsonObject(
                    mapOf(
                        "action" to JsonPrimitive("buy"),
                        "items" to JsonArray(
                            listOf(
                                JsonPrimitive("milk"),
                                JsonPrimitive("milk"),
                                JsonPrimitive("bread")
                            )
                        ),
                        "subject" to JsonPrimitive("")
                    )
                )
            )
        )

        val frame = extractSemanticFrame(root)

        assertEquals(listOf("milk", "bread"), frame.items)
    }

    @Test
    fun `extractSemanticFrame filters excessively long items`() {
        val longItem = "a".repeat(100)
        val root = JsonObject(
            mapOf(
                "semantic" to JsonObject(
                    mapOf(
                        "action" to JsonPrimitive("test"),
                        "items" to JsonArray(
                            listOf(
                                JsonPrimitive(longItem),
                                JsonPrimitive("milk")
                            )
                        ),
                        "subject" to JsonPrimitive("")
                    )
                )
            )
        )

        val frame = extractSemanticFrame(root)

        assertEquals(listOf("milk"), frame.items)
    }

    @Test
    fun `extractSemanticFrame filters blank items`() {
        val root = JsonObject(
            mapOf(
                "semantic" to JsonObject(
                    mapOf(
                        "action" to JsonPrimitive("buy"),
                        "items" to JsonArray(
                            listOf(
                                JsonPrimitive(""),
                                JsonPrimitive("   "),
                                JsonPrimitive("milk")
                            )
                        ),
                        "subject" to JsonPrimitive("")
                    )
                )
            )
        )

        val frame = extractSemanticFrame(root)

        assertEquals(listOf("milk"), frame.items)
    }

    @Test
    fun `extractSemanticFrame returns EMPTY when all fields are blank`() {
        val root = JsonObject(
            mapOf(
                "semantic" to JsonObject(
                    mapOf(
                        "action" to JsonPrimitive(""),
                        "items" to JsonArray(emptyList()),
                        "subject" to JsonPrimitive("")
                    )
                )
            )
        )

        assertEquals(SemanticFrame.EMPTY, extractSemanticFrame(root))
    }
}

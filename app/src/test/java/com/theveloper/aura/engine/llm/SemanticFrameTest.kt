package com.theveloper.aura.engine.llm

import com.theveloper.aura.engine.dsl.SemanticFrame
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `extractSemanticFrame parses goal and frequency`() {
        val root = JsonObject(
            mapOf(
                "semantic" to JsonObject(
                    mapOf(
                        "action" to JsonPrimitive("hacer rutina"),
                        "items" to JsonArray(
                            listOf(
                                JsonPrimitive("sentadillas"),
                                JsonPrimitive("flexiones"),
                                JsonPrimitive("burpees")
                            )
                        ),
                        "subject" to JsonPrimitive("gym"),
                        "goal" to JsonPrimitive("perder grasa"),
                        "frequency" to JsonPrimitive("rutina")
                    )
                )
            )
        )

        val frame = extractSemanticFrame(root)

        assertEquals("perder grasa", frame.goal)
        assertEquals("rutina", frame.frequency)
        assertTrue(frame.hasGoal)
        assertTrue(frame.hasFrequency)
    }

    @Test
    fun `extractSemanticFrame returns EMPTY when only goal and frequency are blank`() {
        val root = JsonObject(
            mapOf(
                "semantic" to JsonObject(
                    mapOf(
                        "action" to JsonPrimitive(""),
                        "items" to JsonArray(emptyList()),
                        "subject" to JsonPrimitive(""),
                        "goal" to JsonPrimitive(""),
                        "frequency" to JsonPrimitive("")
                    )
                )
            )
        )

        assertEquals(SemanticFrame.EMPTY, extractSemanticFrame(root))
    }

    @Test
    fun `extractSemanticFrame handles missing goal and frequency gracefully`() {
        val root = JsonObject(
            mapOf(
                "semantic" to JsonObject(
                    mapOf(
                        "action" to JsonPrimitive("study"),
                        "items" to JsonArray(emptyList()),
                        "subject" to JsonPrimitive("math")
                    )
                )
            )
        )

        val frame = extractSemanticFrame(root)

        assertEquals("", frame.goal)
        assertEquals("", frame.frequency)
        assertFalse(frame.hasGoal)
        assertFalse(frame.hasFrequency)
    }

    @Test
    fun `SemanticFrame EMPTY has blank goal and frequency`() {
        assertEquals("", SemanticFrame.EMPTY.goal)
        assertEquals("", SemanticFrame.EMPTY.frequency)
        assertFalse(SemanticFrame.EMPTY.hasGoal)
        assertFalse(SemanticFrame.EMPTY.hasFrequency)
    }

    @Test
    fun `extractSemanticFrame not EMPTY when only goal is present`() {
        val root = JsonObject(
            mapOf(
                "semantic" to JsonObject(
                    mapOf(
                        "action" to JsonPrimitive(""),
                        "items" to JsonArray(emptyList()),
                        "subject" to JsonPrimitive(""),
                        "goal" to JsonPrimitive("lose 5kg"),
                        "frequency" to JsonPrimitive("")
                    )
                )
            )
        )

        val frame = extractSemanticFrame(root)

        assertTrue(frame.hasGoal)
        assertFalse(frame == SemanticFrame.EMPTY)
        assertEquals("lose 5kg", frame.goal)
    }
}

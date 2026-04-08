package com.theveloper.aura.engine.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistContentQualityTest {

    @Test
    fun `sanitizeItems removes malformed repeated checklist fragments and umbrella labels`() {
        val sanitized = ChecklistContentQuality.sanitizeItems(
            items = listOf(
                "Flour",
                "Sugar",
                "Eggs",
                "Milk",
                "Vanilla Extract",
                "Fl Flan Mold",
                "Caramel Ingredients",
                "Butter",
                "Fl Fl Flan Mold",
                "Fl Mold",
                "Mold",
                "Fl Mold Mold Mold Mold",
                "Fl Flan"
            ),
            taskTitle = "Argentinian Flan Shopping List and Recipe"
        )

        assertEquals(
            listOf("Flour", "Sugar", "Eggs", "Milk", "Vanilla Extract", "Flan Mold", "Butter"),
            sanitized
        )
    }

    @Test
    fun `itemsLookUsable rejects abstract checklist rows even when non empty`() {
        assertFalse(
            ChecklistContentQuality.itemsLookUsable(
                items = listOf("Flan Ingredients", "Recipe Steps", "Shopping List"),
                taskTitle = "Argentinian Flan Shopping List and Recipe"
            )
        )
        assertTrue(
            ChecklistContentQuality.itemsLookUsable(
                items = listOf("Eggs", "Milk", "Sugar", "Vanilla extract"),
                taskTitle = "Argentinian Flan Shopping List and Recipe"
            )
        )
    }
}

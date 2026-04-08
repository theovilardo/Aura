package com.theveloper.aura.engine.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftContentQualityTest {

    @Test
    fun `sanitizeGeneratedText collapses excessive duplicate bullet lines`() {
        val sanitized = DraftContentQuality.sanitizeGeneratedText(
            """
            ## Ingredients
            - 100 grams cinnamon
            - 100 grams cinnamon
            - 100 grams cinnamon
            - 10 eggs
            - 10 eggs
            """.trimIndent()
        )

        assertEquals(1, sanitized.lineSequence().count { it.contains("100 grams cinnamon") })
        assertEquals(1, sanitized.lineSequence().count { it.contains("10 eggs") })
    }

    @Test
    fun `sanitizeGeneratedText removes repeated short headings without blanking the note`() {
        val sanitized = DraftContentQuality.sanitizeGeneratedText(
            """
            Ingredients:

            Ingredients:
            Mix everything well.
            Mix everything well.
            Chill before serving.
            """.trimIndent()
        )

        assertEquals(1, sanitized.lineSequence().count { it.trim() == "Ingredients:" })
        assertEquals(1, sanitized.lineSequence().count { it.trim() == "Mix everything well." })
        assertTrue(sanitized.contains("Chill before serving."))
    }
}

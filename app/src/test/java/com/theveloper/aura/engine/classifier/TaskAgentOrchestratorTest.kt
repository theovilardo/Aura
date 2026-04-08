package com.theveloper.aura.engine.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TaskAgentOrchestratorTest {

    @Test
    fun `parseTaskAgentPlan coerces singleton skill strings into arrays`() {
        val raw = """
            {
              "title": "Chocolate Pudding Recipe",
              "type": "GENERAL",
              "semantic": {
                "action": "find",
                "items": ["chocolate pudding recipe"],
                "subject": "cooking",
                "goal": "a chocolate pudding recipe",
                "frequency": ""
              },
              "uiSkills": "notes",
              "functionSkills": "structured-brief"
            }
        """.trimIndent()

        val plan = parseTaskAgentPlan(raw)

        assertNotNull(plan)
        assertEquals(listOf("notes"), plan?.uiSkills)
        assertEquals(listOf("structured-brief"), plan?.functionSkills)
        assertEquals("Chocolate Pudding Recipe", plan?.title)
    }
}

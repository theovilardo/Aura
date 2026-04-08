package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.ChecklistDslItems
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.llm.LLMService
import com.theveloper.aura.engine.llm.LLMTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `parseTaskAgentPlan accepts compact local planner schema without semantic`() {
        val raw = """
            {
              "title": "Argentinian Flan Shopping List and Recipe",
              "type": "GENERAL",
              "uiSkills": ["checklist", "notes"],
              "functionSkills": []
            }
        """.trimIndent()

        val plan = parseTaskAgentPlan(raw)

        assertNotNull(plan)
        assertEquals(listOf("checklist", "notes"), plan?.uiSkills)
        assertEquals(emptyList<String>(), plan?.functionSkills)
        assertEquals("Argentinian Flan Shopping List and Recipe", plan?.title)
    }

    @Test
    fun `orchestrate local fills checklist and notes separately for mixed artifact tasks`() = kotlinx.coroutines.test.runTest {
        val subject = TaskAgentOrchestrator()
        val service = object : LLMService {
            override val tier = LLMTier.GEMMA_4_E2B

            override fun isAvailable() = true

            override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
                error("unused")
            }

            override suspend fun complete(prompt: String): String {
                return when {
                    prompt.contains("task orchestration agent") -> """
                        {
                          "title": "Argentinian Flan Shopping List and Recipe",
                          "type": "GENERAL",
                          "uiSkills": ["checklist", "notes"],
                          "functionSkills": []
                        }
                    """.trimIndent()

                    prompt.contains("checklist skill filler") -> """
                        Huevos
                        Leche
                        Azucar
                        Esencia de vainilla
                    """.trimIndent()

                    prompt.contains("notes skill filler") -> """
                        ## Receta
                        1. Hacer un caramelo con azucar y cubrir la flanera.
                        2. Mezclar huevos, leche, azucar y vainilla.
                        3. Cocinar a bano maria hasta que cuaje.
                        4. Enfriar y desmoldar.
                    """.trimIndent()

                    else -> error("Unexpected prompt: $prompt")
                }
            }
        }

        val dsl = subject.orchestrate(
            service = service,
            input = "I need a shopping list + recipe for an Argentinian flan",
            llmContext = LLMClassificationContext(detectedTaskType = TaskType.GENERAL)
        )

        assertNotNull(dsl)
        assertEquals(listOf(ComponentType.CHECKLIST, ComponentType.NOTES), dsl!!.components.map { it.type })
        assertEquals(
            listOf("Huevos", "Leche", "Azucar", "Esencia de vainilla"),
            ChecklistDslItems.parse(dsl.components.first().config).map { it.label }
        )
        assertTrue(dsl.components.last().config["text"].toString().contains("bano maria"))
    }

    @Test
    fun `orchestrate local retries checklist extraction from notes when initial checklist output is noisy`() = kotlinx.coroutines.test.runTest {
        val subject = TaskAgentOrchestrator()
        var checklistCallCount = 0
        val service = object : LLMService {
            override val tier = LLMTier.GEMMA_4_E2B

            override fun isAvailable() = true

            override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
                error("unused")
            }

            override suspend fun complete(prompt: String): String {
                return when {
                    prompt.contains("task orchestration agent") -> """
                        {
                          "title": "Argentinian Flan Shopping List and Recipe",
                          "type": "GENERAL",
                          "uiSkills": ["checklist", "notes"],
                          "functionSkills": []
                        }
                    """.trimIndent()

                    prompt.contains("notes skill filler") -> """
                        ## Recipe
                        1. Make caramel with sugar.
                        2. Mix eggs, milk, sugar, and vanilla.
                        3. Bake in a water bath until set.
                    """.trimIndent()

                    prompt.contains("checklist skill filler") -> {
                        checklistCallCount++
                        """
                            Fl Flan Mix or Ingredients
                            Flan Ingredients
                            false
                        """.trimIndent()
                    }

                    prompt.contains("checklist extraction agent") -> """
                        Eggs
                        Milk
                        Sugar
                        Vanilla extract
                    """.trimIndent()

                    else -> error("Unexpected prompt: $prompt")
                }
            }
        }

        val dsl = subject.orchestrate(
            service = service,
            input = "I need a shopping list + recipe for making an Argentinian flan",
            llmContext = LLMClassificationContext(detectedTaskType = TaskType.GENERAL)
        )

        assertNotNull(dsl)
        assertEquals(1, checklistCallCount)
        assertEquals(
            listOf("Eggs", "Milk", "Sugar", "Vanilla extract"),
            ChecklistDslItems.parse(dsl!!.components.first { it.type == ComponentType.CHECKLIST }.config).map { it.label }
        )
    }
}

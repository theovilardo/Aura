package com.theveloper.aura.engine.classifier

import android.content.Context
import android.content.res.AssetManager
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.ChecklistDslItems
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.llm.LLMService
import com.theveloper.aura.engine.llm.LLMTier
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDraftRescueServiceTest {

    private val assetManager = mockk<AssetManager>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val subject: TaskDraftRescueService

    init {
        every { context.assets } returns assetManager
        every { assetManager.open(any()) } answers {
            ByteArrayInputStream("placeholder".toByteArray())
        }
        subject = TaskDraftRescueService(context)
    }

    @Test
    fun `needsRescue flags teaser notes as shallow draft`() {
        val draft = TaskDSLOutput(
            title = "Chocolate pudding recipe",
            type = TaskType.GENERAL,
            components = listOf(
                notesComponent("Here is a chocolate pudding recipe. Please wait for the full recipe content.")
            )
        )

        assertTrue(subject.needsRescue(draft))
    }

    @Test
    fun `needsRescue accepts structured markdown notes`() {
        val draft = TaskDSLOutput(
            title = "Chocolate pudding recipe",
            type = TaskType.GENERAL,
            components = listOf(
                notesComponent(
                    """
                    ## Steps
                    1. Heat the milk with cocoa.
                    2. Whisk in sugar and cornstarch.
                    3. Chill until set.
                    """.trimIndent()
                )
            )
        )

        assertFalse(subject.needsRescue(draft))
    }

    @Test
    fun `needsRescue flags structured placeholder notes as shallow draft`() {
        val draft = TaskDSLOutput(
            title = "Chocolate pudding recipe",
            type = TaskType.GENERAL,
            components = listOf(
                notesComponent(
                    """
                    # Chocolate Pudding Recipe Guide

                    ## Recipe Steps
                    - [To be filled with the actual recipe steps found.]
                    - [To be filled with any specific cooking tips found.]
                    """.trimIndent()
                )
            )
        )

        assertTrue(subject.needsRescue(draft))
    }

    @Test
    fun `needsRescue flags shopping list draft without checklist component`() {
        val draft = TaskDSLOutput(
            title = "Argentinian Flan Shopping List and Recipe",
            type = TaskType.GENERAL,
            components = listOf(
                notesComponent(
                    """
                    ## Flan argentino
                    1. Hacer el caramelo.
                    2. Batir los huevos con leche y azucar.
                    3. Cocinar a bano maria.
                    """.trimIndent()
                )
            )
        )

        assertTrue(subject.needsRescue(draft))
    }

    @Test
    fun `rescue repairs hollow notes with markdown-only fallback`() = kotlinx.coroutines.test.runTest {
        val service = object : LLMService {
            override val tier = LLMTier.GEMMA_4_E4B
            override fun isAvailable() = true
            override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
                error("unused")
            }
            override suspend fun complete(prompt: String): String {
                return when {
                    prompt.contains("notes content repair agent") -> """
                        ## Chocolate pudding
                        **Ingredients**
                        - Milk
                        - Cocoa powder
                        - Sugar

                        **Steps**
                        1. Heat the milk.
                        2. Whisk in cocoa and sugar.
                        3. Chill until set.
                    """.trimIndent()
                    else -> ""
                }
            }
        }
        val draft = TaskDSLOutput(
            title = "Chocolate pudding recipe",
            type = TaskType.GENERAL,
            components = listOf(
                notesComponent(""),
                checklistComponent()
            )
        )

        val rescued = subject.rescue(
            service = service,
            input = "I need a chocolate pudding recipe",
            llmContext = LLMClassificationContext(detectedTaskType = TaskType.GENERAL),
            draft = draft
        )

        assertNotNull(rescued)
        assertFalse(subject.needsRescue(rescued!!))
        assertTrue(
            rescued.components.first().config["text"]?.toString()?.contains("Chocolate pudding") == true
        )
    }

    @Test
    fun `rescue can synthesize notes when the draft has no components`() = kotlinx.coroutines.test.runTest {
        val service = object : LLMService {
            override val tier = LLMTier.GEMMA_4_E4B
            override fun isAvailable() = true
            override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
                error("unused")
            }
            override suspend fun complete(prompt: String): String {
                return when {
                    prompt.contains("notes content repair agent") -> """
                        ## Chocolate pudding
                        **Ingredients**
                        - Milk
                        - Cocoa powder
                        - Sugar

                        **Steps**
                        1. Heat the milk.
                        2. Whisk in cocoa and sugar.
                        3. Chill until set.
                    """.trimIndent()
                    else -> ""
                }
            }
        }
        val draft = TaskDSLOutput(
            title = "Untitled task",
            type = TaskType.GENERAL,
            components = emptyList()
        )

        val rescued = subject.rescue(
            service = service,
            input = "I need a chocolate pudding recipe",
            llmContext = LLMClassificationContext(detectedTaskType = TaskType.GENERAL),
            draft = draft
        )

        assertNotNull(rescued)
        assertTrue(rescued!!.components.any { it.type == ComponentType.NOTES })
        assertFalse(subject.needsRescue(rescued))
    }

    @Test
    fun `rescue repairs hollow checklist with concrete item-only fallback`() = kotlinx.coroutines.test.runTest {
        val service = object : LLMService {
            override val tier = LLMTier.GEMMA_4_E4B
            override fun isAvailable() = true
            override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
                error("unused")
            }
            override suspend fun complete(prompt: String): String {
                return when {
                    prompt.contains("checklist item repair agent") -> """
                        Cranberries
                        Chocolate
                        Milk
                        Cheese
                        Mascarpone cheese
                        Eggs
                        Sugar
                        Coffee
                        Cocoa powder
                        Ladyfingers
                    """.trimIndent()
                    else -> ""
                }
            }
        }
        val draft = TaskDSLOutput(
            title = "Tiramisu Shopping List",
            type = TaskType.GENERAL,
            components = listOf(checklistComponent())
        )

        val rescued = subject.rescue(
            service = service,
            input = "I need a shopping list for cranberries, chocolate, milk, cheese and all the ingredients for a tiramisu as well",
            llmContext = LLMClassificationContext(detectedTaskType = TaskType.GENERAL),
            draft = draft
        )

        assertNotNull(rescued)
        assertFalse(subject.needsRescue(rescued!!))
        val checklist = rescued.components.first { it.type == ComponentType.CHECKLIST }
        val items = ChecklistDslItems.parse(checklist.config).map { it.label }
        assertTrue(items.contains("Cranberries"))
        assertTrue(items.contains("Chocolate"))
        assertTrue(items.contains("Mascarpone cheese"))
        assertTrue(items.contains("Ladyfingers"))
    }

    @Test
    fun `rescue sanitizes malformed repeated checklist repair output`() = kotlinx.coroutines.test.runTest {
        val service = object : LLMService {
            override val tier = LLMTier.GEMMA_4_E4B
            override fun isAvailable() = true
            override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
                error("unused")
            }
            override suspend fun complete(prompt: String): String {
                return when {
                    prompt.contains("checklist item repair agent") -> """
                        Flour
                        Sugar
                        Eggs
                        Milk
                        Vanilla Extract
                        Fl Flan Mold
                        Caramel Ingredients
                        Butter
                        Fl Fl Flan Mold
                        Fl Mold
                        Mold
                        Fl Mold Mold Mold Mold
                        Fl Flan
                    """.trimIndent()
                    else -> ""
                }
            }
        }
        val draft = TaskDSLOutput(
            title = "Argentinian Flan Shopping List and Recipe",
            type = TaskType.GENERAL,
            components = listOf(checklistComponent())
        )

        val rescued = subject.rescue(
            service = service,
            input = "I need a shopping list + recipe for making an Argentinian flan",
            llmContext = LLMClassificationContext(detectedTaskType = TaskType.GENERAL),
            draft = draft
        )

        assertNotNull(rescued)
        val checklist = rescued!!.components.first { it.type == ComponentType.CHECKLIST }
        assertEquals(
            listOf("Flour", "Sugar", "Eggs", "Milk", "Vanilla Extract", "Flan Mold", "Butter"),
            ChecklistDslItems.parse(checklist.config).map { it.label }
        )
    }

    @Test
    fun `rescue expands umbrella checklist item into atomic items`() = kotlinx.coroutines.test.runTest {
        val service = object : LLMService {
            override val tier = LLMTier.GEMMA_4_E4B
            override fun isAvailable() = true
            override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
                error("unused")
            }
            override suspend fun complete(prompt: String): String {
                return when {
                    prompt.contains("checklist refinement agent") -> """
                        Cranberries
                        Chocolate
                        Milk
                        Cheese
                        Mascarpone cheese
                        Eggs
                        Sugar
                        Coffee
                        Cocoa powder
                        Ladyfingers
                    """.trimIndent()
                    else -> ""
                }
            }
        }
        val draft = TaskDSLOutput(
            title = "Tiramisu Shopping List",
            type = TaskType.GENERAL,
            components = listOf(
                checklistComponent(
                    items = listOf(
                        "Cranberries",
                        "Chocolate",
                        "Milk",
                        "Cheese",
                        "Tiramisu ingredients"
                    )
                )
            )
        )

        val rescued = subject.rescue(
            service = service,
            input = "I need a shopping list for cranberries, chocolate, milk, cheese and all the ingredients for a tiramisu as well",
            llmContext = LLMClassificationContext(detectedTaskType = TaskType.GENERAL),
            draft = draft
        )

        assertNotNull(rescued)
        val checklist = rescued!!.components.first { it.type == ComponentType.CHECKLIST }
        val items = ChecklistDslItems.parse(checklist.config).map { it.label }
        assertTrue(items.contains("Cranberries"))
        assertTrue(items.contains("Mascarpone cheese"))
        assertTrue(items.contains("Ladyfingers"))
        assertFalse(items.contains("Tiramisu ingredients"))
    }

    @Test
    fun `rescue rebalances multiple checklists into checklist plus notes when one carries narrative content`() = kotlinx.coroutines.test.runTest {
        val service = object : LLMService {
            override val tier = LLMTier.GEMMA_4_E4B
            override fun isAvailable() = true
            override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
                error("unused")
            }
            override suspend fun complete(prompt: String): String {
                return when {
                    prompt.contains("UI rebalance agent") -> """
                        {
                          "title": "Waffles with Nutella",
                          "type": "GENERAL",
                          "priority": 0,
                          "targetDateMs": 0,
                          "skills": [
                            {
                              "skill": "checklist",
                              "sortOrder": 0,
                              "populatedFromInput": true,
                              "needsClarification": false,
                              "config": {
                                "config_type": "CHECKLIST",
                                "label": "Shopping list",
                                "allowAddItems": true,
                                "items": [
                                  {"label": "Flour", "isSuggested": false},
                                  {"label": "Milk", "isSuggested": false},
                                  {"label": "Eggs", "isSuggested": false},
                                  {"label": "Nutella", "isSuggested": false}
                                ]
                              }
                            },
                            {
                              "skill": "notes",
                              "sortOrder": 1,
                              "populatedFromInput": false,
                              "needsClarification": false,
                              "config": {
                                "config_type": "NOTES",
                                "text": "## Recipe\n1. Mix the batter.\n2. Cook in the waffle iron.\n3. Serve with Nutella.",
                                "isMarkdown": true
                              }
                            }
                          ],
                          "functionSkills": [],
                          "reminders": [],
                          "fetchers": []
                        }
                    """.trimIndent()
                    else -> ""
                }
            }
        }

        val draft = TaskDSLOutput(
            title = "Waffles with Nutella",
            type = TaskType.GENERAL,
            components = listOf(
                checklistComponent(items = listOf("Flour", "Milk", "Eggs", "Nutella")).copy(sortOrder = 0),
                checklistComponent(items = listOf("Mix dry ingredients", "Mix wet ingredients", "Cook waffles")).copy(sortOrder = 1)
            )
        )

        val rescued = subject.rescue(
            service = service,
            input = "I need a shopping list + recipe for waffles with nutella",
            llmContext = LLMClassificationContext(detectedTaskType = TaskType.GENERAL),
            draft = draft
        )

        assertNotNull(rescued)
        assertEquals(listOf(ComponentType.CHECKLIST, ComponentType.NOTES), rescued!!.components.map { it.type })
        assertTrue(rescued.components.last().config["text"]?.toString()?.contains("Cook in the waffle iron") == true)
    }

    @Test
    fun `rescue rejects planner-only json and prompts for final task schema`() = kotlinx.coroutines.test.runTest {
        val prompts = mutableListOf<String>()
        val plannerOnlyJson = """
            {
              "title": "Argentinian Flan Shopping List and Recipe",
              "type": "GENERAL",
              "uiSkills": ["checklist", "notes"],
              "functionSkills": ["structured-brief"]
            }
        """.trimIndent()
        val service = object : LLMService {
            override val tier = LLMTier.GEMMA_4_E4B
            override fun isAvailable() = true
            override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
                error("unused")
            }
            override suspend fun complete(prompt: String): String {
                prompts += prompt
                return when {
                    prompt.contains("--- RESCUE TASK ---") -> plannerOnlyJson
                    prompt.contains("focused draft repair agent") -> plannerOnlyJson
                    prompt.contains("checklist item repair agent") -> """
                        Eggs
                        Milk
                        Sugar
                        Vanilla extract
                    """.trimIndent()
                    prompt.contains("notes content repair agent") -> """
                        ## Flan argentino
                        1. Hacer un caramelo.
                        2. Mezclar huevos, leche, azucar y vainilla.
                        3. Cocinar a bano maria hasta que cuaje.
                        4. Enfriar antes de desmoldar.
                    """.trimIndent()
                    else -> ""
                }
            }
        }

        val rescued = subject.rescue(
            service = service,
            input = "I need a shopping list+ recipe for making an Argentinian flan",
            llmContext = LLMClassificationContext(detectedTaskType = TaskType.GENERAL),
            draft = TaskDSLOutput(
                title = "Argentinian Flan Shopping List and Recipe",
                type = TaskType.GENERAL,
                components = emptyList()
            )
        )

        assertNotNull(rescued)
        assertTrue(rescued!!.components.any { it.type == ComponentType.CHECKLIST })
        assertTrue(rescued.components.any { it.type == ComponentType.NOTES })
        assertFalse(subject.needsRescue(rescued))
        assertTrue(
            prompts.any { prompt ->
                prompt.contains("--- RESCUE TASK ---") &&
                    prompt.contains("Final task JSON schema:") &&
                    !prompt.contains("Plan JSON schema:")
            }
        )
    }

    @Test
    fun `rescue salvages malformed final task json and still repairs missing notes`() = kotlinx.coroutines.test.runTest {
        val malformedRescueJson = """
            ```json
            {
              "title": "Argentinian Flan Shopping List and Recipe",
              "type": "GENERAL",
              "skills": [
                {
                  "skill": "checklist",
                  "sortOrder": 0,
                  "config": {
                    "config_type": "CHECKLIST",
                    "label": "Shopping list",
                    "allowAddItems": true,
                    "items": [
                      {"label": "Eggs", "isSuggested": false},
                      {"label": "Milk", "isSuggested": false},
                      {"label": "Sugar", "isSuggested": false},
                      {"label": "Vanilla extract", "isSuggested": true}
                    ],
                    "quality_rules": "expand known bundles, recipes, recipes, recipes
            """.trimIndent()

        val service = object : LLMService {
            override val tier = LLMTier.GEMMA_4_E4B
            override fun isAvailable() = true
            override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
                error("unused")
            }
            override suspend fun complete(prompt: String): String {
                return when {
                    prompt.contains("--- RESCUE TASK ---") -> malformedRescueJson
                    prompt.contains("notes content repair agent") -> """
                        ## Flan argentino
                        1. Preparar un caramelo con azucar.
                        2. Mezclar huevos, leche y vainilla.
                        3. Cocinar a bano maria hasta que cuaje.
                        4. Enfriar y desmoldar.
                    """.trimIndent()
                    else -> ""
                }
            }
        }

        val rescued = subject.rescue(
            service = service,
            input = "I need a shopping list+ recipe for making an Argentinian flan",
            llmContext = LLMClassificationContext(detectedTaskType = TaskType.GENERAL),
            draft = TaskDSLOutput(
                title = "Argentinian Flan Shopping List and Recipe",
                type = TaskType.GENERAL,
                components = emptyList()
            )
        )

        assertNotNull(rescued)
        val checklist = rescued!!.components.first { it.type == ComponentType.CHECKLIST }
        assertEquals(
            listOf("Eggs", "Milk", "Sugar", "Vanilla extract"),
            ChecklistDslItems.parse(checklist.config).map { it.label }
        )
        assertTrue(rescued.components.any { it.type == ComponentType.NOTES })
        assertFalse(subject.needsRescue(rescued))
    }

    private fun notesComponent(text: String) = ComponentDSL(
        type = ComponentType.NOTES,
        sortOrder = 0,
        config = JsonObject(
            mapOf(
                "config_type" to JsonPrimitive("NOTES"),
                "text" to JsonPrimitive(text),
                "isMarkdown" to JsonPrimitive(true)
            )
        )
    )

    private fun checklistComponent(items: List<String> = emptyList()) = ComponentDSL(
        type = ComponentType.CHECKLIST,
        sortOrder = 1,
        config = ChecklistDslItems.withItems(
            JsonObject(
                mapOf(
                    "config_type" to JsonPrimitive("CHECKLIST"),
                    "label" to JsonPrimitive("Ingredients"),
                    "allowAddItems" to JsonPrimitive(true)
                )
            ),
            items.map { com.theveloper.aura.engine.dsl.ChecklistItemDSL(it) }
        )
    )
}

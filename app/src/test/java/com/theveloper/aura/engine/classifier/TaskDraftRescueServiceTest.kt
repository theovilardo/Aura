package com.theveloper.aura.engine.classifier

import android.content.Context
import android.content.res.AssetManager
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
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

    private fun checklistComponent() = ComponentDSL(
        type = ComponentType.CHECKLIST,
        sortOrder = 1,
        config = JsonObject(
            mapOf(
                "config_type" to JsonPrimitive("CHECKLIST"),
                "label" to JsonPrimitive("Ingredients"),
                "allowAddItems" to JsonPrimitive(true)
            )
        )
    )
}

package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.ChecklistDslItems
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDSLQualityGateTest {

    private val subject = TaskDSLQualityGate()

    @Test
    fun `enforce generates structured roadmap content for hollow learning skills`() {
        val result = subject.enforce(
            dsl = TaskDSLOutput(
                title = "Roadmap and Guide for Kotlin Programming",
                type = TaskType.GOAL,
                components = listOf(
                    ComponentDSL(
                        skillId = "notes",
                        type = ComponentType.NOTES,
                        sortOrder = 0,
                        config = JsonObject(
                            mapOf(
                                "config_type" to JsonPrimitive("NOTES"),
                                "text" to JsonPrimitive(""),
                                "isMarkdown" to JsonPrimitive(true)
                            )
                        )
                    ),
                    ComponentDSL(
                        skillId = "checklist",
                        type = ComponentType.CHECKLIST,
                        sortOrder = 1,
                        config = JsonObject(
                            mapOf(
                                "config_type" to JsonPrimitive("CHECKLIST"),
                                "label" to JsonPrimitive("Checklist"),
                                "allowAddItems" to JsonPrimitive(true)
                            )
                        )
                    )
                )
            ),
            input = "quiero aprender a programar en Kotlin, necesito un roadmap y guía con descripción, también quiero links o referencias",
            entities = ExtractedEntities()
        )

        val passed = result as TaskDSLQualityGate.GateResult.Passed
        val notes = passed.dsl.components.first { it.type == ComponentType.NOTES }
        val checklist = passed.dsl.components.first { it.type == ComponentType.CHECKLIST }
        val text = notes.config["text"]?.toString().orEmpty()
        val items = ChecklistDslItems.parse(checklist.config).map { it.label }

        assertTrue(text.contains("Documentación oficial de Kotlin"))
        assertTrue(text.contains("## Roadmap sugerido"))
        assertEquals("Hitos del roadmap", checklist.config["label"]?.toString()?.trim('"'))
        assertTrue(items.contains("Sintaxis básica de Kotlin"))
        assertTrue(items.contains("Coroutines y Flow"))
        assertTrue(passed.repairs.any { it.contains("roadmap content") || it.contains("structured checklist") })
    }
}

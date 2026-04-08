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
    fun `enforce injects explicit inline items into checklist and prunes empty notes`() {
        val result = subject.enforce(
            dsl = TaskDSLOutput(
                title = "Milk, bread, eggs",
                type = TaskType.GENERAL,
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
                    )
                )
            ),
            input = "milk, bread, eggs",
            entities = ExtractedEntities()
        )

        val passed = result as TaskDSLQualityGate.GateResult.Passed
        val checklist = passed.dsl.components.first { it.type == ComponentType.CHECKLIST }
        val items = ChecklistDslItems.parse(checklist.config).map { it.label }

        assertEquals(listOf("milk", "bread", "eggs"), items)
        assertEquals(1, passed.dsl.components.size)
        assertTrue(passed.repairs.any { it.contains("explicit items") })
        assertTrue(passed.repairs.any { it.contains("Removed low-value NOTES") })
    }

    @Test
    fun `enforce marks empty checklist for clarification when no explicit items exist`() {
        val result = subject.enforce(
            dsl = TaskDSLOutput(
                title = "Plan",
                type = TaskType.GENERAL,
                components = listOf(
                    ComponentDSL(
                        skillId = "checklist",
                        type = ComponentType.CHECKLIST,
                        sortOrder = 0,
                        config = JsonObject(
                            mapOf(
                                "config_type" to JsonPrimitive("CHECKLIST"),
                                "label" to JsonPrimitive("Plan"),
                                "allowAddItems" to JsonPrimitive(true)
                            )
                        )
                    )
                )
            ),
            input = "make a plan",
            entities = ExtractedEntities()
        )

        val passed = result as TaskDSLQualityGate.GateResult.Passed
        val checklist = passed.dsl.components.first()

        assertTrue(checklist.needsClarification)
        assertTrue(passed.repairs.any { it.contains("Marked empty CHECKLIST") })
    }

    @Test
    fun `enforce prunes teaser notes when stronger ui content exists`() {
        val result = subject.enforce(
            dsl = TaskDSLOutput(
                title = "Chocolate pudding recipe",
                type = TaskType.GENERAL,
                components = listOf(
                    ComponentDSL(
                        skillId = "checklist",
                        type = ComponentType.CHECKLIST,
                        sortOrder = 0,
                        config = ChecklistDslItems.withItems(
                            config = JsonObject(
                                mapOf(
                                    "config_type" to JsonPrimitive("CHECKLIST"),
                                    "label" to JsonPrimitive("Ingredients"),
                                    "allowAddItems" to JsonPrimitive(true)
                                )
                            ),
                            items = listOf(
                                com.theveloper.aura.engine.dsl.ChecklistItemDSL("milk"),
                                com.theveloper.aura.engine.dsl.ChecklistItemDSL("cocoa"),
                                com.theveloper.aura.engine.dsl.ChecklistItemDSL("sugar")
                            )
                        )
                    ),
                    ComponentDSL(
                        skillId = "notes",
                        type = ComponentType.NOTES,
                        sortOrder = 1,
                        config = JsonObject(
                            mapOf(
                                "config_type" to JsonPrimitive("NOTES"),
                                "text" to JsonPrimitive("Here is a chocolate pudding recipe. Please wait for the full recipe content."),
                                "isMarkdown" to JsonPrimitive(true)
                            )
                        )
                    )
                )
            ),
            input = "I need a chocolate pudding recipe",
            entities = ExtractedEntities()
        )

        val passed = result as TaskDSLQualityGate.GateResult.Passed

        assertEquals(listOf(ComponentType.CHECKLIST), passed.dsl.components.map { it.type })
        assertTrue(passed.repairs.any { it.contains("Removed low-value NOTES") })
    }

    @Test
    fun `enforce sanitizes malformed checklist rows before keeping checklist`() {
        val result = subject.enforce(
            dsl = TaskDSLOutput(
                title = "Argentinian Flan Shopping List and Recipe",
                type = TaskType.GENERAL,
                components = listOf(
                    ComponentDSL(
                        skillId = "checklist",
                        type = ComponentType.CHECKLIST,
                        sortOrder = 0,
                        config = ChecklistDslItems.withItems(
                            config = JsonObject(
                                mapOf(
                                    "config_type" to JsonPrimitive("CHECKLIST"),
                                    "label" to JsonPrimitive("Argentinian Flan Shopping List and Recipe"),
                                    "allowAddItems" to JsonPrimitive(true)
                                )
                            ),
                            items = listOf(
                                com.theveloper.aura.engine.dsl.ChecklistItemDSL("Flour"),
                                com.theveloper.aura.engine.dsl.ChecklistItemDSL("Sugar"),
                                com.theveloper.aura.engine.dsl.ChecklistItemDSL("Eggs"),
                                com.theveloper.aura.engine.dsl.ChecklistItemDSL("Milk"),
                                com.theveloper.aura.engine.dsl.ChecklistItemDSL("Vanilla Extract"),
                                com.theveloper.aura.engine.dsl.ChecklistItemDSL("Fl Flan Mold"),
                                com.theveloper.aura.engine.dsl.ChecklistItemDSL("Caramel Ingredients"),
                                com.theveloper.aura.engine.dsl.ChecklistItemDSL("Butter"),
                                com.theveloper.aura.engine.dsl.ChecklistItemDSL("Fl Mold"),
                                com.theveloper.aura.engine.dsl.ChecklistItemDSL("Mold")
                            )
                        )
                    )
                )
            ),
            input = "I need a shopping list + recipe for making an Argentinian flan",
            entities = ExtractedEntities()
        )

        val passed = result as TaskDSLQualityGate.GateResult.Passed
        val checklist = passed.dsl.components.first { it.type == ComponentType.CHECKLIST }

        assertEquals(
            listOf("Flour", "Sugar", "Eggs", "Milk", "Vanilla Extract", "Flan Mold", "Butter"),
            ChecklistDslItems.parse(checklist.config).map { it.label }
        )
        assertTrue(passed.repairs.any { it.contains("Sanitized noisy CHECKLIST items") })
    }

    @Test
    fun `enforce prunes empty checklist when strong notes already exist`() {
        val result = subject.enforce(
            dsl = TaskDSLOutput(
                title = "Chocolate pudding recipe",
                type = TaskType.GENERAL,
                components = listOf(
                    ComponentDSL(
                        skillId = "checklist",
                        type = ComponentType.CHECKLIST,
                        sortOrder = 0,
                        config = JsonObject(
                            mapOf(
                                "config_type" to JsonPrimitive("CHECKLIST"),
                                "label" to JsonPrimitive("Ingredients"),
                                "allowAddItems" to JsonPrimitive(true)
                            )
                        )
                    ),
                    ComponentDSL(
                        skillId = "notes",
                        type = ComponentType.NOTES,
                        sortOrder = 1,
                        config = JsonObject(
                            mapOf(
                                "config_type" to JsonPrimitive("NOTES"),
                                "text" to JsonPrimitive(
                                    """
                                    ## Ingredients
                                    - Milk
                                    - Cocoa
                                    - Sugar

                                    ## Steps
                                    1. Heat the milk.
                                    2. Whisk everything together.
                                    3. Chill until thick.
                                    """.trimIndent()
                                ),
                                "isMarkdown" to JsonPrimitive(true)
                            )
                        )
                    )
                )
            ),
            input = "I need a chocolate pudding recipe",
            entities = ExtractedEntities()
        )

        val passed = result as TaskDSLQualityGate.GateResult.Passed

        assertEquals(listOf(ComponentType.NOTES), passed.dsl.components.map { it.type })
        assertTrue(passed.repairs.any { it.contains("Removed low-value CHECKLIST") })
    }

    @Test
    fun `enforce replaces hollow note with checklist clarification scaffold`() {
        val result = subject.enforce(
            dsl = TaskDSLOutput(
                title = "Chocolate pudding recipe",
                type = TaskType.GENERAL,
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
                    )
                )
            ),
            input = "I need a chocolate pudding recipe",
            entities = ExtractedEntities()
        )

        val passed = result as TaskDSLQualityGate.GateResult.Passed
        val scaffold = passed.dsl.components.first()

        assertEquals(ComponentType.CHECKLIST, scaffold.type)
        assertTrue(scaffold.needsClarification)
        assertTrue(passed.repairs.any { it.contains("clarification scaffold") })
    }
}

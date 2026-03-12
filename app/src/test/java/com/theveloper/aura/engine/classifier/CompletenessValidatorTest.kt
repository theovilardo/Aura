package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.ChecklistDslItems
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletenessValidatorTest {

    private val subject = CompletenessValidator()

    @Test
    fun `clarification answer splits trailing english conjunction in comma list`() {
        val result = subject.enrich(
            input = """
                shopping list for groceries

                Aclaracion del usuario: tomatoes, milk, bread, Cheese and ham
            """.trimIndent(),
            dsl = shoppingChecklistDsl()
        )

        val checklist = result.dsl.components.first { it.type == ComponentType.CHECKLIST }
        val items = ChecklistDslItems.parse(checklist.config).map { it.label }

        assertEquals(listOf("tomatoes", "milk", "bread", "Cheese", "ham"), items)
        assertNull(result.clarification)
    }

    @Test
    fun `clarification answer keeps non trailing compound item intact`() {
        val result = subject.enrich(
            input = """
                empanada flavors

                Aclaracion del usuario: ham and cheese, beef, chicken
            """.trimIndent(),
            dsl = shoppingChecklistDsl()
        )

        val checklist = result.dsl.components.first { it.type == ComponentType.CHECKLIST }
        val items = ChecklistDslItems.parse(checklist.config).map { it.label }

        assertEquals(listOf("ham and cheese", "beef", "chicken"), items)
    }

    @Test
    fun `enrichChecklist skips regex when populatedFromInput is true and items exist`() {
        val dsl = TaskDSLOutput(
            title = "Shopping List",
            type = TaskType.GENERAL,
            components = listOf(
                ComponentDSL(
                    type = ComponentType.CHECKLIST,
                    sortOrder = 0,
                    config = JsonObject(
                        mapOf(
                            "config_type" to JsonPrimitive("CHECKLIST"),
                            "label" to JsonPrimitive("Grocery Store"),
                            "allowAddItems" to JsonPrimitive(true),
                            "items" to JsonArray(
                                listOf(
                                    JsonObject(
                                        mapOf(
                                            "label" to JsonPrimitive("tomatoes"),
                                            "isSuggested" to JsonPrimitive(false)
                                        )
                                    ),
                                    JsonObject(
                                        mapOf(
                                            "label" to JsonPrimitive("cheese"),
                                            "isSuggested" to JsonPrimitive(false)
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    populatedFromInput = true,
                    needsClarification = false
                )
            )
        )

        val result = subject.enrich(
            input = "shopping list for groceries, comprar tomates y queso",
            dsl = dsl
        )

        val checklist = result.dsl.components.first { it.type == ComponentType.CHECKLIST }
        val items = ChecklistDslItems.parse(checklist.config).map { it.label }

        // Should keep the semantic items, not run regex extraction
        assertEquals(listOf("tomatoes", "cheese"), items)
        assertNull(result.clarification)
        assertTrue(result.check.isComplete)
    }

    private fun shoppingChecklistDsl(): TaskDSLOutput {
        return TaskDSLOutput(
            title = "Shopping List",
            type = TaskType.GENERAL,
            components = listOf(
                ComponentDSL(
                    type = ComponentType.CHECKLIST,
                    sortOrder = 0,
                    config = JsonObject(
                        mapOf(
                            "config_type" to JsonPrimitive("CHECKLIST"),
                            "label" to JsonPrimitive("Shopping List"),
                            "allowAddItems" to JsonPrimitive(true)
                        )
                    ),
                    needsClarification = true
                )
            )
        )
    }
}

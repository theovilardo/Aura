package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.dsl.TaskDSLValidator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDSLValidatorTest {

    @Test
    fun `validate rejects blank title`() {
        val dsl = minimalDsl(title = "")
        val result = TaskDSLValidator.validate(dsl)
        assertTrue(result is TaskDSLValidator.ValidationResult.Invalid)
    }

    @Test
    fun `validate rejects placeholder title`() {
        val dsl = minimalDsl(title = TaskDSLValidator.PLACEHOLDER_TITLE)
        val result = TaskDSLValidator.validate(dsl)
        assertTrue(
            "Validator must reject '${TaskDSLValidator.PLACEHOLDER_TITLE}' as an invalid title so the heuristic fallback is used",
            result is TaskDSLValidator.ValidationResult.Invalid
        )
    }

    @Test
    fun `validate accepts non-blank non-placeholder title`() {
        val dsl = minimalDsl(title = "Buy groceries")
        val result = TaskDSLValidator.validate(dsl)
        assertTrue(result is TaskDSLValidator.ValidationResult.Valid)
    }

    @Test
    fun `PLACEHOLDER_TITLE constant matches the string written by normalizer`() {
        // Ensure constant stays in sync with the fallback string in LLMParsing
        assertTrue(TaskDSLValidator.PLACEHOLDER_TITLE.isNotBlank())
        assertFalse(TaskDSLValidator.PLACEHOLDER_TITLE == "")
    }

    @Test
    fun `validate rejects unknown ui skill ids`() {
        val dsl = TaskDSLOutput(
            title = "Buy groceries",
            type = TaskType.GENERAL,
            components = listOf(
                ComponentDSL(
                    skillId = "mystery-skill",
                    type = ComponentType.NOTES,
                    sortOrder = 0,
                    config = JsonObject(
                        mapOf(
                            "config_type" to JsonPrimitive("NOTES"),
                            "text" to JsonPrimitive("hello"),
                            "isMarkdown" to JsonPrimitive(true)
                        )
                    )
                )
            )
        )

        val result = TaskDSLValidator.validate(dsl)
        assertTrue(result is TaskDSLValidator.ValidationResult.Invalid)
    }

    private fun minimalDsl(title: String): TaskDSLOutput {
        return TaskDSLOutput(
            title = title,
            type = TaskType.GENERAL,
            components = listOf(
                ComponentDSL(
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
        )
    }
}

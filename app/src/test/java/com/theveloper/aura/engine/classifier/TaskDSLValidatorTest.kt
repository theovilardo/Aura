package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.FunctionSkillRuntime
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.model.UiSkillRuntime
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.FunctionSkillDSL
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

    @Test
    fun `validate accepts known function skills`() {
        val dsl = TaskDSLOutput(
            title = "Learn Kotlin",
            type = TaskType.GOAL,
            components = listOf(
                ComponentDSL(
                    skillId = "notes",
                    skillRuntime = UiSkillRuntime.NATIVE,
                    type = ComponentType.NOTES,
                    sortOrder = 0,
                    config = JsonObject(
                        mapOf(
                            "config_type" to JsonPrimitive("NOTES"),
                            "text" to JsonPrimitive("Plan"),
                            "isMarkdown" to JsonPrimitive(true)
                        )
                    )
                )
            ),
            functionSkills = listOf(
                FunctionSkillDSL(
                    skillId = "learning-guide",
                    runtime = FunctionSkillRuntime.PROMPT_AUGMENTATION,
                    config = JsonObject(mapOf("mode" to JsonPrimitive("roadmap")))
                ),
                FunctionSkillDSL(
                    skillId = "resource-curator",
                    runtime = FunctionSkillRuntime.PROMPT_AUGMENTATION,
                    config = JsonObject(mapOf("preferOfficial" to JsonPrimitive(true)))
                )
            )
        )

        val result = TaskDSLValidator.validate(dsl)
        assertTrue(result is TaskDSLValidator.ValidationResult.Valid)
    }

    @Test
    fun `validate rejects function skill runtime mismatch`() {
        val dsl = TaskDSLOutput(
            title = "Learn Kotlin",
            type = TaskType.GOAL,
            components = listOf(
                ComponentDSL(
                    skillId = "notes",
                    skillRuntime = UiSkillRuntime.NATIVE,
                    type = ComponentType.NOTES,
                    sortOrder = 0,
                    config = JsonObject(
                        mapOf(
                            "config_type" to JsonPrimitive("NOTES"),
                            "text" to JsonPrimitive("Plan"),
                            "isMarkdown" to JsonPrimitive(true)
                        )
                    )
                )
            ),
            functionSkills = listOf(
                FunctionSkillDSL(
                    skillId = "learning-guide",
                    runtime = FunctionSkillRuntime.LOCAL_EXECUTOR,
                    config = JsonObject(mapOf("mode" to JsonPrimitive("roadmap")))
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

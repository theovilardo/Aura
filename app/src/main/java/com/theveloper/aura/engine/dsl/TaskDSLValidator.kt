package com.theveloper.aura.engine.dsl

import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.domain.model.ComponentConfig
import com.theveloper.aura.engine.skill.SkillRegistry
import com.theveloper.aura.engine.skill.UiSkillRegistry
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

object TaskDSLValidator {

    fun validate(dsl: TaskDSLOutput): ValidationResult {
        if (dsl.title.isBlank() || dsl.title == PLACEHOLDER_TITLE) {
            return ValidationResult.Invalid("Task title cannot be blank or placeholder")
        }

        if (dsl.priority !in 0..3) {
            return ValidationResult.Invalid("Task priority must be between 0 and 3")
        }

        if (dsl.components.isEmpty()) {
            return ValidationResult.Invalid("Task must have at least one component")
        }

        val duplicatedSortOrders = dsl.components
            .groupBy { it.sortOrder }
            .filterValues { it.size > 1 }
            .keys
        if (duplicatedSortOrders.isNotEmpty()) {
            return ValidationResult.Invalid("Component sort orders must be unique")
        }

        dsl.components.forEach { component ->
            component.skillId?.takeIf { it.isNotBlank() }?.let { skillId ->
                val validation = UiSkillRegistry.validate(skillId, component.config)
                if (!validation.isValid) {
                    return ValidationResult.Invalid(validation.reason ?: "Invalid UI skill selection")
                }
                if (validation.definition?.componentType != component.type) {
                    return ValidationResult.Invalid(
                        "UI skill $skillId does not match component ${component.type}"
                    )
                }
                component.skillRuntime?.let { runtime ->
                    if (validation.definition?.runtime != runtime) {
                        return ValidationResult.Invalid(
                            "UI skill $skillId does not match runtime ${runtime.name}"
                        )
                    }
                }
            }

            val configType = component.config["config_type"]?.jsonPrimitive?.contentOrNull
            if (configType != component.type.name) {
                return ValidationResult.Invalid("Config type ${configType ?: "null"} does not match component ${component.type}")
            }

            decodeComponentConfig(component.config).onFailure {
                return ValidationResult.Invalid("Invalid config for ${component.type}: ${it.message}")
            }
        }

        dsl.functionSkills.forEach { functionSkill ->
            val validation = SkillRegistry.validateFunctionSkill(functionSkill.skillId, functionSkill.config)
            if (!validation.isValid) {
                return ValidationResult.Invalid(validation.reason ?: "Invalid function skill selection")
            }
            functionSkill.runtime?.let { runtime ->
                if (validation.definition?.runtime != runtime) {
                    return ValidationResult.Invalid(
                        "Function skill ${functionSkill.skillId} does not match runtime ${runtime.name}"
                    )
                }
            }
        }

        dsl.fetchers.forEach { fetcher ->
            if (fetcher.cronExpression.isBlank()) {
                return ValidationResult.Invalid("Fetcher ${fetcher.type} must declare a cron expression")
            }
        }

        dsl.reminders.forEach { reminder ->
            if (reminder.scheduledAtMs <= 0L) {
                return ValidationResult.Invalid("Reminder scheduledAtMs must be positive")
            }
        }

        return ValidationResult.Valid
    }

    private fun decodeComponentConfig(config: JsonObject): Result<ComponentConfig> {
        return runCatching { auraJson.decodeFromJsonElement<ComponentConfig>(config) }
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    /**
     * The fallback placeholder written by the normalizer when the LLM returns a blank title.
     * Treated as invalid so the pipeline falls through to the heuristic rather than
     * surfacing a meaningless placeholder to the user.
     */
    const val PLACEHOLDER_TITLE = "Untitled task"
}

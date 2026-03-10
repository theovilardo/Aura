package com.theveloper.aura.engine.dsl

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.FetcherType

object TaskDSLValidator {

    fun validate(dsl: TaskDSLOutput): ValidationResult {
        if (dsl.title.isBlank()) {
            return ValidationResult.Invalid("Task title cannot be blank")
        }

        // Validate Components
        dsl.components.forEach { component ->
            runCatching { ComponentType.valueOf(component.type) }.onFailure {
                return ValidationResult.Invalid("Unknown component type: ${component.type}")
            }
        }

        // Validate Fetchers
        dsl.fetchers.forEach { fetcher ->
            runCatching { FetcherType.valueOf(fetcher.type) }.onFailure {
                return ValidationResult.Invalid("Unknown fetcher type: ${fetcher.type}")
            }
        }

        return ValidationResult.Valid
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }
}

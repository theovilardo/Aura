package com.theveloper.aura.domain.model.rule

import kotlinx.serialization.Serializable

@Serializable
data class TriggerCondition(
    val field: String,
    val operator: ConditionOperator,
    val value: String
)

@Serializable
enum class ConditionOperator {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    MULTIPLE_OF
}

package com.theveloper.aura.domain.model.rule

import java.util.UUID

data class ComponentRule(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val triggerComponentId: String,
    val triggerEvent: TriggerEvent,
    val triggerCondition: TriggerCondition? = null,
    val targetComponentId: String,
    val action: RuleAction,
    val actionParams: Map<String, String> = emptyMap(),
    val isEnabled: Boolean = true,
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: RuleOrigin = RuleOrigin.SYSTEM
)

enum class RuleOrigin {
    SYSTEM,
    USER
}

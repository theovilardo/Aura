package com.theveloper.aura.domain.repository

import com.theveloper.aura.domain.model.rule.ComponentRule
import kotlinx.coroutines.flow.Flow

interface ComponentRuleRepository {
    fun getRulesForTask(taskId: String): Flow<List<ComponentRule>>
    suspend fun getRulesForComponent(componentId: String): List<ComponentRule>
    suspend fun insertRule(rule: ComponentRule)
    suspend fun insertRules(rules: List<ComponentRule>)
    suspend fun setRuleEnabled(ruleId: String, enabled: Boolean)
    suspend fun deleteRulesForTask(taskId: String)
}

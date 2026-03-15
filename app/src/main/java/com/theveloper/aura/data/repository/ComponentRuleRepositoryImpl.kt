package com.theveloper.aura.data.repository

import com.theveloper.aura.data.db.ComponentRuleDao
import com.theveloper.aura.data.mapper.toDomain
import com.theveloper.aura.data.mapper.toEntity
import com.theveloper.aura.domain.model.rule.ComponentRule
import com.theveloper.aura.domain.repository.ComponentRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComponentRuleRepositoryImpl @Inject constructor(
    private val dao: ComponentRuleDao
) : ComponentRuleRepository {

    override fun getRulesForTask(taskId: String): Flow<List<ComponentRule>> {
        return dao.getRulesForTask(taskId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getRulesForComponent(componentId: String): List<ComponentRule> {
        return dao.getRulesForComponent(componentId).map { it.toDomain() }
    }

    override suspend fun insertRule(rule: ComponentRule) {
        dao.insertRule(rule.toEntity())
    }

    override suspend fun insertRules(rules: List<ComponentRule>) {
        dao.insertRules(rules.map { it.toEntity() })
    }

    override suspend fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        dao.setRuleEnabled(ruleId, enabled)
    }

    override suspend fun deleteRulesForTask(taskId: String) {
        dao.deleteRulesForTask(taskId)
    }
}

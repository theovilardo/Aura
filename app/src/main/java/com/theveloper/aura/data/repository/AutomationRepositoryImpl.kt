package com.theveloper.aura.data.repository

import com.theveloper.aura.data.db.AuraAutomationDao
import com.theveloper.aura.data.mapper.toDomain
import com.theveloper.aura.data.mapper.toEntity
import com.theveloper.aura.domain.model.AuraAutomation
import com.theveloper.aura.domain.model.AutomationStatus
import com.theveloper.aura.domain.repository.AutomationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AutomationRepositoryImpl @Inject constructor(
    private val dao: AuraAutomationDao
) : AutomationRepository {

    override fun observeAll(): Flow<List<AuraAutomation>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeById(id: String): Flow<AuraAutomation?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): AuraAutomation? =
        dao.getById(id)?.toDomain()

    override suspend fun getActive(): List<AuraAutomation> =
        dao.getActive().map { it.toDomain() }

    override suspend fun insert(automation: AuraAutomation) {
        dao.insert(automation.toEntity())
    }

    override suspend fun update(automation: AuraAutomation) {
        dao.update(automation.toEntity())
    }

    override suspend fun updateStatus(id: String, status: AutomationStatus) {
        dao.updateStatus(id, status, System.currentTimeMillis())
    }

    override suspend fun updateExecutionResult(
        id: String, executedAt: Long, resultJson: String?, failureCount: Int
    ) {
        dao.updateExecutionResult(id, executedAt, resultJson, failureCount, System.currentTimeMillis())
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }
}

package com.theveloper.aura.domain.repository

import com.theveloper.aura.domain.model.AuraAutomation
import com.theveloper.aura.domain.model.AutomationStatus
import kotlinx.coroutines.flow.Flow

interface AutomationRepository {
    fun observeAll(): Flow<List<AuraAutomation>>
    fun observeById(id: String): Flow<AuraAutomation?>
    suspend fun getById(id: String): AuraAutomation?
    suspend fun getActive(): List<AuraAutomation>
    suspend fun insert(automation: AuraAutomation)
    suspend fun update(automation: AuraAutomation)
    suspend fun updateStatus(id: String, status: AutomationStatus)
    suspend fun updateExecutionResult(
        id: String, executedAt: Long, resultJson: String?, failureCount: Int
    )
    suspend fun delete(id: String)
}

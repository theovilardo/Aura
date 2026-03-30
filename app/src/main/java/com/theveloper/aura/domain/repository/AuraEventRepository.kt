package com.theveloper.aura.domain.repository

import com.theveloper.aura.domain.model.AuraEvent
import com.theveloper.aura.domain.model.EventStatus
import kotlinx.coroutines.flow.Flow

interface AuraEventRepository {
    fun observeAll(): Flow<List<AuraEvent>>
    fun observeById(id: String): Flow<AuraEvent?>
    suspend fun getById(id: String): AuraEvent?
    suspend fun getEventsToActivate(now: Long): List<AuraEvent>
    suspend fun getEventsToComplete(now: Long): List<AuraEvent>
    suspend fun getActiveEvents(): List<AuraEvent>
    suspend fun insert(event: AuraEvent)
    suspend fun update(event: AuraEvent)
    suspend fun updateStatus(id: String, status: EventStatus)
    suspend fun delete(id: String)
}

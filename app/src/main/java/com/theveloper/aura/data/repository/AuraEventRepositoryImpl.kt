package com.theveloper.aura.data.repository

import com.theveloper.aura.data.db.AuraEventDao
import com.theveloper.aura.data.db.EventComponentDao
import com.theveloper.aura.data.db.EventComponentEntity
import com.theveloper.aura.data.db.EventSubActionDao
import com.theveloper.aura.data.db.TaskComponentDao
import com.theveloper.aura.data.mapper.toDomain
import com.theveloper.aura.data.mapper.toEntity
import com.theveloper.aura.domain.model.AuraEvent
import com.theveloper.aura.domain.model.EventStatus
import com.theveloper.aura.domain.repository.AuraEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class AuraEventRepositoryImpl @Inject constructor(
    private val eventDao: AuraEventDao,
    private val subActionDao: EventSubActionDao,
    private val eventComponentDao: EventComponentDao,
    private val taskComponentDao: TaskComponentDao
) : AuraEventRepository {

    override fun observeAll(): Flow<List<AuraEvent>> =
        eventDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeById(id: String): Flow<AuraEvent?> =
        eventDao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): AuraEvent? =
        eventDao.getWithDetails(id)?.toDomain()

    override suspend fun getEventsToActivate(now: Long): List<AuraEvent> =
        eventDao.getEventsToActivate(now).map { it.toDomain() }

    override suspend fun getEventsToComplete(now: Long): List<AuraEvent> =
        eventDao.getEventsToComplete(now).map { it.toDomain() }

    override suspend fun getActiveEvents(): List<AuraEvent> =
        eventDao.getActiveEvents().map { it.toDomain() }

    override suspend fun insert(event: AuraEvent) {
        eventDao.insert(event.toEntity())
        if (event.subActions.isNotEmpty()) {
            subActionDao.insertAll(event.subActions.map { it.toEntity() })
        }
        // Persist event components: insert into task_components then link
        if (event.components.isNotEmpty()) {
            val componentEntities = event.components.map { it.toEntity() }
            taskComponentDao.insertComponents(componentEntities)
            eventComponentDao.insertAll(
                event.components.map { comp ->
                    EventComponentEntity(
                        id = UUID.randomUUID().toString(),
                        eventId = event.id,
                        componentId = comp.id
                    )
                }
            )
        }
    }

    override suspend fun update(event: AuraEvent) {
        eventDao.update(event.toEntity())
        subActionDao.deleteForEvent(event.id)
        if (event.subActions.isNotEmpty()) {
            subActionDao.insertAll(event.subActions.map { it.toEntity() })
        }
    }

    override suspend fun updateStatus(id: String, status: EventStatus) {
        eventDao.updateStatus(id, status, System.currentTimeMillis())
    }

    override suspend fun delete(id: String) {
        eventDao.delete(id) // CASCADE deletes sub_actions and event_components
    }
}

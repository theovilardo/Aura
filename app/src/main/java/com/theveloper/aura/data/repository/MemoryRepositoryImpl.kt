package com.theveloper.aura.data.repository

import com.theveloper.aura.data.db.AuraDatabase
import com.theveloper.aura.data.mapper.toDomain
import com.theveloper.aura.data.mapper.toEntity
import com.theveloper.aura.domain.model.MemoryCategory
import com.theveloper.aura.domain.model.MemorySlot
import com.theveloper.aura.domain.repository.MemoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepositoryImpl @Inject constructor(
    db: AuraDatabase
) : MemoryRepository {
    private val dao = db.memorySlotDao()

    override suspend fun getSlots(): List<MemorySlot> {
        return dao.getAll().map { it.toDomain() }
    }

    override suspend fun getSlot(category: MemoryCategory): MemorySlot? {
        return dao.getByCategory(category)?.toDomain()
    }

    override suspend fun upsert(slot: MemorySlot) {
        dao.upsert(slot.toEntity())
    }

    override suspend fun upsertAll(slots: List<MemorySlot>) {
        if (slots.isEmpty()) return
        dao.upsertAll(slots.map { it.toEntity() })
    }
}

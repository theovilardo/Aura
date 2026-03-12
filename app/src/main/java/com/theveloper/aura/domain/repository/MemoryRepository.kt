package com.theveloper.aura.domain.repository

import com.theveloper.aura.domain.model.MemoryCategory
import com.theveloper.aura.domain.model.MemorySlot

interface MemoryRepository {
    suspend fun getSlots(): List<MemorySlot>
    suspend fun getSlot(category: MemoryCategory): MemorySlot?
    suspend fun upsert(slot: MemorySlot)
    suspend fun upsertAll(slots: List<MemorySlot>)
}

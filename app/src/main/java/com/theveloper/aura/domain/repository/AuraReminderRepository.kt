package com.theveloper.aura.domain.repository

import com.theveloper.aura.domain.model.AuraReminder
import com.theveloper.aura.domain.model.ReminderStatus
import kotlinx.coroutines.flow.Flow

interface AuraReminderRepository {
    fun observeAll(): Flow<List<AuraReminder>>
    fun observeById(id: String): Flow<AuraReminder?>
    suspend fun getById(id: String): AuraReminder?
    suspend fun getDueReminders(beforeTime: Long): List<AuraReminder>
    suspend fun getUpcoming(): List<AuraReminder>
    suspend fun insert(reminder: AuraReminder)
    suspend fun update(reminder: AuraReminder)
    suspend fun updateStatus(id: String, status: ReminderStatus)
    suspend fun delete(id: String)
}

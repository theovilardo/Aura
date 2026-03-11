package com.theveloper.aura.domain.repository

import com.theveloper.aura.domain.model.Reminder
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    suspend fun insertReminders(reminders: List<Reminder>)
    suspend fun insert(reminder: Reminder)
    suspend fun update(reminder: Reminder)
    suspend fun delete(reminder: Reminder)
    fun getActiveRemindersScheduled(beforeTime: Long): Flow<List<Reminder>>
    suspend fun deleteRemindersForTask(taskId: String)
}

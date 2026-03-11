package com.theveloper.aura.data.repository

import com.theveloper.aura.data.db.AuraDatabase
import com.theveloper.aura.data.mapper.toDomain
import com.theveloper.aura.data.mapper.toEntity
import com.theveloper.aura.domain.model.Reminder
import com.theveloper.aura.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val db: AuraDatabase
) : ReminderRepository {
    private val reminderDao = db.reminderDao()

    override suspend fun insertReminders(reminders: List<Reminder>) {
        reminderDao.insertReminders(reminders.map { it.toEntity() })
    }

    override suspend fun insert(reminder: Reminder) {
        reminderDao.insert(reminder.toEntity())
    }

    override suspend fun update(reminder: Reminder) {
        reminderDao.update(reminder.toEntity())
    }

    override suspend fun delete(reminder: Reminder) {
        reminderDao.delete(reminder.toEntity())
    }

    override fun getActiveRemindersScheduled(beforeTime: Long): Flow<List<Reminder>> {
        return reminderDao.getActiveRemindersScheduled(beforeTime).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun deleteRemindersForTask(taskId: String) {
        reminderDao.deleteRemindersForTask(taskId)
    }
}

package com.theveloper.aura.data.repository

import com.theveloper.aura.data.db.AuraReminderDao
import com.theveloper.aura.data.db.ReminderChecklistItemDao
import com.theveloper.aura.data.mapper.toChecklistEntities
import com.theveloper.aura.data.mapper.toDomain
import com.theveloper.aura.data.mapper.toEntity
import com.theveloper.aura.domain.model.AuraReminder
import com.theveloper.aura.domain.model.ReminderStatus
import com.theveloper.aura.domain.repository.AuraReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AuraReminderRepositoryImpl @Inject constructor(
    private val reminderDao: AuraReminderDao,
    private val checklistDao: ReminderChecklistItemDao
) : AuraReminderRepository {

    override fun observeAll(): Flow<List<AuraReminder>> =
        reminderDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeById(id: String): Flow<AuraReminder?> =
        reminderDao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): AuraReminder? =
        reminderDao.getWithChecklist(id)?.toDomain()

    override suspend fun getDueReminders(beforeTime: Long): List<AuraReminder> =
        reminderDao.getDueReminders(beforeTime).map {
            // Due reminders are fetched without checklist for performance; enrich if needed.
            reminderDao.getWithChecklist(it.id)?.toDomain()
                ?: it.let { e ->
                    AuraReminder(
                        id = e.id, title = e.title, body = e.body,
                        reminderType = e.reminderType, scheduledAt = e.scheduledAt,
                        status = e.status, createdAt = e.createdAt, updatedAt = e.updatedAt
                    )
                }
        }

    override suspend fun getUpcoming(): List<AuraReminder> =
        reminderDao.getUpcoming().map {
            reminderDao.getWithChecklist(it.id)?.toDomain()
                ?: AuraReminder(
                    id = it.id, title = it.title, body = it.body,
                    reminderType = it.reminderType, scheduledAt = it.scheduledAt,
                    status = it.status, createdAt = it.createdAt, updatedAt = it.updatedAt
                )
        }

    override suspend fun insert(reminder: AuraReminder) {
        reminderDao.insert(reminder.toEntity())
        if (reminder.checklistItems.isNotEmpty()) {
            checklistDao.insertItems(reminder.toChecklistEntities())
        }
    }

    override suspend fun update(reminder: AuraReminder) {
        reminderDao.update(reminder.toEntity())
        checklistDao.deleteForReminder(reminder.id)
        if (reminder.checklistItems.isNotEmpty()) {
            checklistDao.insertItems(reminder.toChecklistEntities())
        }
    }

    override suspend fun updateStatus(id: String, status: ReminderStatus) {
        reminderDao.updateStatus(id, status, System.currentTimeMillis())
    }

    override suspend fun delete(id: String) {
        reminderDao.delete(id)
    }
}

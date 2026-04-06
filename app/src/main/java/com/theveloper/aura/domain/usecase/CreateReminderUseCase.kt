package com.theveloper.aura.domain.usecase

import com.theveloper.aura.domain.model.AuraReminder
import com.theveloper.aura.domain.model.ChecklistItem
import com.theveloper.aura.domain.model.ReminderStatus
import com.theveloper.aura.domain.repository.AuraReminderRepository
import com.theveloper.aura.engine.dsl.ReminderDSLOutput
import java.util.UUID
import javax.inject.Inject

class CreateReminderUseCase @Inject constructor(
    private val repository: AuraReminderRepository
) {
    suspend operator fun invoke(dsl: ReminderDSLOutput): AuraReminder {
        val now = System.currentTimeMillis()
        val reminderId = UUID.randomUUID().toString()

        val checklistItems = dsl.checklistItems.mapIndexed { index, text ->
            ChecklistItem(
                id = UUID.randomUUID().toString(),
                componentId = reminderId,
                text = text,
                isCompleted = false,
                sortOrder = index
            )
        }

        val reminder = AuraReminder(
            id = reminderId,
            title = dsl.title,
            body = dsl.body,
            reminderType = dsl.reminderType,
            scheduledAt = dsl.scheduledAtMs,
            repeatCount = dsl.repeatCount,
            intervalMs = dsl.intervalMs,
            cronExpression = dsl.cronExpression,
            linkedTaskId = dsl.linkedTaskId,
            checklistItems = checklistItems,
            links = dsl.links,
            status = ReminderStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )

        repository.insert(reminder)
        return reminder
    }
}

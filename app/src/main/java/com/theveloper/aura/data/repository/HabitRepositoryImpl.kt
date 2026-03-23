package com.theveloper.aura.data.repository

import com.theveloper.aura.data.db.AuraDatabase
import com.theveloper.aura.data.db.HabitSignalEntity
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.domain.repository.HabitRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepositoryImpl @Inject constructor(
    private val db: AuraDatabase
) : HabitRepository {
    private val habitSignalDao = db.habitSignalDao()

    override suspend fun insertSignal(
        taskId: String,
        signalType: SignalType,
        hourOfDay: Int,
        dayOfWeek: Int
    ) {
        val entity = HabitSignalEntity(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            signalType = signalType,
            hourOfDay = hourOfDay,
            dayOfWeek = dayOfWeek,
            recordedAt = System.currentTimeMillis()
        )
        habitSignalDao.insert(entity)
    }

    override suspend fun getSignalsForTask(taskId: String): List<HabitSignalEntity> {
        return habitSignalDao.getSignalsForTask(taskId)
    }

    override suspend fun getSignalsForTasks(taskIds: Collection<String>): List<HabitSignalEntity> {
        if (taskIds.isEmpty()) return emptyList()
        return habitSignalDao.getSignalsForTasks(taskIds.toList())
    }

    override suspend fun getSignalsByTimeWindow(hourOfDay: Int, dayOfWeek: Int): List<HabitSignalEntity> {
        return habitSignalDao.getSignalsByTimeWindow(hourOfDay, dayOfWeek)
    }
}

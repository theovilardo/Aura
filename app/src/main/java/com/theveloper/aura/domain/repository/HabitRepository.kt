package com.theveloper.aura.domain.repository

import com.theveloper.aura.data.db.HabitSignalEntity
import com.theveloper.aura.domain.model.SignalType

interface HabitRepository {
    suspend fun insertSignal(
        taskId: String,
        signalType: SignalType,
        hourOfDay: Int,
        dayOfWeek: Int
    )

    suspend fun getSignalsForTask(taskId: String): List<HabitSignalEntity>

    suspend fun getSignalsByTimeWindow(hourOfDay: Int, dayOfWeek: Int): List<HabitSignalEntity>
}

package com.theveloper.aura.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theveloper.aura.engine.habit.HabitEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class HabitAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val habitEngine: HabitEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            habitEngine.processBatch()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}

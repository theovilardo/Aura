package com.theveloper.aura.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theveloper.aura.engine.habit.HabitEngine
import com.theveloper.aura.engine.memory.MemoryWriter
import com.theveloper.aura.engine.suggestion.SuggestionEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class HabitAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val habitEngine: HabitEngine,
    private val suggestionEngine: SuggestionEngine,
    private val memoryWriter: MemoryWriter
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            habitEngine.processBatch()
            suggestionEngine.evaluatePatterns() // F5-02
            memoryWriter.refresh()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}

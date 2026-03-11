package com.theveloper.aura.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theveloper.aura.domain.repository.SuggestionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SuggestionCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val suggestionRepository: SuggestionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            suggestionRepository.markExpired(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}

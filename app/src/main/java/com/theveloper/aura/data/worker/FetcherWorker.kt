package com.theveloper.aura.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theveloper.aura.data.db.FetcherConfigDao
import com.theveloper.aura.engine.fetcher.FetchResult
import com.theveloper.aura.engine.fetcher.FetcherEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class FetcherWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val fetcherConfigDao: FetcherConfigDao,
    private val fetcherEngine: FetcherEngine
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val activeConfigs = fetcherConfigDao.getActiveConfigs()
            
            for (config in activeConfigs) {
                // Fetcher params are retrieved from the config entity (Map structure)
                val paramsMap = runCatching { 
                    org.json.JSONObject(config.params).let { json ->
                        json.keys().asSequence().associateWith { json.getString(it) }
                    }
                }.getOrDefault(emptyMap())
                
                val result = fetcherEngine.fetch(config.type, paramsMap)
                
                when (result) {
                    is FetchResult.Success -> {
                        fetcherConfigDao.updateLastResult(
                            configId = config.id,
                            json = result.data,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                    is FetchResult.Error -> {
                        Log.e("FetcherWorker", "Error fetching ${config.type}: ${result.reason}")
                        // Retain previous json cache
                    }
                    else -> Unit
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("FetcherWorker", "Error executing worker", e)
            Result.retry()
        }
    }
}

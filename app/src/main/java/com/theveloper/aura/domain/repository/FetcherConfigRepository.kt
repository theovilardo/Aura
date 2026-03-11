package com.theveloper.aura.domain.repository

import com.theveloper.aura.data.db.FetcherConfigEntity

interface FetcherConfigRepository {
    suspend fun insert(config: FetcherConfigEntity)
    suspend fun update(config: FetcherConfigEntity)
    suspend fun delete(config: FetcherConfigEntity)
    suspend fun getConfigsForTask(taskId: String): List<FetcherConfigEntity>
    suspend fun updateLastResult(configId: String, json: String, timestamp: Long)
}

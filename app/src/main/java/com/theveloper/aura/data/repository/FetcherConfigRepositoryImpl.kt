package com.theveloper.aura.data.repository

import com.theveloper.aura.data.db.FetcherConfigDao
import com.theveloper.aura.data.db.FetcherConfigEntity
import com.theveloper.aura.domain.repository.FetcherConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FetcherConfigRepositoryImpl @Inject constructor(
    private val dao: FetcherConfigDao
) : FetcherConfigRepository {
    override suspend fun insert(config: FetcherConfigEntity) {
        dao.insert(config)
    }

    override suspend fun update(config: FetcherConfigEntity) {
        dao.update(config)
    }

    override suspend fun delete(config: FetcherConfigEntity) {
        dao.delete(config)
    }

    override suspend fun getConfigsForTask(taskId: String): List<FetcherConfigEntity> {
        return dao.getConfigsForTask(taskId)
    }

    override suspend fun updateLastResult(configId: String, json: String, timestamp: Long) {
        dao.updateLastResult(configId, json, timestamp)
    }
}

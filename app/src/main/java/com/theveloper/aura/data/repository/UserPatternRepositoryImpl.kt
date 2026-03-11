package com.theveloper.aura.data.repository

import com.theveloper.aura.data.db.AuraDatabase
import com.theveloper.aura.data.db.UserPatternEntity
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.model.UserPattern
import com.theveloper.aura.domain.repository.UserPatternRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPatternRepositoryImpl @Inject constructor(
    private val db: AuraDatabase
) : UserPatternRepository {
    private val dao = db.userPatternDao()

    override suspend fun upsertPattern(pattern: UserPattern) {
        val entity = UserPatternEntity(
            taskType = pattern.taskType,
            hourOfDay = pattern.hourOfDay,
            dayOfWeek = pattern.dayOfWeek,
            completionRate = pattern.completionRate,
            dismissRate = pattern.dismissRate,
            avgDelayMs = pattern.avgDelayMs,
            sampleSize = pattern.sampleSize,
            confidence = pattern.confidence
        )
        dao.insertOrUpdate(entity)
    }

    override suspend fun getPatternsForType(taskType: TaskType): List<UserPattern> {
        return dao.getPatternsForType(taskType).map { entity ->
            UserPattern(
                taskType = entity.taskType,
                hourOfDay = entity.hourOfDay,
                dayOfWeek = entity.dayOfWeek,
                completionRate = entity.completionRate,
                dismissRate = entity.dismissRate,
                avgDelayMs = entity.avgDelayMs,
                sampleSize = entity.sampleSize,
                confidence = entity.confidence
            )
        }
    }

    override suspend fun getBestPatternForType(taskType: TaskType): UserPattern? {
        val patterns = getPatternsForType(taskType)
        return patterns.maxByOrNull { it.completionRate * it.confidence }
    }
}

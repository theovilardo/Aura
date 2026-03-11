package com.theveloper.aura.domain.repository

import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.model.UserPattern

interface UserPatternRepository {
    suspend fun upsertPattern(pattern: UserPattern)
    suspend fun getPatternsForType(taskType: TaskType): List<UserPattern>
    suspend fun getBestPatternForType(taskType: TaskType): UserPattern?
}

package com.theveloper.aura.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserPattern(
    val taskType: TaskType,
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val completionRate: Float,
    val dismissRate: Float,
    val avgDelayMs: Long,
    val sampleSize: Int,
    val confidence: Float
)

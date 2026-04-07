package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.TaskType

data class LLMClassificationContext(
    val intentConfidence: Float = 0f,
    val detectedTaskType: TaskType? = null,
    val extractedDates: List<Long> = emptyList(),
    val extractedNumbers: List<Double> = emptyList(),
    val extractedLocations: List<String> = emptyList(),
    val memoryContext: String = ""
)

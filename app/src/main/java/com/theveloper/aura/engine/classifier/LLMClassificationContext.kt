package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.TaskType

data class LLMClassificationContext(
    val intentHint: TaskType? = null,
    val intentConfidence: Float = 0f,
    val extractedDates: List<Long> = emptyList(),
    val extractedNumbers: List<Double> = emptyList(),
    val extractedLocations: List<String> = emptyList(),
    val memoryContext: String = ""
)

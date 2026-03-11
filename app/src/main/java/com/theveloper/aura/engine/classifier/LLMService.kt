package com.theveloper.aura.engine.classifier

import com.theveloper.aura.engine.dsl.TaskDSLOutput

interface LLMService {
    suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput
}

data class LLMClassificationContext(
    val intentHint: com.theveloper.aura.domain.model.TaskType? = null,
    val intentConfidence: Float = 0f,
    val extractedDates: List<Long> = emptyList(),
    val extractedNumbers: List<Double> = emptyList(),
    val extractedLocations: List<String> = emptyList()
)

package com.theveloper.aura.engine.classifier

data class LLMClassificationContext(
    val intentConfidence: Float = 0f,
    val extractedDates: List<Long> = emptyList(),
    val extractedNumbers: List<Double> = emptyList(),
    val extractedLocations: List<String> = emptyList(),
    val memoryContext: String = ""
)

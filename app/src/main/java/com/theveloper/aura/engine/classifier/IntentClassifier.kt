package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.TaskType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentClassifier @Inject constructor() {
    
    // Placeholder logic for MVP until TFLite is integrated
    fun classify(text: String): IntentResult {
        val lowerText = text.lowercase()
        
        val taskType = when {
            lowerText.contains("viaje") || lowerText.contains("vuelo") || lowerText.contains("vacaciones") -> TaskType.TRAVEL
            lowerText.contains("hábito") || lowerText.contains("todos los días") || lowerText.contains("rutina") -> TaskType.HABIT
            lowerText.contains("peso") || lowerText.contains("agua") || lowerText.contains("ejercicio") -> TaskType.HEALTH
            lowerText.contains("proyecto") || lowerText.contains("app") || lowerText.contains("mvp") -> TaskType.PROJECT
            lowerText.contains("dólar") || lowerText.contains("usd") || lowerText.contains("pagar") -> TaskType.FINANCE
            else -> TaskType.GENERAL
        }

        // TFLite mock confidence
        val confidence = if (taskType == TaskType.GENERAL) 0.5f else 0.9f

        return IntentResult(taskType, confidence)
    }
}

data class IntentResult(
    val taskType: TaskType,
    val confidence: Float
)

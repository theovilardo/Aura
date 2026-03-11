package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.TaskType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentClassifier @Inject constructor() {

    private val rules = listOf(
        ClassificationRule(
            taskType = TaskType.TRAVEL,
            confidence = 0.92f,
            patterns = listOf(
                Regex("\\bviaj\\w*", RegexOption.IGNORE_CASE),
                Regex("\\bvuelo\\w*", RegexOption.IGNORE_CASE),
                Regex("\\bhotel\\w*", RegexOption.IGNORE_CASE),
                Regex("\\bpasaporte\\b", RegexOption.IGNORE_CASE),
                Regex("\\btravel\\b", RegexOption.IGNORE_CASE)
            )
        ),
        ClassificationRule(
            taskType = TaskType.HABIT,
            confidence = 0.91f,
            patterns = listOf(
                Regex("\\bcada\\s+\\d+\\s+(hora|horas|día|dias|días)\\b", RegexOption.IGNORE_CASE),
                Regex("\\btodos?\\s+los\\s+d[ií]as\\b", RegexOption.IGNORE_CASE),
                Regex("\\brutina\\b", RegexOption.IGNORE_CASE),
                Regex("\\bh[aá]bito\\b", RegexOption.IGNORE_CASE),
                Regex("tomar\\s+agua", RegexOption.IGNORE_CASE)
            )
        ),
        ClassificationRule(
            taskType = TaskType.HEALTH,
            confidence = 0.88f,
            patterns = listOf(
                Regex("\\bpeso\\b", RegexOption.IGNORE_CASE),
                Regex("\\bpresi[oó]n\\b", RegexOption.IGNORE_CASE),
                Regex("\\bglucosa\\b", RegexOption.IGNORE_CASE),
                Regex("\\bejercicio\\b", RegexOption.IGNORE_CASE),
                Regex("\\bcalor[ií]as\\b", RegexOption.IGNORE_CASE)
            )
        ),
        ClassificationRule(
            taskType = TaskType.PROJECT,
            confidence = 0.89f,
            patterns = listOf(
                Regex("\\bproyecto\\b", RegexOption.IGNORE_CASE),
                Regex("\\bmvp\\b", RegexOption.IGNORE_CASE),
                Regex("\\broadmap\\b", RegexOption.IGNORE_CASE),
                Regex("\\bentrega\\b", RegexOption.IGNORE_CASE),
                Regex("\\bapp\\b", RegexOption.IGNORE_CASE)
            )
        ),
        ClassificationRule(
            taskType = TaskType.FINANCE,
            confidence = 0.9f,
            patterns = listOf(
                Regex("\\bd[oó]lar\\b", RegexOption.IGNORE_CASE),
                Regex("\\busd\\b", RegexOption.IGNORE_CASE),
                Regex("\\bcotizaci[oó]n\\b", RegexOption.IGNORE_CASE),
                Regex("\\bpagar\\b", RegexOption.IGNORE_CASE),
                Regex("\\bcuota\\b", RegexOption.IGNORE_CASE)
            )
        )
    )

    fun classify(text: String): IntentResult {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            return IntentResult(TaskType.GENERAL, 0f)
        }

        val matches = rules.mapNotNull { rule ->
            val totalMatches = rule.patterns.count { pattern -> pattern.containsMatchIn(normalized) }
            if (totalMatches == 0) {
                null
            } else {
                rule.taskType to (rule.confidence + (totalMatches - 1) * 0.02f).coerceAtMost(0.98f)
            }
        }

        val bestMatch = matches.maxByOrNull { it.second }
        val taskType = bestMatch?.first ?: TaskType.GENERAL
        val confidence = bestMatch?.second ?: 0.45f

        return IntentResult(taskType, confidence)
    }
}

data class IntentResult(
    val taskType: TaskType,
    val confidence: Float
)

private data class ClassificationRule(
    val taskType: TaskType,
    val confidence: Float,
    val patterns: List<Regex>
)

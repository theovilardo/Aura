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
                Regex("\\btravel\\b", RegexOption.IGNORE_CASE),
                Regex("\\bmaleta\\b", RegexOption.IGNORE_CASE),
                Regex("\\baeropuerto\\b", RegexOption.IGNORE_CASE),
                Regex("\\bturismo\\b", RegexOption.IGNORE_CASE),
                Regex("\\bitinerario\\b", RegexOption.IGNORE_CASE),
                Regex("\\breserva\\w*", RegexOption.IGNORE_CASE)
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
                Regex("tomar\\s+agua", RegexOption.IGNORE_CASE),
                Regex("\\bmeditar\\b", RegexOption.IGNORE_CASE),
                Regex("\\bmeditaci[oó]n\\b", RegexOption.IGNORE_CASE),
                Regex("\\bcada\\s+ma[ñn]ana\\b", RegexOption.IGNORE_CASE),
                Regex("\\bcada\\s+noche\\b", RegexOption.IGNORE_CASE),
                Regex("\\bdiariamente\\b", RegexOption.IGNORE_CASE),
                Regex("\\bdiario\\b", RegexOption.IGNORE_CASE),
                Regex("\\bstreak\\b", RegexOption.IGNORE_CASE)
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
                Regex("\\bcalor[ií]as\\b", RegexOption.IGNORE_CASE),
                Regex("\\bgym\\b", RegexOption.IGNORE_CASE),
                Regex("\\bgimnasio\\b", RegexOption.IGNORE_CASE),
                Regex("\\bcorr[ei]\\w*", RegexOption.IGNORE_CASE),
                Regex("\\bentrena\\w*", RegexOption.IGNORE_CASE),
                Regex("\\bdieta\\b", RegexOption.IGNORE_CASE),
                Regex("\\bsalud\\b", RegexOption.IGNORE_CASE),
                Regex("\\bpasos\\b", RegexOption.IGNORE_CASE),
                Regex("\\badelgazar\\b", RegexOption.IGNORE_CASE),
                Regex("\\bmedic\\w*", RegexOption.IGNORE_CASE),
                Regex("\\btratamiento\\b", RegexOption.IGNORE_CASE),
                Regex("\\bsue[ñn]o\\b", RegexOption.IGNORE_CASE)
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
                Regex("\\bapp\\b", RegexOption.IGNORE_CASE),
                Regex("\\bestudiar\\b", RegexOption.IGNORE_CASE),
                Regex("\\baprender\\b", RegexOption.IGNORE_CASE),
                Regex("\\bcurso\\b", RegexOption.IGNORE_CASE),
                Regex("\\btesis\\b", RegexOption.IGNORE_CASE),
                Regex("\\binforme\\b", RegexOption.IGNORE_CASE),
                Regex("\\bpresentaci[oó]n\\b", RegexOption.IGNORE_CASE),
                Regex("\\bexamen\\b", RegexOption.IGNORE_CASE),
                Regex("\\bdesarrollar\\b", RegexOption.IGNORE_CASE),
                Regex("\\breuni[oó]n\\b", RegexOption.IGNORE_CASE),
                Regex("\\binvestigar\\b", RegexOption.IGNORE_CASE),
                Regex("\\blanzamiento\\b", RegexOption.IGNORE_CASE)
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
                Regex("\\bcuota\\b", RegexOption.IGNORE_CASE),
                Regex("\\bahorrar\\b", RegexOption.IGNORE_CASE),
                Regex("\\bahorro\\b", RegexOption.IGNORE_CASE),
                Regex("\\bpresupuesto\\b", RegexOption.IGNORE_CASE),
                Regex("\\bdeuda\\b", RegexOption.IGNORE_CASE),
                Regex("\\bgastos?\\b", RegexOption.IGNORE_CASE),
                Regex("\\bdinero\\b", RegexOption.IGNORE_CASE),
                Regex("\\bfinanza\\w*", RegexOption.IGNORE_CASE),
                Regex("\\balquiler\\b", RegexOption.IGNORE_CASE),
                Regex("\\bfactura\\b", RegexOption.IGNORE_CASE),
                Regex("\\bvencim\\w*", RegexOption.IGNORE_CASE)
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

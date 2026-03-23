package com.theveloper.aura.engine.router

import com.theveloper.aura.engine.classifier.ExtractedEntities
import com.theveloper.aura.engine.classifier.LLMClassificationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ComplexityScore(
    val score: Float,
    val tier: ComplexityTier,
    val factors: Map<ComplexityFactor, Float>,
    val reasons: List<String>
)

enum class ComplexityTier {
    SIMPLE,     // 0.0-3.0: lightweight local model sufficient
    MODERATE,   // 3.1-6.0: capable local model or fast API
    COMPLEX,    // 6.1-8.5: powerful API needed
    HEAVY       // 8.6-10.0: most capable model available
}

enum class ComplexityFactor {
    INPUT_LENGTH,
    MULTI_STEP,
    SYSTEM_ACCESS,
    CONTEXT_DEPTH,
    SPECIALIZED_DOMAIN,
    STRUCTURED_OUTPUT,
    ATTACHMENTS,
    ENTITY_DENSITY
}

@Singleton
class ComplexityScorer @Inject constructor() {

    fun score(
        input: String,
        entities: ExtractedEntities? = null,
        context: LLMClassificationContext? = null
    ): ComplexityScore {
        val factors = mutableMapOf<ComplexityFactor, Float>()
        val reasons = mutableListOf<String>()

        // Factor 1 — Input length
        val lengthScore = when {
            input.length > 2000 -> 2.5f
            input.length > 500 -> 1.5f
            input.length > 100 -> 0.5f
            else -> 0f
        }
        if (lengthScore > 0) {
            factors[ComplexityFactor.INPUT_LENGTH] = lengthScore
            reasons += "Long input (${input.length} chars)"
        }

        // Factor 2 — Multi-step reasoning
        val multiStepScore = if (containsMultiStep(input)) {
            reasons += "Requires multi-step reasoning"
            2f
        } else 0f
        factors[ComplexityFactor.MULTI_STEP] = multiStepScore

        // Factor 3 — Entity density (more entities = more complex)
        val entityCount = (entities?.dateTimes?.size ?: 0) +
                (entities?.numbers?.size ?: 0) +
                (entities?.locations?.size ?: 0)
        val entityScore = when {
            entityCount >= 5 -> 2f
            entityCount >= 3 -> 1f
            entityCount >= 1 -> 0.5f
            else -> 0f
        }
        if (entityScore > 0) {
            factors[ComplexityFactor.ENTITY_DENSITY] = entityScore
            reasons += "Contains $entityCount extracted entities"
        }

        // Factor 4 — Context depth
        val contextScore = when {
            (context?.memoryContext?.length ?: 0) > 500 -> 1.5f
            (context?.memoryContext?.length ?: 0) > 200 -> 0.5f
            else -> 0f
        }
        if (contextScore > 0) {
            factors[ComplexityFactor.CONTEXT_DEPTH] = contextScore
            reasons += "Deep conversation context"
        }

        // Factor 5 — Specialized domain
        val (domainScore, domainName) = detectSpecializedDomain(input)
        if (domainScore > 0) {
            factors[ComplexityFactor.SPECIALIZED_DOMAIN] = domainScore
            reasons += "Specialized domain: $domainName"
        }

        // Factor 6 — Structured output
        val structureScore = if (requiresComplexOutput(input)) {
            reasons += "Requires complex structured output"
            1f
        } else 0f
        factors[ComplexityFactor.STRUCTURED_OUTPUT] = structureScore

        val total = factors.values.sum().coerceIn(0f, 10f)

        return ComplexityScore(
            score = total,
            tier = when {
                total <= 3f -> ComplexityTier.SIMPLE
                total <= 6f -> ComplexityTier.MODERATE
                total <= 8.5f -> ComplexityTier.COMPLEX
                else -> ComplexityTier.HEAVY
            },
            factors = factors,
            reasons = reasons
        )
    }

    private fun containsMultiStep(input: String): Boolean {
        val indicators = listOf(
            "primero", "luego", "después", "finalmente", "paso a paso",
            "first", "then", "after that", "finally", "step by step",
            "y también", "además", "por otro lado",
            "and also", "additionally", "on the other hand"
        )
        val lower = input.lowercase()
        return indicators.count { lower.contains(it) } >= 2
    }

    private fun detectSpecializedDomain(input: String): Pair<Float, String> {
        val domains = mapOf(
            listOf("código", "función", "bug", "deploy", "git", "code", "function", "api") to (1.5f to "programming"),
            listOf("diagnóstico", "síntoma", "tratamiento", "médico", "diagnosis", "symptom") to (2f to "medicine"),
            listOf("contrato", "legal", "ley", "demanda", "contract", "lawsuit") to (2f to "legal"),
            listOf("inversión", "portfolio", "acciones", "trading", "investment", "stocks") to (1.5f to "finance")
        )
        val lower = input.lowercase()
        for ((keywords, result) in domains) {
            if (keywords.any { lower.contains(it) }) return result
        }
        return 0f to ""
    }

    private fun requiresComplexOutput(input: String): Boolean {
        val indicators = listOf(
            "tabla", "informe", "reporte", "json", "xml", "estructura",
            "table", "report", "structured", "format", "spreadsheet"
        )
        return indicators.any { input.lowercase().contains(it) }
    }
}

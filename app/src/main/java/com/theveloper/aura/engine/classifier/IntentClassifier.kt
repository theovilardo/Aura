package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.TaskType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight pre-classifier based exclusively on language-agnostic structural signals.
 *
 * This classifier does NOT use any keyword patterns tied to a specific language. It detects
 * universal structural markers (currency symbols, date/time formats, set×rep notation, etc.)
 * to produce a weak TaskType hint and a low confidence value.
 *
 * Confidence is intentionally capped at 0.55 — structural signals are coarse hints.
 * The LLM is always preferred when available and performs the real classification.
 * This classifier only provides a starting point for the rules-only fallback path.
 */
@Singleton
class IntentClassifier @Inject constructor() {

    fun classify(text: String): IntentResult {
        val normalized = text.trim()
        if (normalized.isBlank()) return IntentResult(TaskType.GENERAL, 0f)

        val hasCurrencySymbol = CURRENCY_SYMBOL_PATTERN.containsMatchIn(normalized)
        val hasCurrencyCode   = CURRENCY_CODE_PATTERN.containsMatchIn(normalized)
        val hasIsoDate        = ISO_DATE_PATTERN.containsMatchIn(normalized)
        val hasTime           = TIME_PATTERN.containsMatchIn(normalized)
        val hasSetRep         = SET_REP_PATTERN.containsMatchIn(normalized)
        val hasNumericAmount  = NUMERIC_AMOUNT_PATTERN.containsMatchIn(normalized)

        val taskType = when {
            hasCurrencySymbol || hasCurrencyCode -> TaskType.FINANCE
            hasSetRep                            -> TaskType.HEALTH
            hasIsoDate || hasTime                -> TaskType.EVENT
            else                                 -> TaskType.GENERAL
        }

        // Confidence never exceeds 0.55 — structural signals are weak, language-agnostic hints.
        // The LLM always takes precedence when available regardless of this value.
        val confidence = when {
            (hasCurrencySymbol || hasCurrencyCode) && hasNumericAmount -> 0.55f
            hasCurrencySymbol || hasCurrencyCode                       -> 0.45f
            hasSetRep                                                  -> 0.48f
            hasIsoDate && hasTime                                      -> 0.50f
            hasIsoDate || hasTime                                      -> 0.44f
            else                                                       -> 0.30f
        }

        return IntentResult(taskType, confidence)
    }

    private companion object {
        /** Universal currency symbols: $, €, £, ¥, ₹, ₽, ₩, ₴, ₦ */
        val CURRENCY_SYMBOL_PATTERN = Regex("""[$€£¥₹₽₩₴₦]""")

        /** ISO 4217 currency codes commonly written in plain text */
        val CURRENCY_CODE_PATTERN = Regex(
            """\b(USD|EUR|GBP|ARS|BRL|JPY|CNY|MXN|CLP|COP|PEN|CAD|AUD|CHF|INR|KRW|SEK|NOK|DKK)\b""",
            RegexOption.IGNORE_CASE
        )

        /**
         * Date patterns — works across locales:
         *   ISO 8601:          2025-12-25
         *   Common DD/MM/YYYY: 25/12/2025 or 25-12-2025 or 25.12.2025
         *   Short MM/DD:       12/25
         */
        val ISO_DATE_PATTERN = Regex(
            """\b\d{4}[-/]\d{1,2}[-/]\d{1,2}\b|\b\d{1,2}[/.\-]\d{1,2}([/.\-]\d{2,4})?\b"""
        )

        /**
         * Time patterns: 14:30, 9:00am, 3:00 PM
         * The colon is the universal separator — language-agnostic.
         */
        val TIME_PATTERN = Regex("""\b\d{1,2}:\d{2}\s*(am|pm)?\b""", RegexOption.IGNORE_CASE)

        /**
         * Workout/gym set-rep notation — universal across languages:
         *   3x10, 4×8, 3 X 12, 10 reps, 3 sets, 3 series
         * "reps", "sets", "series" are widely used in fitness contexts internationally.
         */
        val SET_REP_PATTERN = Regex(
            """\b\d+\s*[xX×]\s*\d+\b|\b\d+\s*(sets?|reps?|series?)\b""",
            RegexOption.IGNORE_CASE
        )

        /** A number that looks like a monetary amount: 1500, 1.500, 1,500.00 */
        val NUMERIC_AMOUNT_PATTERN = Regex("""\b\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})?\b""")
    }
}

data class IntentResult(
    val taskType: TaskType,
    val confidence: Float
)

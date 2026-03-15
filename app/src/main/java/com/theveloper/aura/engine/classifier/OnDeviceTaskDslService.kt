package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.ReminderDSL
import com.theveloper.aura.engine.dsl.TaskComponentCatalog
import com.theveloper.aura.engine.dsl.TaskComponentContext
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class OnDeviceTaskDslRequest(
    val input: String,
    val intentResult: IntentResult,
    val extractedEntities: ExtractedEntities,
    val llmContext: LLMClassificationContext
)

data class OnDeviceTaskDslResult(
    val dsl: TaskDSLOutput,
    val confidence: Float,
    val source: TaskGenerationSource
)

interface OnDeviceTaskDslService {
    suspend fun compose(request: OnDeviceTaskDslRequest): OnDeviceTaskDslResult
}

@Singleton
class HeuristicOnDeviceTaskDslService @Inject constructor() : OnDeviceTaskDslService {

    override suspend fun compose(request: OnDeviceTaskDslRequest): OnDeviceTaskDslResult {
        val input = request.input.trim()
        val signals = PromptSignals.from(input, request.extractedEntities)
        val taskType = resolveTaskType(request.intentResult, signals)
        val title = TaskDSLBuilder.buildTitle(input, taskType)
        val targetDateMs = request.extractedEntities.dateTimes.firstOrNull()
        val context = TaskComponentContext(
            input = input,
            title = title,
            taskType = taskType,
            targetDateMs = targetDateMs,
            numbers = request.extractedEntities.numbers,
            locations = request.extractedEntities.locations
        )

        val defaultIds = TaskDSLBuilder.defaultTemplateIdsFor(taskType, request.extractedEntities, input)
        val templateIds = linkedSetOf<String>().apply {
            addAll(defaultIds)
            addAll(additionalTemplatesFor(taskType, signals))
            if (isEmpty()) add("notes_brain_dump")
        }.toList()

        val now = System.currentTimeMillis()
        val (components, reminders) = TaskComponentCatalog.buildSelection(
            templateIds = templateIds,
            now = now,
            context = context
        )

        val priority = inferPriority(input, targetDateMs, signals)
        val confidence = inferConfidence(request.intentResult.confidence, signals, templateIds.size)
        val source = if (signals.enrichesUi || templateIds.size > defaultIds.size) {
            TaskGenerationSource.LOCAL_AI
        } else {
            TaskGenerationSource.RULES
        }

        return OnDeviceTaskDslResult(
            dsl = TaskDSLOutput(
                title = title,
                type = taskType,
                priority = priority,
                targetDateMs = targetDateMs,
                components = components,
                reminders = normalizeReminders(reminders)
            ),
            confidence = confidence,
            source = source
        )
    }

    private fun resolveTaskType(intentResult: IntentResult, signals: PromptSignals): TaskType {
        val scores = mutableMapOf<TaskType, Float>().withDefault { 0f }
        TaskType.entries.forEach { scores[it] = if (it == intentResult.taskType) intentResult.confidence else 0.05f }

        fun bump(t: TaskType, d: Float) { scores[t] = scores.getValue(t) + d }

        // Finance: currency symbols and codes are unambiguous cross-language signals
        if (signals.hasCurrencySymbol || signals.hasCurrencyCode) bump(TaskType.FINANCE, 0.42f)
        if (signals.hasLargeAmount && (signals.hasCurrencySymbol || signals.hasCurrencyCode)) bump(TaskType.FINANCE, 0.10f)

        // Health: set×rep and unit notation are internationally recognized in fitness contexts
        if (signals.hasSetRepNotation) bump(TaskType.HEALTH, 0.42f)
        if (signals.hasUnitAnnotation && !signals.hasCurrencyCode) bump(TaskType.HEALTH, 0.20f)

        // Event: a single specific date/time strongly suggests an appointment or deadline.
        // 0.48 bump overrides a weak GENERAL baseline (≤ 0.46) from IntentClassifier.
        if (signals.hasExtractedDate && !signals.hasMultipleDates) bump(TaskType.EVENT, 0.48f)

        // Travel: multiple dates (itinerary-like) + location is a travel pattern
        if (signals.hasMultipleDates) { bump(TaskType.TRAVEL, 0.28f); bump(TaskType.EVENT, 0.12f) }
        if (signals.hasLocation && signals.hasExtractedDate) bump(TaskType.TRAVEL, 0.22f)

        // Goal/Project: structured multi-line text or list format suggests a plan
        if (signals.isMultiLine || signals.hasListStructure) bump(TaskType.PROJECT, 0.14f)
        if (signals.hasPercentage) bump(TaskType.GOAL, 0.18f)

        return scores.maxByOrNull { it.value }?.key ?: intentResult.taskType
    }

    private fun additionalTemplatesFor(taskType: TaskType, signals: PromptSignals): List<String> {
        return buildList {
            // Financial components
            if (signals.hasCurrencySymbol || signals.hasCurrencyCode) {
                add("metric_budget_target")
                add("progress_budget")
            }
            if (signals.hasLargeAmount && (signals.hasCurrencySymbol || signals.hasCurrencyCode)) {
                add("payment_countdown")
            }

            // Fitness/health metric components
            if (signals.hasSetRepNotation || (signals.hasUnitAnnotation && !signals.hasCurrencyCode)) {
                add("metric_weight")
            }

            // Temporal components — add type-appropriate countdown
            if (signals.hasExtractedDate || signals.hasTimePattern) {
                when (taskType) {
                    TaskType.EVENT   -> add("event_notes")
                    TaskType.TRAVEL  -> add("travel_countdown")
                    TaskType.FINANCE -> add("payment_countdown")
                    else             -> add("deadline_countdown")
                }
            }

            // Location → travel itinerary or weather feed
            if (signals.hasLocation) {
                if (taskType == TaskType.TRAVEL) add("travel_itinerary_notes")
                else add("feed_weather")
            }

            // List/multi-line structure → action checklist
            if (signals.isMultiLine || signals.hasListStructure) add("action_checklist")

            // Percentage → progress tracking
            if (signals.hasPercentage) add("progress_manual")
        }.distinct()
    }

    private fun inferPriority(input: String, targetDateMs: Long?, signals: PromptSignals): Int {
        return when {
            URGENT_PATTERN.containsMatchIn(input) -> 3
            targetDateMs != null || signals.hasExtractedDate -> 2
            signals.hasCurrencySymbol || signals.hasCurrencyCode || signals.hasSetRepNotation -> 1
            else -> 0
        }
    }

    private fun inferConfidence(intentConfidence: Float, signals: PromptSignals, templateCount: Int): Float {
        val signalBoost = (signals.signalCount * 0.05f).coerceAtMost(0.25f)
        val templateBoost = max(0, templateCount - 1) * 0.03f
        return (intentConfidence.coerceAtLeast(0.38f) + signalBoost + templateBoost).coerceAtMost(0.96f)
    }

    private fun normalizeReminders(reminders: List<ReminderDSL>): List<ReminderDSL> {
        return reminders
            .sortedBy { it.scheduledAtMs }
            .distinctBy { it.scheduledAtMs to it.intervalDays }
    }

    private companion object {
        /** ASAP is universally used in professional contexts; !! is a structural urgency marker. */
        val URGENT_PATTERN = Regex("""\basap\b|!!""", RegexOption.IGNORE_CASE)
    }
}

/**
 * Language-agnostic structural signals derived from the input text and ML-extracted entities.
 *
 * Every signal here is detectable without knowing the user's language:
 *   - Currency symbols and ISO codes are universal
 *   - Set×rep notation (3x10, 4 sets) is internationally used in fitness
 *   - Unit annotations (kg, km, ml) are SI/international units
 *   - Date/time/location signals come from the ML-based EntityExtractor
 *   - Structural signals (list markers, multi-line) are character-level
 *
 * NO keyword detection tied to any specific language.
 */
private data class PromptSignals(
    // Finance
    val hasCurrencySymbol: Boolean,
    val hasCurrencyCode: Boolean,
    val hasLargeAmount: Boolean,        // any extracted number > 100

    // Health / fitness
    val hasSetRepNotation: Boolean,     // 3x10, 4×8, 3 sets, 10 reps, 3 series
    val hasUnitAnnotation: Boolean,     // number + kg/km/ml/lb/h/min/steps/reps

    // Temporal (from ML entities — language-agnostic)
    val hasExtractedDate: Boolean,
    val hasMultipleDates: Boolean,      // > 1 extracted date → itinerary / recurring pattern

    // Location (from ML entities — language-agnostic)
    val hasLocation: Boolean,

    // Structural (character-level — language-agnostic)
    val hasTimePattern: Boolean,        // HH:MM format
    val isMultiLine: Boolean,           // 3+ non-blank lines
    val hasListStructure: Boolean,      // lines starting with -, *, •, 1., 2.
    val hasPercentage: Boolean,         // number + %
    val hasMultipleNumbers: Boolean,    // 3+ distinct extracted numbers
) {
    val signalCount: Int = listOf(
        hasCurrencySymbol, hasCurrencyCode, hasLargeAmount,
        hasSetRepNotation, hasUnitAnnotation,
        hasExtractedDate, hasMultipleDates, hasLocation,
        hasTimePattern, isMultiLine, hasListStructure,
        hasPercentage, hasMultipleNumbers
    ).count { it }

    val enrichesUi: Boolean = listOf(
        hasCurrencySymbol, hasCurrencyCode,
        hasSetRepNotation, hasUnitAnnotation,
        isMultiLine, hasListStructure,
        hasMultipleDates, hasLocation
    ).any { it }

    companion object {
        fun from(input: String, entities: ExtractedEntities): PromptSignals {
            return PromptSignals(
                hasCurrencySymbol  = CURRENCY_SYMBOL.containsMatchIn(input),
                hasCurrencyCode    = CURRENCY_CODE.containsMatchIn(input),
                hasLargeAmount     = entities.numbers.any { it > 100.0 },
                hasSetRepNotation  = SET_REP.containsMatchIn(input),
                hasUnitAnnotation  = UNIT_ANNOTATION.containsMatchIn(input),
                hasExtractedDate   = entities.dateTimes.isNotEmpty(),
                hasMultipleDates   = entities.dateTimes.size > 1,
                hasLocation        = entities.locations.isNotEmpty(),
                hasTimePattern     = TIME_PATTERN.containsMatchIn(input),
                isMultiLine        = input.lines().count { it.isNotBlank() } > 2,
                hasListStructure   = LIST_STRUCTURE.containsMatchIn(input),
                hasPercentage      = PERCENTAGE.containsMatchIn(input),
                hasMultipleNumbers = entities.numbers.size >= 3
            )
        }

        /** Universal currency symbols */
        private val CURRENCY_SYMBOL = Regex("""[$€£¥₹₽₩₴₦]""")

        /** ISO 4217 codes commonly written in plain text */
        private val CURRENCY_CODE = Regex(
            """\b(USD|EUR|GBP|ARS|BRL|JPY|CNY|MXN|CLP|COP|PEN|CAD|AUD|CHF|INR|KRW|SEK|NOK|DKK)\b""",
            RegexOption.IGNORE_CASE
        )

        /** Workout/gym set-rep notation — used internationally across all fitness cultures */
        private val SET_REP = Regex(
            """\b\d+\s*[xX×]\s*\d+\b|\b\d+\s*(sets?|reps?|series?)\b""",
            RegexOption.IGNORE_CASE
        )

        /** Number followed by SI/international unit abbreviation */
        private val UNIT_ANNOTATION = Regex(
            """\b\d+(?:\.\d+)?\s*(kg|lb|km|mi|ml|oz|kcal|cal|h\b|hr\b|min\b|steps|reps)\b""",
            RegexOption.IGNORE_CASE
        )

        /** Time in HH:MM format — the colon separator is universal */
        private val TIME_PATTERN = Regex("""\b\d{1,2}:\d{2}\s*(am|pm)?\b""", RegexOption.IGNORE_CASE)

        /** Lines starting with list markers — works in any language */
        private val LIST_STRUCTURE = Regex("""(?:^|\n)\s*[-*•]\s+\S|(?:^|\n)\s*\d+[.)]\s+\S""")

        /** Percentage sign — universal */
        private val PERCENTAGE = Regex("""\b\d+\s*%""")
    }
}

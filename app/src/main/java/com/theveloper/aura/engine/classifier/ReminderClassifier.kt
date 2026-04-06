package com.theveloper.aura.engine.classifier

import android.util.Log
import com.theveloper.aura.domain.model.ReminderType
import com.theveloper.aura.engine.dsl.ReminderDSLOutput
import com.theveloper.aura.engine.llm.extractLikelyJsonBlock
import com.theveloper.aura.engine.llm.LLMServiceFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifier for standalone reminders.
 *
 * Pipeline:
 * 1. Extract entities (dates, times) via [EntityExtractorService].
 * 2. Detect [ReminderType] from structural signals.
 * 3. If LLM available: call with `reminder_prompt.txt` for enriched output.
 * 4. Heuristic fallback: build from extracted entities.
 */
@Singleton
class ReminderClassifier @Inject constructor(
    private val entityExtractorService: EntityExtractorService,
    private val llmServiceFactory: LLMServiceFactory
) {

    suspend fun classify(input: String): ReminderGenerationResult {
        Log.d(TAG, "classify(input=${input.take(60)}…)")

        val normalizedInput = input.trim()
        val entities = entityExtractorService.extract(normalizedInput)

        // Detect reminder type from structural signals
        val reminderType = detectReminderType(normalizedInput, entities)
        val scheduledAt = entities.dateTimes.firstOrNull() ?: 0L

        // Try LLM path first
        return try {
            val route = llmServiceFactory.resolvePrimaryService()
            if (route.tier != com.theveloper.aura.engine.llm.LLMTier.RULES_ONLY) {
                val context = LLMClassificationContext(
                    extractedDates = entities.dateTimes,
                    extractedNumbers = entities.numbers,
                    extractedLocations = entities.locations
                )
                val prompt = buildLLMPrompt(normalizedInput, context)
                val rawJson = route.service.complete(prompt)
                val dsl = parseLLMResponse(rawJson, normalizedInput, reminderType, scheduledAt)
                ReminderGenerationResult(
                    dsl = dsl,
                    source = route.source
                )
            } else {
                buildHeuristicResult(normalizedInput, reminderType, scheduledAt, entities)
            }
        } catch (e: Exception) {
            Log.w(TAG, "LLM path failed, using heuristic", e)
            buildHeuristicResult(normalizedInput, reminderType, scheduledAt, entities)
        }
    }

    private fun detectReminderType(input: String, entities: ExtractedEntities): ReminderType {
        val lower = input.lowercase()
        // Cyclical patterns: "every", "cada", "weekly", "daily", "monthly"
        val cyclicalSignals = listOf("every ", "cada ", "weekly", "daily", "monthly",
            "mensual", "semanal", "diario", "todos los", "each ")
        if (cyclicalSignals.any { lower.contains(it) }) return ReminderType.CYCLICAL

        // Repeating patterns: explicit count like "3 times", "5 veces"
        val repeatingRegex = Regex("""(\d+)\s*(times|veces|repeticiones)""", RegexOption.IGNORE_CASE)
        if (repeatingRegex.containsMatchIn(lower)) return ReminderType.REPEATING

        return ReminderType.ONE_TIME
    }

    private fun buildLLMPrompt(input: String, context: LLMClassificationContext): String {
        return buildString {
            appendLine("You are a reminder creation assistant. Output ONLY valid JSON, no markdown.")
            appendLine("LANGUAGE RULE: All user-facing text MUST match the input language.")
            appendLine()
            appendLine("Given this user input, create a reminder JSON object:")
            appendLine("Input: \"$input\"")
            if (context.extractedDates.isNotEmpty()) {
                appendLine("Extracted dates (epoch ms): ${context.extractedDates}")
            }
            appendLine()
            appendLine("JSON schema:")
            appendLine("""{
  "title": "short reminder title",
  "body": "markdown body with details",
  "reminder_type": "ONE_TIME|REPEATING|CYCLICAL",
  "scheduled_at_ms": 0,
  "repeat_count": 0,
  "interval_ms": 0,
  "cron_expression": "",
  "checklist_items": ["item1", "item2"],
  "links": []
}""")
        }
    }

    private fun parseLLMResponse(
        rawJson: String,
        input: String,
        fallbackType: ReminderType,
        fallbackScheduledAt: Long
    ): ReminderDSLOutput {
        return try {
            val json = rawJson.extractLikelyJsonBlock()
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(json)
                as? kotlinx.serialization.json.JsonObject ?: return buildFallbackDsl(input, fallbackType, fallbackScheduledAt)

            fun str(key: String) = (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            fun long(key: String) = (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 0L
            fun int(key: String) = (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
            fun strList(key: String) = (obj[key] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?: emptyList()

            val typeStr = str("reminder_type").uppercase()
            val type = runCatching { ReminderType.valueOf(typeStr) }.getOrDefault(fallbackType)

            ReminderDSLOutput(
                title = str("title").ifBlank { input.take(50) },
                body = str("body"),
                reminderType = type,
                scheduledAtMs = long("scheduled_at_ms").takeIf { it > 0 } ?: fallbackScheduledAt,
                repeatCount = int("repeat_count"),
                intervalMs = long("interval_ms"),
                cronExpression = str("cron_expression"),
                checklistItems = strList("checklist_items"),
                links = strList("links")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse LLM reminder response", e)
            buildFallbackDsl(input, fallbackType, fallbackScheduledAt)
        }
    }

    private fun buildHeuristicResult(
        input: String,
        reminderType: ReminderType,
        scheduledAt: Long,
        entities: ExtractedEntities
    ): ReminderGenerationResult {
        val dsl = buildFallbackDsl(input, reminderType, scheduledAt)
        val warnings = mutableListOf<String>()
        if (scheduledAt == 0L) warnings.add("No date/time detected — you'll need to set the schedule manually.")
        return ReminderGenerationResult(
            dsl = dsl,
            source = TaskGenerationSource.RULES,
            warnings = warnings
        )
    }

    private fun buildFallbackDsl(input: String, type: ReminderType, scheduledAt: Long): ReminderDSLOutput {
        // Heuristic interval for cyclical reminders
        val intervalMs = when (type) {
            ReminderType.CYCLICAL -> 24 * 60 * 60 * 1000L // default: daily
            ReminderType.REPEATING -> 24 * 60 * 60 * 1000L
            ReminderType.ONE_TIME -> 0L
        }
        return ReminderDSLOutput(
            title = input.take(80),
            body = "",
            reminderType = type,
            scheduledAtMs = scheduledAt,
            intervalMs = intervalMs
        )
    }

    companion object {
        private const val TAG = "ReminderClassifier"
    }
}

data class ReminderGenerationResult(
    val dsl: ReminderDSLOutput,
    val source: TaskGenerationSource,
    val warnings: List<String> = emptyList()
)

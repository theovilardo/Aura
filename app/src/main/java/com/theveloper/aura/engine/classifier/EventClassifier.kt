package com.theveloper.aura.engine.classifier

import android.util.Log
import com.theveloper.aura.domain.model.EventSubActionType
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.EventDSLOutput
import com.theveloper.aura.engine.dsl.EventSubActionDSL
import com.theveloper.aura.engine.llm.extractLikelyJsonBlock
import com.theveloper.aura.engine.llm.LLMServiceFactory
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifier for time-span events (festivals, conferences, sprints, etc.).
 *
 * Pipeline:
 * 1. Extract date range from input.
 * 2. Detect sub-actions (metric tracking, notifications, etc.).
 * 3. Build components via catalog templates.
 * 4. LLM enrichment or heuristic fallback.
 */
@Singleton
class EventClassifier @Inject constructor(
    private val entityExtractorService: EntityExtractorService,
    private val llmServiceFactory: LLMServiceFactory
) {

    suspend fun classify(input: String): EventGenerationResult {
        Log.d(TAG, "classify(input=${input.take(60)}…)")

        val normalizedInput = input.trim()
        val entities = entityExtractorService.extract(normalizedInput)

        // Try to detect start/end from extracted dates
        val sortedDates = entities.dateTimes.sorted()
        val startAt = sortedDates.firstOrNull() ?: 0L
        val endAt = if (sortedDates.size >= 2) sortedDates.last() else 0L

        // Try LLM path
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
                val dsl = parseLLMResponse(rawJson, normalizedInput, startAt, endAt)
                EventGenerationResult(dsl = dsl, source = route.source)
            } else {
                buildHeuristicResult(normalizedInput, startAt, endAt, entities)
            }
        } catch (e: Exception) {
            Log.w(TAG, "LLM path failed, using heuristic", e)
            buildHeuristicResult(normalizedInput, startAt, endAt, entities)
        }
    }

    private fun buildLLMPrompt(input: String, context: LLMClassificationContext): String {
        return buildString {
            appendLine("You are an event creation assistant. Output ONLY valid JSON, no markdown.")
            appendLine("LANGUAGE RULE: All user-facing text MUST match the input language.")
            appendLine()
            appendLine("Given this user input, create an event with sub-actions:")
            appendLine("Input: \"$input\"")
            if (context.extractedDates.isNotEmpty()) {
                appendLine("Extracted dates (epoch ms): ${context.extractedDates}")
            }
            appendLine()
            appendLine("Events have a time span and sub-actions that execute during the event.")
            appendLine("Sub-action types: NOTIFICATION, METRIC_PROMPT, AUTOMATION, CHECKLIST_REMIND")
            appendLine()
            appendLine("JSON schema:")
            appendLine("""{
  "title": "event title",
  "description": "event description",
  "start_at_ms": 0,
  "end_at_ms": 0,
  "sub_actions": [
    {
      "type": "METRIC_PROMPT|NOTIFICATION|AUTOMATION|CHECKLIST_REMIND",
      "title": "action title",
      "interval_ms": 3600000,
      "prompt": "question to ask user",
      "enabled": true
    }
  ],
  "components": [
    {
      "type": "METRIC_TRACKER|CHECKLIST|NOTES|COUNTDOWN",
      "sort_order": 0,
      "config": {}
    }
  ]
}""")
        }
    }

    private fun parseLLMResponse(
        rawJson: String,
        input: String,
        fallbackStart: Long,
        fallbackEnd: Long
    ): EventDSLOutput {
        return try {
            val json = rawJson.extractLikelyJsonBlock()
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(json)
                as? kotlinx.serialization.json.JsonObject
                ?: return buildFallbackDsl(input, fallbackStart, fallbackEnd)

            fun str(key: String) = (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            fun long(key: String) = (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 0L

            val title = str("title").ifBlank { input.take(50) }
            val description = str("description")
            val startMs = long("start_at_ms").takeIf { it > 0 } ?: fallbackStart
            val endMs = long("end_at_ms").takeIf { it > 0 } ?: fallbackEnd

            // Parse sub-actions
            val subActionsArray = obj["sub_actions"] as? kotlinx.serialization.json.JsonArray
            val subActions = subActionsArray?.mapNotNull { element ->
                val saObj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val typeStr = (saObj["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.uppercase() ?: return@mapNotNull null
                val saType = runCatching { EventSubActionType.valueOf(typeStr) }.getOrNull() ?: return@mapNotNull null
                val saTitle = (saObj["title"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                val interval = (saObj["interval_ms"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 0L
                val prompt = (saObj["prompt"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                EventSubActionDSL(
                    type = saType,
                    title = saTitle,
                    intervalMs = interval,
                    prompt = prompt,
                    enabled = true
                )
            } ?: emptyList()

            // Parse components
            val componentsArray = obj["components"] as? kotlinx.serialization.json.JsonArray
            val components = componentsArray?.mapIndexedNotNull { index, element ->
                val cObj = element as? kotlinx.serialization.json.JsonObject ?: return@mapIndexedNotNull null
                val typeStr = (cObj["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.uppercase() ?: return@mapIndexedNotNull null
                val cType = runCatching { com.theveloper.aura.domain.model.ComponentType.valueOf(typeStr) }.getOrNull()
                    ?: return@mapIndexedNotNull null
                val configObj = cObj["config"] as? kotlinx.serialization.json.JsonObject ?: JsonObject(emptyMap())
                ComponentDSL(
                    type = cType,
                    sortOrder = index,
                    config = configObj
                )
            } ?: emptyList()

            EventDSLOutput(
                title = title,
                description = description,
                startAtMs = startMs,
                endAtMs = endMs,
                subActions = subActions,
                components = components
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse LLM event response", e)
            buildFallbackDsl(input, fallbackStart, fallbackEnd)
        }
    }

    private fun buildHeuristicResult(
        input: String,
        startAt: Long,
        endAt: Long,
        entities: ExtractedEntities
    ): EventGenerationResult {
        val dsl = buildFallbackDsl(input, startAt, endAt)
        val warnings = mutableListOf<String>()
        if (startAt == 0L) warnings.add("No start date detected — you'll need to set it manually.")
        if (endAt == 0L) warnings.add("No end date detected — you'll need to set it manually.")
        return EventGenerationResult(
            dsl = dsl,
            source = TaskGenerationSource.RULES,
            warnings = warnings
        )
    }

    private fun buildFallbackDsl(input: String, startAt: Long, endAt: Long): EventDSLOutput {
        // Default: add a NOTES component and a METRIC_PROMPT sub-action
        val defaultSubAction = EventSubActionDSL(
            type = EventSubActionType.NOTIFICATION,
            title = "Event check-in",
            intervalMs = 4 * 60 * 60 * 1000L, // every 4 hours
            prompt = "",
            enabled = true
        )
        val defaultComponent = ComponentDSL(
            type = com.theveloper.aura.domain.model.ComponentType.NOTES,
            sortOrder = 0,
            config = JsonObject(mapOf(
                "text" to kotlinx.serialization.json.JsonPrimitive(""),
                "isMarkdown" to kotlinx.serialization.json.JsonPrimitive(true)
            ))
        )
        return EventDSLOutput(
            title = input.take(80),
            description = "",
            startAtMs = startAt,
            endAtMs = endAt,
            subActions = listOf(defaultSubAction),
            components = listOf(defaultComponent)
        )
    }

    companion object {
        private const val TAG = "EventClassifier"
    }
}

data class EventGenerationResult(
    val dsl: EventDSLOutput,
    val source: TaskGenerationSource,
    val warnings: List<String> = emptyList()
)

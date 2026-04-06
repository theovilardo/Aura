package com.theveloper.aura.engine.classifier

import android.util.Log
import com.theveloper.aura.domain.model.AutomationExecutionPlan
import com.theveloper.aura.domain.model.AutomationOutputType
import com.theveloper.aura.domain.model.AutomationStep
import com.theveloper.aura.domain.model.AutomationStepType
import com.theveloper.aura.engine.dsl.AutomationDSLOutput
import com.theveloper.aura.engine.llm.extractLikelyJsonBlock
import com.theveloper.aura.engine.llm.LLMServiceFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifier for automations — the most LLM-dependent creation type.
 *
 * Pipeline:
 * 1. Extract entities for schedule detection.
 * 2. LLM path: generate an [AutomationExecutionPlan] describing steps.
 * 3. Heuristic fallback: generic "gather tasks + summarize" template.
 */
@Singleton
class AutomationClassifier @Inject constructor(
    private val entityExtractorService: EntityExtractorService,
    private val llmServiceFactory: LLMServiceFactory
) {

    suspend fun classify(input: String): AutomationGenerationResult {
        Log.d(TAG, "classify(input=${input.take(60)}…)")

        val normalizedInput = input.trim()
        val entities = entityExtractorService.extract(normalizedInput)

        // Detect cron expression from natural language
        val cronExpression = detectCronExpression(normalizedInput)

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
                val dsl = parseLLMResponse(rawJson, normalizedInput, cronExpression)
                AutomationGenerationResult(dsl = dsl, source = route.source)
            } else {
                buildHeuristicResult(normalizedInput, cronExpression)
            }
        } catch (e: Exception) {
            Log.w(TAG, "LLM path failed, using heuristic", e)
            buildHeuristicResult(normalizedInput, cronExpression)
        }
    }

    /**
     * Detect simple cron patterns from natural language.
     * Supports: "every monday", "daily", "weekly", "every morning", etc.
     */
    private fun detectCronExpression(input: String): String {
        val lower = input.lowercase()

        // Day-of-week patterns
        val dayMap = mapOf(
            "monday" to 1, "lunes" to 1,
            "tuesday" to 2, "martes" to 2,
            "wednesday" to 3, "miercoles" to 3, "miércoles" to 3,
            "thursday" to 4, "jueves" to 4,
            "friday" to 5, "viernes" to 5,
            "saturday" to 6, "sabado" to 6, "sábado" to 6,
            "sunday" to 0, "domingo" to 0
        )
        for ((name, dow) in dayMap) {
            if (lower.contains(name)) return "0 9 * * $dow"
        }

        // Frequency patterns
        if (lower.contains("daily") || lower.contains("diario") || lower.contains("todos los dias")
            || lower.contains("cada dia") || lower.contains("every day")
        ) return "0 9 * * *"

        if (lower.contains("weekly") || lower.contains("semanal") || lower.contains("cada semana")
            || lower.contains("every week")
        ) return "0 9 * * 1" // Monday 9am

        if (lower.contains("monthly") || lower.contains("mensual") || lower.contains("cada mes")
            || lower.contains("every month")
        ) return "0 9 1 * *" // 1st of month

        // Time patterns: "every morning", "cada mañana"
        if (lower.contains("morning") || lower.contains("mañana")) return "0 8 * * *"
        if (lower.contains("evening") || lower.contains("noche") || lower.contains("tarde")) return "0 18 * * *"

        // Default: daily at 9am if no schedule detected
        return "0 9 * * *"
    }

    private fun buildLLMPrompt(input: String, context: LLMClassificationContext): String {
        return buildString {
            appendLine("You are an automation creation assistant. Output ONLY valid JSON, no markdown.")
            appendLine("LANGUAGE RULE: All user-facing text MUST match the input language.")
            appendLine()
            appendLine("Given this user input, create an automation plan:")
            appendLine("Input: \"$input\"")
            if (context.extractedDates.isNotEmpty()) {
                appendLine("Extracted dates (epoch ms): ${context.extractedDates}")
            }
            appendLine()
            appendLine("An automation has steps that execute in order when triggered:")
            appendLine("- GATHER_CONTEXT: collect data from the user's tasks/reminders/events")
            appendLine("- LLM_PROCESS: process gathered data with AI (summarize, analyze, recommend)")
            appendLine("- OUTPUT: deliver results (notification, task_update, summary)")
            appendLine()
            appendLine("JSON schema:")
            appendLine("""{
  "title": "short automation title",
  "cron_expression": "0 9 * * 1",
  "output_type": "NOTIFICATION|TASK_UPDATE|SUMMARY|CUSTOM",
  "steps": [
    {
      "type": "GATHER_CONTEXT",
      "description": "what data to collect",
      "params": {"query": "active_tasks_due_this_week"}
    },
    {
      "type": "LLM_PROCESS",
      "description": "what to do with the data",
      "params": {"instruction": "summarize into priorities"}
    },
    {
      "type": "OUTPUT",
      "description": "how to deliver",
      "params": {"format": "notification"}
    }
  ]
}""")
        }
    }

    private fun parseLLMResponse(
        rawJson: String,
        input: String,
        fallbackCron: String
    ): AutomationDSLOutput {
        return try {
            val json = rawJson.extractLikelyJsonBlock()
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(json)
                as? kotlinx.serialization.json.JsonObject
                ?: return buildFallbackDsl(input, fallbackCron)

            fun str(key: String) = (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""

            val title = str("title").ifBlank { input.take(50) }
            val cron = str("cron_expression").ifBlank { fallbackCron }
            val outputTypeStr = str("output_type").uppercase()
            val outputType = runCatching { AutomationOutputType.valueOf(outputTypeStr) }
                .getOrDefault(AutomationOutputType.NOTIFICATION)

            val stepsArray = obj["steps"] as? kotlinx.serialization.json.JsonArray
            val steps = stepsArray?.mapNotNull { element ->
                val stepObj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val typeStr = (stepObj["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.uppercase() ?: return@mapNotNull null
                val stepType = runCatching { AutomationStepType.valueOf(typeStr) }.getOrNull() ?: return@mapNotNull null
                val desc = (stepObj["description"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                val params = (stepObj["params"] as? kotlinx.serialization.json.JsonObject)
                    ?.mapValues { (_, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "" }
                    ?: emptyMap()
                AutomationStep(type = stepType, description = desc, params = params)
            } ?: emptyList()

            AutomationDSLOutput(
                title = title,
                prompt = input,
                cronExpression = cron,
                executionPlan = AutomationExecutionPlan(steps = steps.ifEmpty { defaultSteps(input) }),
                outputType = outputType
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse LLM automation response", e)
            buildFallbackDsl(input, fallbackCron)
        }
    }

    private fun buildHeuristicResult(input: String, cron: String): AutomationGenerationResult {
        val dsl = buildFallbackDsl(input, cron)
        return AutomationGenerationResult(
            dsl = dsl,
            source = TaskGenerationSource.RULES,
            warnings = listOf("Automation plan generated with heuristic rules — you can refine the steps.")
        )
    }

    private fun buildFallbackDsl(input: String, cron: String): AutomationDSLOutput {
        return AutomationDSLOutput(
            title = input.take(80),
            prompt = input,
            cronExpression = cron,
            executionPlan = AutomationExecutionPlan(steps = defaultSteps(input)),
            outputType = AutomationOutputType.NOTIFICATION
        )
    }

    private fun defaultSteps(input: String): List<AutomationStep> = listOf(
        AutomationStep(
            type = AutomationStepType.GATHER_CONTEXT,
            description = "Collect active tasks and upcoming reminders",
            params = mapOf("query" to "active_items")
        ),
        AutomationStep(
            type = AutomationStepType.LLM_PROCESS,
            description = "Process and summarize based on user request",
            params = mapOf("instruction" to input)
        ),
        AutomationStep(
            type = AutomationStepType.OUTPUT,
            description = "Deliver result as notification",
            params = mapOf("format" to "notification")
        )
    )

    companion object {
        private const val TAG = "AutomationClassifier"
    }
}

data class AutomationGenerationResult(
    val dsl: AutomationDSLOutput,
    val source: TaskGenerationSource,
    val warnings: List<String> = emptyList()
)

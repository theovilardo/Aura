package com.theveloper.aura.engine.llm

import com.theveloper.aura.engine.classifier.ExtractedEntities
import com.theveloper.aura.engine.classifier.IntentResult
import com.theveloper.aura.engine.classifier.LLMClassificationContext
import com.theveloper.aura.engine.classifier.OnDeviceTaskDslRequest
import com.theveloper.aura.engine.classifier.OnDeviceTaskDslService
import com.theveloper.aura.engine.classifier.TaskDSLBuilder
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RulesOnlyLLMService @Inject constructor(
    private val onDeviceTaskDslService: OnDeviceTaskDslService
) : LLMService {

    override val tier: LLMTier = LLMTier.RULES_ONLY

    override fun isAvailable(): Boolean = true

    override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
        val taskType = context.intentHint ?: return TaskDSLBuilder.buildFallback(input, context)
        return runCatching {
            onDeviceTaskDslService.compose(
                OnDeviceTaskDslRequest(
                    input = input,
                    intentResult = IntentResult(taskType, context.intentConfidence),
                    extractedEntities = ExtractedEntities(
                        dateTimes = context.extractedDates,
                        numbers = context.extractedNumbers,
                        locations = context.extractedLocations
                    ),
                    llmContext = context
                )
            ).dsl
        }.getOrElse {
            TaskDSLBuilder.buildFallback(input, context)
        }
    }
}

package com.theveloper.aura.engine.classifier

import com.theveloper.aura.engine.dsl.TaskDSLOutput

enum class TaskGenerationSource {
    MANUAL,
    RULES,
    LOCAL_AI,
    GROQ_API
}

data class TaskGenerationResult(
    val dsl: TaskDSLOutput,
    val source: TaskGenerationSource,
    val executionMode: AiExecutionMode,
    val intentConfidence: Float,
    val localConfidence: Float,
    val warnings: List<String> = emptyList(),
    val completenessCheck: CompletenessCheck = CompletenessCheck(isComplete = true),
    val clarifications: List<ClarificationRequest> = emptyList()
) {
    /** First pending clarification question, null if none. */
    val clarification: ClarificationRequest? get() = clarifications.firstOrNull()
}

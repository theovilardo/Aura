package com.theveloper.aura.engine.router

import com.theveloper.aura.engine.classifier.ExtractedEntities
import com.theveloper.aura.engine.classifier.LLMClassificationContext
import com.theveloper.aura.engine.provider.ProviderCapability
import com.theveloper.aura.protocol.ExecutionTarget

data class ExecutionRequest(
    val input: String,
    val entities: ExtractedEntities? = null,
    val context: LLMClassificationContext? = null,
    val capability: ProviderCapability = ProviderCapability.CLASSIFY,
    val preferredTarget: ExecutionTarget? = null,
    val preferredProviderId: String? = null,
    val urgency: Urgency = Urgency.NORMAL
)

enum class Urgency { LOW, NORMAL, HIGH }

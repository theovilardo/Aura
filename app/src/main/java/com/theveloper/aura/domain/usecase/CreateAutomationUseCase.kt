package com.theveloper.aura.domain.usecase

import com.theveloper.aura.domain.model.AuraAutomation
import com.theveloper.aura.domain.model.AutomationStatus
import com.theveloper.aura.domain.repository.AutomationRepository
import com.theveloper.aura.engine.dsl.AutomationDSLOutput
import java.util.UUID
import javax.inject.Inject

class CreateAutomationUseCase @Inject constructor(
    private val repository: AutomationRepository
) {
    suspend operator fun invoke(dsl: AutomationDSLOutput): AuraAutomation {
        val now = System.currentTimeMillis()

        val automation = AuraAutomation(
            id = UUID.randomUUID().toString(),
            title = dsl.title,
            prompt = dsl.prompt,
            cronExpression = dsl.cronExpression,
            executionPlan = dsl.executionPlan,
            outputType = dsl.outputType,
            status = AutomationStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )

        repository.insert(automation)
        return automation
    }
}

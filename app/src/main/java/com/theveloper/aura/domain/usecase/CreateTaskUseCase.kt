package com.theveloper.aura.domain.usecase

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.toTaskShape
import com.theveloper.aura.domain.repository.TaskRepository
import com.theveloper.aura.domain.repository.FetcherConfigRepository
import com.theveloper.aura.domain.repository.ComponentRuleRepository
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.model.FetcherType
import com.theveloper.aura.data.db.FetcherConfigEntity
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.dsl.TaskDslMapper
import com.theveloper.aura.engine.rule.ShapeRuleDefaults
import java.util.UUID
import javax.inject.Inject

class CreateTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val fetcherConfigRepository: FetcherConfigRepository,
    private val ruleRepository: ComponentRuleRepository
) {
    suspend operator fun invoke(dsl: TaskDSLOutput): Task {
        val task = TaskDslMapper.toTask(dsl)
        taskRepository.insertTask(task)

        // Auto-generate component rules based on shape
        setupDefaultRules(task)

        // F4-08 Create predefined fetchers for TRAVEL
        if (task.type == TaskType.TRAVEL) {
            setupTravelFetchers(task)
        }
        return task
    }

    private suspend fun setupDefaultRules(task: Task) {
        val shape = task.type.toTaskShape()
        val componentIds = task.components.associate { it.type to it.id }
        val rules = ShapeRuleDefaults.getRulesForShape(task.id, shape, componentIds)
        if (rules.isNotEmpty()) {
            ruleRepository.insertRules(rules)
        }
    }

    private suspend fun setupTravelFetchers(task: Task) {
        // Config: Weather para DESTINO
        fetcherConfigRepository.insert(
            FetcherConfigEntity(
                id = UUID.randomUUID().toString(),
                taskId = task.id,
                type = FetcherType.WEATHER,
                params = "{\"latitude\":\"40.4168\", \"longitude\":\"-3.7038\"}",
                cronExpression = "0 */12 * * *",
                lastResultJson = null,
                lastUpdatedAt = null
            )
        )

        // Config: Vuelos BUE->MAD
        fetcherConfigRepository.insert(
            FetcherConfigEntity(
                id = UUID.randomUUID().toString(),
                taskId = task.id,
                type = FetcherType.FLIGHT_PRICES,
                params = "{\"origin\":\"BUE\", \"destination\":\"MAD\", \"date_from\":\"2026-08-01\", \"date_to\":\"2026-08-15\"}",
                cronExpression = "0 0 * * *",
                lastResultJson = null,
                lastUpdatedAt = null
            )
        )

        // Config: Moneda (ARS->EUR mock as travel to MAD)
        fetcherConfigRepository.insert(
            FetcherConfigEntity(
                id = UUID.randomUUID().toString(),
                taskId = task.id,
                type = FetcherType.CURRENCY_EXCHANGE,
                params = "{\"from\":\"USD\", \"to\":\"EUR\"}",
                cronExpression = "0 6 * * *",
                lastResultJson = null,
                lastUpdatedAt = null
            )
        )
    }
}

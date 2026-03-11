package com.theveloper.aura.domain.usecase

import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.repository.TaskRepository
import com.theveloper.aura.domain.repository.FetcherConfigRepository
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.model.FetcherType
import com.theveloper.aura.data.db.FetcherConfigEntity
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.dsl.TaskDslMapper
import java.util.UUID
import javax.inject.Inject

class CreateTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val fetcherConfigRepository: FetcherConfigRepository
) {
    suspend operator fun invoke(dsl: TaskDSLOutput): Task {
        val task = TaskDslMapper.toTask(dsl)
        taskRepository.insertTask(task)

        // F4-08 Create predefined fetchers for TRAVEL
        if (task.type == TaskType.TRAVEL) {
            setupTravelFetchers(task)
        }
        return task
    }

    private suspend fun setupTravelFetchers(task: Task) {
        // Enlazar weather de un lugar dummy, o desde components (requiere NLP param)
        // Por simplicidad en esta lógica que requiere NLP parameters como origen y destino para F-05,
        // vamos a instanciar FetcherConfigs tomando en cuenta que los params deben autollenarse.
        // Dado el MVP, extraemos del titulo/descripción o de DataFeedComponents la data requerida.
        
        // Config: Weather para DESTINO
        fetcherConfigRepository.insert(
            FetcherConfigEntity(
                id = UUID.randomUUID().toString(),
                taskId = task.id,
                type = FetcherType.WEATHER,
                params = "{\"latitude\":\"40.4168\", \"longitude\":\"-3.7038\"}", // Madrid como default mock
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

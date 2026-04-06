package com.theveloper.aura.domain.usecase

import com.theveloper.aura.domain.model.AuraEvent
import com.theveloper.aura.domain.model.EventStatus
import com.theveloper.aura.domain.model.EventSubAction
import com.theveloper.aura.domain.model.TaskComponent
import com.theveloper.aura.domain.model.UnknownConfig
import com.theveloper.aura.domain.repository.AuraEventRepository
import com.theveloper.aura.engine.dsl.EventDSLOutput
import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.domain.model.ComponentConfig
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject

class CreateEventUseCase @Inject constructor(
    private val repository: AuraEventRepository
) {
    suspend operator fun invoke(dsl: EventDSLOutput): AuraEvent {
        val now = System.currentTimeMillis()
        val eventId = UUID.randomUUID().toString()

        val subActions = dsl.subActions.map { saDsl ->
            EventSubAction(
                id = UUID.randomUUID().toString(),
                eventId = eventId,
                type = saDsl.type,
                title = saDsl.title,
                cronExpression = saDsl.cronExpression,
                intervalMs = saDsl.intervalMs,
                prompt = saDsl.prompt,
                config = saDsl.config.let { jsonObj ->
                    jsonObj.mapValues { (_, v) ->
                        (v as? kotlinx.serialization.json.JsonPrimitive)?.content ?: v.toString()
                    }
                },
                enabled = saDsl.enabled
            )
        }

        val components = dsl.components.mapIndexed { index, cDsl ->
            val config: ComponentConfig = runCatching {
                auraJson.decodeFromString(
                    ComponentConfig.serializer(),
                    cDsl.config.toString()
                )
            }.getOrDefault(UnknownConfig())

            TaskComponent(
                id = UUID.randomUUID().toString(),
                taskId = eventId, // reuse taskId field for the event FK
                type = cDsl.type,
                sortOrder = cDsl.sortOrder,
                config = config,
                needsClarification = cDsl.needsClarification
            )
        }

        val status = if (dsl.startAtMs <= now) EventStatus.ACTIVE else EventStatus.UPCOMING

        val event = AuraEvent(
            id = eventId,
            title = dsl.title,
            description = dsl.description,
            startAt = dsl.startAtMs,
            endAt = dsl.endAtMs,
            subActions = subActions,
            components = components,
            status = status,
            createdAt = now,
            updatedAt = now
        )

        repository.insert(event)
        return event
    }
}

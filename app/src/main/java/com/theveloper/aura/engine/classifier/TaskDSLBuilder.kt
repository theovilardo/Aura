package com.theveloper.aura.engine.classifier

import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

object TaskDSLBuilder {
    fun buildPathDeterministic(
        title: String,
        taskType: TaskType,
        entities: List<Entity>
    ): TaskDSLOutput {
        var targetDateMs: Long? = null

        val dateTimeEntities = entities.filterIsInstance<DateTimeEntity>()
        if (dateTimeEntities.isNotEmpty()) {
            targetDateMs = dateTimeEntities.first().timestampMillis
        }

        val components = mutableListOf<ComponentDSL>()
        
        when (taskType) {
            TaskType.TRAVEL -> {
                components.add(ComponentDSL(type = "COUNTDOWN", sortOrder = 0, config = buildJsonObject {
                    put("config_type", JsonPrimitive("COUNTDOWN"))
                    put("targetDate", JsonPrimitive(targetDateMs ?: 0L))
                    put("label", JsonPrimitive("Días para el viaje"))
                }))
                components.add(ComponentDSL(type = "CHECKLIST", sortOrder = 1, config = buildJsonObject {
                    put("config_type", JsonPrimitive("CHECKLIST"))
                    put("label", JsonPrimitive("Preparativos"))
                    put("allowAddItems", JsonPrimitive(true))
                }))
            }
            TaskType.HABIT -> {
                components.add(ComponentDSL(type = "HABIT_RING", sortOrder = 0, config = buildJsonObject {
                    put("config_type", JsonPrimitive("HABIT_RING"))
                    put("frequency", JsonPrimitive("DAILY"))
                    put("label", JsonPrimitive("Progreso"))
                }))
            }
            TaskType.HEALTH -> {
                 components.add(ComponentDSL(type = "METRIC_TRACKER", sortOrder = 0, config = buildJsonObject {
                    put("config_type", JsonPrimitive("METRIC_TRACKER"))
                    put("unit", JsonPrimitive(""))
                    put("label", JsonPrimitive("Medición"))
                }))
            }
            TaskType.PROJECT -> {
                components.add(ComponentDSL(type = "PROGRESS_BAR", sortOrder = 0, config = buildJsonObject {
                    put("config_type", JsonPrimitive("PROGRESS_BAR"))
                    put("source", JsonPrimitive("MANUAL"))
                    put("label", JsonPrimitive("Avance general"))
                }))
                components.add(ComponentDSL(type = "NOTES", sortOrder = 1, config = buildJsonObject {
                    put("config_type", JsonPrimitive("NOTES"))
                    put("text", JsonPrimitive(""))
                    put("isMarkdown", JsonPrimitive(true))
                }))
            }
            TaskType.FINANCE -> {
                components.add(ComponentDSL(type = "DATA_FEED", sortOrder = 0, config = buildJsonObject {
                    put("config_type", JsonPrimitive("DATA_FEED"))
                    put("fetcherConfigId", JsonPrimitive("usd_ars"))
                    put("displayLabel", JsonPrimitive("Cotización USD"))
                }))
            }
            else -> {
                components.add(ComponentDSL(type = "NOTES", sortOrder = 0, config = buildJsonObject {
                    put("config_type", JsonPrimitive("NOTES"))
                    put("text", JsonPrimitive(""))
                    put("isMarkdown", JsonPrimitive(true))
                }))
            }
        }

        return TaskDSLOutput(
            title = title,
            type = taskType,
            targetDateMs = targetDateMs,
            components = components
        )
    }
}

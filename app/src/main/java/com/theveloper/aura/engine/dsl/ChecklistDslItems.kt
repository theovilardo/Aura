package com.theveloper.aura.engine.dsl

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ChecklistItemDSL(
    val label: String,
    val isSuggested: Boolean = false
)

object ChecklistDslItems {

    fun parse(config: JsonObject): List<ChecklistItemDSL> {
        val items = config["items"] as? JsonArray ?: return emptyList()
        return items.mapNotNull(::parseItem)
    }

    fun withItems(
        config: JsonObject,
        items: List<ChecklistItemDSL>
    ): JsonObject {
        return JsonObject(
            config + ("items" to JsonArray(items.map(::toJson)))
        )
    }

    private fun parseItem(element: JsonElement): ChecklistItemDSL? {
        return when (element) {
            is JsonPrimitive -> {
                element.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ChecklistItemDSL(label = it) }
            }

            is JsonObject -> {
                val label = element["label"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (label.isBlank()) {
                    null
                } else {
                    ChecklistItemDSL(
                        label = label,
                        isSuggested = element["isSuggested"]?.jsonPrimitive?.booleanOrNull ?: false
                    )
                }
            }

            else -> null
        }
    }

    private fun toJson(item: ChecklistItemDSL): JsonElement {
        return JsonObject(
            mapOf(
                "label" to JsonPrimitive(item.label),
                "isSuggested" to JsonPrimitive(item.isSuggested)
            )
        )
    }
}

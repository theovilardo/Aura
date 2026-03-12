package com.theveloper.aura.engine.llm

import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.DataFeedStatus
import com.theveloper.aura.domain.model.FetcherType
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal fun String.stripCodeFences(): String {
    return trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
}

internal fun String.extractLikelyJsonBlock(): String {
    val normalized = stripCodeFences()
    return normalized.extractBalancedBlock('{', '}')
        ?: normalized.extractBalancedBlock('[', ']')
        ?: normalized
}

internal fun String.normalizeTaskDslJson(): String {
    val candidate = extractLikelyJsonBlock()
    val root = runCatching { auraJson.parseToJsonElement(candidate) }.getOrNull() as? JsonObject
        ?: return candidate

    return normalizeTaskDslObject(root).toString()
}

internal fun TaskDSLOutput.stabilizeLocalClassification(input: String): TaskDSLOutput {
    if (!input.looksLikeShoppingListRequest()) return this

    val filteredComponents = components
        .filter { component ->
            component.type == ComponentType.CHECKLIST || component.type == ComponentType.NOTES
        }
        .ifEmpty { return this }

    if (filteredComponents.none { it.type == ComponentType.CHECKLIST }) {
        return this
    }

    return copy(
        components = filteredComponents.mapIndexed { index, component ->
            component.copy(sortOrder = index)
        }
    )
}

private fun String.extractBalancedBlock(openChar: Char, closeChar: Char): String? {
    val startIndex = indexOf(openChar)
    if (startIndex == -1) return null

    var depth = 0
    var inString = false
    var escaped = false

    for (index in startIndex until length) {
        val current = this[index]
        when {
            escaped -> escaped = false
            current == '\\' && inString -> escaped = true
            current == '"' -> inString = !inString
            !inString && current == openChar -> depth++
            !inString && current == closeChar -> {
                depth--
                if (depth == 0) {
                    return substring(startIndex, index + 1)
                }
            }
        }
    }

    return null
}

private fun normalizeTaskDslObject(root: JsonObject): JsonObject {
    val type = root["type"].enumValueOrDefault(TaskType.GENERAL)
    val targetDateMs = root["targetDateMs"].epochMillisOrNull()
    val components = normalizeComponents(
        components = root["components"] as? JsonArray,
        rootTargetDateMs = targetDateMs
    )

    return JsonObject(
        mapOf(
            "title" to JsonPrimitive(root["title"].stringValue().ifBlank { "Untitled task" }),
            "type" to JsonPrimitive(type.name),
            "priority" to JsonPrimitive(root["priority"].intValue(default = 0).coerceIn(0, 3)),
            "targetDateMs" to (targetDateMs?.let(::JsonPrimitive) ?: JsonNull),
            "components" to components,
            "reminders" to normalizeReminders(root["reminders"] as? JsonArray),
            "fetchers" to normalizeFetchers(root["fetchers"] as? JsonArray)
        )
    )
}

private fun normalizeComponents(
    components: JsonArray?,
    rootTargetDateMs: Long?
): JsonArray {
    val normalized = components
        ?.mapNotNull { normalizeComponent(it as? JsonObject, rootTargetDateMs) }
        .orEmpty()
        .distinctBy { component ->
            buildString {
                append(component["type"])
                append('|')
                append(component["config"])
                append('|')
                append(component["populatedFromInput"])
                append('|')
                append(component["needsClarification"])
            }
        }
        .mapIndexed { index, component ->
            JsonObject(component + ("sortOrder" to JsonPrimitive(index)))
        }

    return JsonArray(normalized)
}

private fun normalizeComponent(
    component: JsonObject?,
    rootTargetDateMs: Long?
): JsonObject? {
    component ?: return null
    val type = component.resolveComponentType() ?: return null
    val config = normalizeComponentConfig(
        type = type,
        component = component,
        rootTargetDateMs = rootTargetDateMs
    )

    return JsonObject(
        mapOf(
            "type" to JsonPrimitive(type.name),
            "sortOrder" to JsonPrimitive(0),
            "config" to config,
            "populatedFromInput" to JsonPrimitive(component["populatedFromInput"].booleanValue(default = false)),
            "needsClarification" to JsonPrimitive(component["needsClarification"].booleanValue(default = false))
        )
    )
}

private fun normalizeComponentConfig(
    type: ComponentType,
    component: JsonObject,
    rootTargetDateMs: Long?
): JsonObject {
    val merged = linkedMapOf<String, JsonElement>()
    (component["config"] as? JsonObject)?.let(merged::putAll)
    component.forEach { (key, value) ->
        if (key !in COMPONENT_METADATA_KEYS && key !in merged) {
            merged[key] = value
        }
    }
    merged["config_type"] = JsonPrimitive(type.name)

    when (type) {
        ComponentType.CHECKLIST -> {
            merged["label"] = JsonPrimitive(merged["label"].stringValue().ifBlank { "Checklist" })
            merged["allowAddItems"] = JsonPrimitive(merged["allowAddItems"].booleanValue(default = true))
            normalizeChecklistItems(merged["items"])?.let { merged["items"] = it } ?: merged.remove("items")
        }

        ComponentType.COUNTDOWN -> {
            val targetDate = merged["targetDate"].epochMillisOrNull() ?: rootTargetDateMs ?: 0L
            merged["targetDate"] = JsonPrimitive(targetDate)
            merged["label"] = JsonPrimitive(merged["label"].stringValue().ifBlank { "Countdown" })
        }

        ComponentType.NOTES -> {
            merged["text"] = JsonPrimitive(merged["text"].stringValue())
            merged["isMarkdown"] = JsonPrimitive(merged["isMarkdown"].booleanValue(default = true))
        }

        ComponentType.PROGRESS_BAR -> {
            merged["source"] = JsonPrimitive(merged["source"].stringValue().ifBlank { "MANUAL" })
            merged["label"] = JsonPrimitive(merged["label"].stringValue())
            merged["manualProgress"] = merged["manualProgress"].floatValueOrNull()?.let(::JsonPrimitive) ?: JsonNull
        }

        ComponentType.HABIT_RING -> {
            merged["frequency"] = JsonPrimitive(merged["frequency"].stringValue().ifBlank { "DAILY" })
            merged["label"] = JsonPrimitive(merged["label"].stringValue())
            merged["targetCount"] = merged["targetCount"].intValueOrNull()?.let(::JsonPrimitive) ?: JsonNull
            merged["completedToday"] = JsonPrimitive(merged["completedToday"].booleanValue(default = false))
            merged["streakCount"] = JsonPrimitive(merged["streakCount"].intValue(default = 0))
        }

        ComponentType.METRIC_TRACKER -> {
            merged["unit"] = JsonPrimitive(merged["unit"].stringValue())
            merged["label"] = JsonPrimitive(merged["label"].stringValue())
            merged["goal"] = merged["goal"].floatValueOrNull()?.let(::JsonPrimitive) ?: JsonNull
            merged["history"] = normalizeNumberArray(merged["history"])
        }

        ComponentType.DATA_FEED -> {
            merged["fetcherConfigId"] = JsonPrimitive(merged["fetcherConfigId"].stringValue())
            merged["displayLabel"] = JsonPrimitive(merged["displayLabel"].stringValue())
            val status = merged["status"].enumValueOrDefault(DataFeedStatus.DATA)
            merged["status"] = JsonPrimitive(status.name)
            merged["value"] = merged["value"]?.let(::normalizeOptionalString) ?: JsonNull
            merged["lastValue"] = merged["lastValue"]?.let(::normalizeOptionalString) ?: JsonNull
            merged["lastUpdatedAt"] = merged["lastUpdatedAt"].epochMillisOrNull()?.let(::JsonPrimitive) ?: JsonNull
            merged["errorMessage"] = merged["errorMessage"]?.let(::normalizeOptionalString) ?: JsonNull
        }
    }

    return JsonObject(merged)
}

private fun normalizeChecklistItems(element: JsonElement?): JsonArray? {
    val items = (element as? JsonArray)
        ?.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> {
                    item.contentOrNull
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { label ->
                            JsonObject(
                                mapOf(
                                    "label" to JsonPrimitive(label),
                                    "isSuggested" to JsonPrimitive(false)
                                )
                            )
                        }
                }

                is JsonObject -> {
                    val label = item["label"].stringValue()
                    if (label.isBlank()) {
                        null
                    } else {
                        JsonObject(
                            mapOf(
                                "label" to JsonPrimitive(label),
                                "isSuggested" to JsonPrimitive(item["isSuggested"].booleanValue(default = false))
                            )
                        )
                    }
                }

                else -> null
            }
        }
        .orEmpty()

    return items.takeIf { it.isNotEmpty() }?.let(::JsonArray)
}

private fun normalizeNumberArray(element: JsonElement?): JsonArray {
    val values = (element as? JsonArray)
        ?.mapNotNull { it.floatValueOrNull() }
        .orEmpty()
        .map(::JsonPrimitive)

    return JsonArray(values)
}

private fun normalizeReminders(reminders: JsonArray?): JsonArray {
    val normalized = reminders
        ?.mapNotNull { reminder ->
            val obj = reminder as? JsonObject ?: return@mapNotNull null
            val scheduledAtMs = obj["scheduledAtMs"].epochMillisOrNull() ?: return@mapNotNull null
            JsonObject(
                mapOf(
                    "scheduledAtMs" to JsonPrimitive(scheduledAtMs),
                    "intervalDays" to JsonPrimitive(obj["intervalDays"].floatValueOrNull() ?: 0f),
                    "easeFactor" to JsonPrimitive(obj["easeFactor"].floatValueOrNull() ?: 2.5f),
                    "repetitions" to JsonPrimitive(obj["repetitions"].intValue(default = 0))
                )
            )
        }
        .orEmpty()

    return JsonArray(normalized)
}

private fun normalizeFetchers(fetchers: JsonArray?): JsonArray {
    val normalized = fetchers
        ?.mapNotNull { fetcher ->
            val obj = fetcher as? JsonObject ?: return@mapNotNull null
            val type = obj["type"].enumValueOrNull<FetcherType>() ?: return@mapNotNull null
            val cronExpression = obj["cronExpression"].stringValue()
            if (cronExpression.isBlank()) return@mapNotNull null

            JsonObject(
                mapOf(
                    "type" to JsonPrimitive(type.name),
                    "params" to (obj["params"] as? JsonObject ?: JsonObject(emptyMap())),
                    "cronExpression" to JsonPrimitive(cronExpression)
                )
            )
        }
        .orEmpty()

    return JsonArray(normalized)
}

private fun JsonObject.resolveComponentType(): ComponentType? {
    this["type"].enumValueOrNull<ComponentType>()?.let { return it }
    this["config_type"].enumValueOrNull<ComponentType>()?.let { return it }
    (this["config"] as? JsonObject)?.get("config_type").enumValueOrNull<ComponentType>()?.let { return it }

    return when {
        hasAnyKey("targetDate") -> ComponentType.COUNTDOWN
        hasAnyKey("frequency", "targetCount", "completedToday", "streakCount") -> ComponentType.HABIT_RING
        hasAnyKey("unit", "goal", "history") -> ComponentType.METRIC_TRACKER
        hasAnyKey("fetcherConfigId", "displayLabel", "status") -> ComponentType.DATA_FEED
        hasAnyKey("source", "manualProgress") -> ComponentType.PROGRESS_BAR
        hasAnyKey("text", "isMarkdown") -> ComponentType.NOTES
        hasAnyKey("items", "allowAddItems", "label") -> ComponentType.CHECKLIST
        else -> null
    }
}

private fun JsonObject.hasAnyKey(vararg keys: String): Boolean {
    val nestedConfig = this["config"] as? JsonObject
    return keys.any { it in this || (nestedConfig != null && it in nestedConfig) }
}

private fun normalizeOptionalString(element: JsonElement): JsonElement {
    val value = element.stringValue().ifBlank { "" }
    return if (value.isBlank()) JsonNull else JsonPrimitive(value)
}

private fun JsonElement?.stringValue(): String {
    return when (this) {
        is JsonPrimitive -> contentOrNull?.trim().orEmpty()
        else -> ""
    }
}

private fun JsonElement?.booleanValue(default: Boolean): Boolean {
    return when (this) {
        is JsonPrimitive -> {
            booleanOrNull
                ?: contentOrNull
                    ?.trim()
                    ?.lowercase(Locale.US)
                    ?.let { raw ->
                        when (raw) {
                            "true", "1", "yes", "si" -> true
                            "false", "0", "no" -> false
                            else -> null
                        }
                    }
                ?: default
        }

        else -> default
    }
}

private fun JsonElement?.intValue(default: Int): Int {
    return intValueOrNull() ?: default
}

private fun JsonElement?.intValueOrNull(): Int? {
    return when (this) {
        is JsonPrimitive -> intOrNull ?: contentOrNull?.trim()?.toIntOrNull()
        else -> null
    }
}

private fun JsonElement?.floatValueOrNull(): Float? {
    return when (this) {
        is JsonPrimitive -> floatOrNull ?: contentOrNull?.trim()?.toFloatOrNull()
        else -> null
    }
}

private fun JsonElement?.epochMillisOrNull(): Long? {
    return when (this) {
        is JsonPrimitive -> {
            longOrNull
                ?: contentOrNull?.trim()?.let(::parseEpochMillis)
        }

        else -> null
    }
}

private fun parseEpochMillis(rawValue: String): Long? {
    if (rawValue.isBlank()) return null
    rawValue.toLongOrNull()?.let { return it }
    runCatching { Instant.parse(rawValue).toEpochMilli() }.getOrNull()?.let { return it }
    return try {
        LocalDate.parse(rawValue)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

private inline fun <reified T : Enum<T>> JsonElement?.enumValueOrDefault(default: T): T {
    return enumValueOrNull<T>() ?: default
}

private inline fun <reified T : Enum<T>> JsonElement?.enumValueOrNull(): T? {
    val raw = this.stringValue()
    if (raw.isBlank()) return null
    return enumValues<T>().firstOrNull { enumValue ->
        enumValue.name.equals(raw.trim(), ignoreCase = true)
    }
}

private val COMPONENT_METADATA_KEYS = setOf(
    "type",
    "sortOrder",
    "config",
    "populatedFromInput",
    "needsClarification"
)

private val SHOPPING_REQUEST_REGEX = Regex(
    pattern = "\\b(shopping\\s+list|grocer(?:y|ies)|groceries|supermarket|market\\s+list|lista\\s+de\\s+compras|compras|supermercado)\\b",
    option = RegexOption.IGNORE_CASE
)

private val SPECIALIZED_UI_SIGNAL_REGEX = Regex(
    pattern = "\\b(progress|avance|progreso|habit|habito|h[aá]bito|metric|m[eé]trica|track|seguimiento|countdown|deadline|streak)\\b",
    option = RegexOption.IGNORE_CASE
)

private fun String.looksLikeShoppingListRequest(): Boolean {
    val normalized = trim()
    if (normalized.isBlank()) return false
    if (SPECIALIZED_UI_SIGNAL_REGEX.containsMatchIn(normalized)) return false
    val wordCount = normalized.split(Regex("\\s+")).count { it.isNotBlank() }
    if (wordCount > 8) return false
    return SHOPPING_REQUEST_REGEX.containsMatchIn(normalized)
}

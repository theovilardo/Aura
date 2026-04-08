package com.theveloper.aura.engine.llm

import com.theveloper.aura.engine.classifier.ChecklistInputExtraction
import com.theveloper.aura.engine.classifier.DraftContentQuality
import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.DataFeedStatus
import com.theveloper.aura.domain.model.FetcherType
import com.theveloper.aura.domain.model.FunctionSkillRuntime
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.model.UiSkillRuntime
import com.theveloper.aura.engine.dsl.SemanticFrame
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.dsl.TaskDSLValidator
import com.theveloper.aura.engine.skill.SkillRegistry
import com.theveloper.aura.engine.skill.UiSkillRegistry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.serialization.decodeFromString
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
    val repairedRootCandidate = normalized.tryRepairJsonLikeObject()
    val objectCandidates = buildList {
        repairedRootCandidate?.let(::add)
        addAll(normalized.extractBalancedBlocks('{', '}'))
        addAll(normalized.extractTruncatedObjectCandidates())
    }.distinct()

    selectBestJsonCandidate(objectCandidates)?.let { return it }

    val containsObject = normalized.indexOf('{') != -1
    val arrayCandidate = (!containsObject).let { onlyArrayMode ->
        if (onlyArrayMode) normalized.extractBalancedBlock('[', ']') else null
    }

    return arrayCandidate ?: repairedRootCandidate ?: normalized
}

internal fun String.normalizeTaskDslJson(): String {
    val candidate = extractLikelyJsonBlock()
    val root = runCatching { auraJson.parseToJsonElement(candidate) }.getOrNull() as? JsonObject
        ?: return candidate

    return normalizeTaskDslObject(root).toString()
}

internal fun String.decodeTaskDslOrNull(): TaskDSLOutput? {
    val candidate = extractLikelyJsonBlock()
    val normalized = candidate.normalizeTaskDslJson()
    val decoded = runCatching {
        auraJson.decodeFromString<TaskDSLOutput>(normalized)
    }.getOrNull() ?: return null

    if (decoded.components.isEmpty() && candidate.looksLikePlannerOnlyTaskPlan()) {
        return null
    }

    return decoded
}

internal fun TaskDSLOutput.stabilizeLocalClassification(input: String): TaskDSLOutput {
    val explicitItems = ChecklistInputExtraction.extract(input)
    if (explicitItems.size < 2) return this

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

private fun String.looksLikePlannerOnlyTaskPlan(): Boolean {
    val candidate = extractLikelyJsonBlock()
    val root = runCatching { auraJson.parseToJsonElement(candidate) }.getOrNull() as? JsonObject
    val taskRoot = (root?.get("task") as? JsonObject) ?: root
    val hasUiSkills =
        taskRoot?.containsKey("uiSkills") == true ||
            root?.containsKey("uiSkills") == true ||
            Regex("""(?m)(["'])?uiSkills\1?\s*:""").containsMatchIn(candidate)
    if (!hasUiSkills) return false

    val hasRenderableTaskPayload =
        (taskRoot?.get("components") as? JsonArray)?.isNotEmpty() == true ||
            (taskRoot?.get("skills") as? JsonArray)?.isNotEmpty() == true ||
            (root?.get("components") as? JsonArray)?.isNotEmpty() == true ||
            (root?.get("skills") as? JsonArray)?.isNotEmpty() == true

    return !hasRenderableTaskPayload
}

private fun String.extractBalancedBlock(openChar: Char, closeChar: Char): String? {
    val startIndex = indexOf(openChar)
    if (startIndex == -1) return null

    return extractBalancedBlockFrom(startIndex, openChar, closeChar)
}

private fun String.extractBalancedBlocks(openChar: Char, closeChar: Char): List<String> {
    return indices.asSequence()
        .filter { this[it] == openChar }
        .mapNotNull { startIndex -> extractBalancedBlockFrom(startIndex, openChar, closeChar) }
        .toList()
}

private fun String.extractBalancedBlockFrom(
    startIndex: Int,
    openChar: Char,
    closeChar: Char
): String? {
    if (startIndex !in indices || this[startIndex] != openChar) return null

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

/**
 * Attempts to recover a truncated JSON object from a small model that ran out of output tokens.
 * Finds the last position where the JSON was structurally valid (after a value boundary),
 * trims everything after, and closes open brackets. Returns null if recovery fails.
 */
private fun String.tryCompleteTruncatedJson(): String? {
    val startIndex = indexOf('{')
    if (startIndex == -1) return null
    return tryCompleteTruncatedJsonFrom(startIndex)
}

private fun String.tryCompleteTruncatedJsonFrom(startIndex: Int): String? {
    if (startIndex !in indices || this[startIndex] != '{') return null

    val partial = substring(startIndex)
    val openStack = mutableListOf<Char>()
    var inString = false
    var escaped = false
    var lastSafeIndex = -1  // index of last char after a complete value

    for (i in partial.indices) {
        val char = partial[i]
        when {
            escaped -> escaped = false
            char == '\\' && inString -> escaped = true
            char == '"' -> {
                inString = !inString
                if (!inString) lastSafeIndex = i  // end of string value
            }
            !inString && (char == '{' || char == '[') -> openStack.add(char)
            !inString && char == '}' -> {
                if (openStack.lastOrNull() == '{') openStack.removeLastOrNull()
                lastSafeIndex = i
            }
            !inString && char == ']' -> {
                if (openStack.lastOrNull() == '[') openStack.removeLastOrNull()
                lastSafeIndex = i
            }
            !inString && char in "0123456789.eE+-" -> lastSafeIndex = i // number value
            !inString && char == 'e' || (!inString && partial.regionMatches(i, "true", 0, 4, ignoreCase = true)) -> lastSafeIndex = i + 3
            !inString && partial.regionMatches(i, "false", 0, 5, ignoreCase = true) -> lastSafeIndex = i + 4
            !inString && partial.regionMatches(i, "null", 0, 4, ignoreCase = true) -> lastSafeIndex = i + 3
        }
    }

    if (openStack.isEmpty()) return null // Already balanced

    // Trim back to the last structurally safe point
    val safePart = if (lastSafeIndex > 0) {
        partial.substring(0, lastSafeIndex + 1).trimEnd().trimEnd(',')
    } else {
        return null
    }

    // Recount open brackets in the safe portion
    val safeStack = mutableListOf<Char>()
    var safeInString = false
    var safeEscaped = false
    for (char in safePart) {
        when {
            safeEscaped -> safeEscaped = false
            char == '\\' && safeInString -> safeEscaped = true
            char == '"' -> safeInString = !safeInString
            !safeInString && (char == '{' || char == '[') -> safeStack.add(char)
            !safeInString && char == '}' -> safeStack.removeLastOrNull()
            !safeInString && char == ']' -> safeStack.removeLastOrNull()
        }
    }

    if (safeStack.isEmpty()) return null

    val closing = safeStack.reversed().joinToString("") { if (it == '{') "}" else "]" }
    val repaired = safePart + closing

    return if (runCatching { auraJson.parseToJsonElement(repaired) }.isSuccess) repaired else null
}

private fun String.extractTruncatedObjectCandidates(): List<String> {
    return indices.asSequence()
        .filter { this[it] == '{' }
        .filter { startIndex ->
            substring(startIndex, minOf(length, startIndex + 256)).let { window ->
                window.contains("\"title\"") ||
                    window.contains("\"type\"") ||
                    window.contains("\"skills\"") ||
                    window.contains("\"uiSkills\"")
            }
        }
        .mapNotNull(::tryCompleteTruncatedJsonFrom)
        .toList()
}

private fun selectBestJsonCandidate(candidates: List<String>): String? {
    return candidates
        .distinct()
        .maxWithOrNull(
            compareBy<String> { scoreJsonCandidate(it) }
                .thenBy { it.length }
        )
}

private fun scoreJsonCandidate(candidate: String): Int {
    val parsed = runCatching { auraJson.parseToJsonElement(candidate) }.getOrNull() as? JsonObject
        ?: return scoreTaskJsonByText(candidate)
    val taskRoot = (parsed["task"] as? JsonObject) ?: parsed

    var score = 0
    if (parsed["task"] is JsonObject) score += 20
    if (!taskRoot["title"].stringValue().isBlank()) score += 90
    if (!taskRoot["type"].stringValue().isBlank()) score += 60
    if ((taskRoot["components"] as? JsonArray)?.isNotEmpty() == true) score += 220
    if ((taskRoot["skills"] as? JsonArray)?.isNotEmpty() == true) score += 200
    if ((taskRoot["uiSkills"] as? JsonArray)?.isNotEmpty() == true) score += 140
    if ((taskRoot["functionSkills"] as? JsonArray)?.isNotEmpty() == true) score += 40
    if (taskRoot["priority"] != null) score += 10
    if (taskRoot["targetDateMs"] != null) score += 10
    score += (candidate.length / 32).coerceAtMost(120)
    return score
}

private fun scoreTaskJsonByText(candidate: String): Int {
    var score = 0
    if (candidate.contains("\"title\"")) score += 60
    if (candidate.contains("\"type\"")) score += 40
    if (candidate.contains("\"components\"")) score += 120
    if (candidate.contains("\"skills\"")) score += 100
    if (candidate.contains("\"uiSkills\"")) score += 70
    if (candidate.contains("\"functionSkills\"")) score += 20
    score += (candidate.length / 32).coerceAtMost(80)
    return score
}

/**
 * Repairs common "almost JSON" outputs from smaller local models:
 * - missing outer braces
 * - missing leading quote on a line-start key such as title":
 * - bare line-start keys such as title:
 * - missing commas between line-separated properties or entries
 */
private fun String.tryRepairJsonLikeObject(): String? {
    val normalized = trim()
    if (normalized.isBlank()) return null

    val keyLikeLineCount = normalized.lineSequence().count { line ->
        val trimmed = line.trimStart()
        trimmed.startsWith("\"") ||
            trimmed.matches(Regex("""[A-Za-z_][A-Za-z0-9_-]*"?\s*:.*"""))
    }
    if (keyLikeLineCount < 2) return null

    var repaired = normalized.repairJsonLikeKeysByLine()
    if (!repaired.trimStart().startsWith("{") && !repaired.trimStart().startsWith("[")) {
        repaired = "{\n$repaired\n}"
    }
    repaired = repaired.insertMissingJsonCommas()

    return repaired.takeIf { runCatching { auraJson.parseToJsonElement(it) }.isSuccess }
}

private fun String.repairJsonLikeKeysByLine(): String {
    return lineSequence().joinToString(separator = "\n") { line ->
        line
            .replaceFirst(
                Regex("""^(\s*)([A-Za-z_][A-Za-z0-9_-]*)"(\s*:)"""),
                "$1\"$2\"$3"
            )
            .replaceFirst(
                Regex("""^(\s*)([A-Za-z_][A-Za-z0-9_-]*)(\s*:)"""),
                "$1\"$2\"$3"
            )
    }
}

private fun String.insertMissingJsonCommas(): String {
    if (isBlank()) return this

    val out = StringBuilder(length + 16)
    var inString = false
    var escaped = false

    fun StringBuilder.lastNonWhitespaceChar(): Char? {
        for (index in length - 1 downTo 0) {
            val candidate = this[index]
            if (!candidate.isWhitespace()) return candidate
        }
        return null
    }

    fun nextNonWhitespaceChar(startIndex: Int): Char? {
        for (index in startIndex until length) {
            val candidate = this[index]
            if (!candidate.isWhitespace()) return candidate
        }
        return null
    }

    fun shouldInsertComma(previous: Char?, next: Char?): Boolean {
        if (previous == null || next == null) return false
        if (previous == '{' || previous == '[' || previous == ',' || previous == ':') return false
        if (next == '}' || next == ']' || next == ',') return false

        val previousEndsValue = previous == '"' ||
            previous == '}' ||
            previous == ']' ||
            previous.isDigit() ||
            previous == 'e' ||
            previous == 'E' ||
            previous == 'l' ||
            previous == 't' ||
            previous == 'f'
        val nextStartsToken = next == '"' ||
            next == '{' ||
            next == '[' ||
            next == '-' ||
            next.isDigit() ||
            next == 't' ||
            next == 'f' ||
            next == 'n'

        return previousEndsValue && nextStartsToken
    }

    for (index in indices) {
        val current = this[index]
        when {
            escaped -> {
                out.append(current)
                escaped = false
            }

            current == '\\' && inString -> {
                out.append(current)
                escaped = true
            }

            current == '"' -> {
                out.append(current)
                inString = !inString
            }

            current == '\n' && !inString -> {
                val previous = out.lastNonWhitespaceChar()
                val next = nextNonWhitespaceChar(index + 1)
                if (shouldInsertComma(previous, next) && out.lastOrNull() != ',') {
                    out.append(',')
                }
                out.append(current)
            }

            else -> out.append(current)
        }
    }

    return out.toString()
}

private fun normalizeTaskDslObject(root: JsonObject): JsonObject {
    val taskRoot = (root["task"] as? JsonObject) ?: root
    val type = taskRoot["type"].enumValueOrDefault(TaskType.GENERAL)
    val targetDateMs = taskRoot["targetDateMs"].epochMillisOrNull()
    val components = normalizeComponents(
        components = taskRoot["components"] as? JsonArray,
        rootTargetDateMs = targetDateMs
    )
    val skillComponents = normalizeSkillComponents(
        skills = (taskRoot["skills"] as? JsonArray) ?: (root["skills"] as? JsonArray),
        rootTargetDateMs = targetDateMs
    )
    val misplacedUiComponents = normalizeMisplacedUiComponents(
        candidates = (taskRoot["fetchers"] as? JsonArray) ?: (root["fetchers"] as? JsonArray),
        rootTargetDateMs = targetDateMs
    )
    val functionSkills = normalizeFunctionSkills(
        skills = (taskRoot["functionSkills"] as? JsonArray) ?: (root["functionSkills"] as? JsonArray)
    )

    val semanticFrame = extractSemanticFrame(root, taskRoot)
    val enrichedComponents = applySemanticToComponents(
        components = mergeNormalizedComponents(components, skillComponents, misplacedUiComponents),
        frame = semanticFrame
    )

    return JsonObject(
        mapOf(
            "title" to JsonPrimitive(
                sanitizeGeneratedText(taskRoot["title"].stringValue())
                    .ifBlank { TaskDSLValidator.PLACEHOLDER_TITLE }
            ),
            "type" to JsonPrimitive(type.name),
            "priority" to JsonPrimitive(taskRoot["priority"].intValue(default = 0).coerceIn(0, 3)),
            "targetDateMs" to (targetDateMs?.let(::JsonPrimitive) ?: JsonNull),
            "components" to enrichedComponents,
            "functionSkills" to functionSkills,
            "reminders" to normalizeReminders(taskRoot["reminders"] as? JsonArray),
            "fetchers" to normalizeFetchers(taskRoot["fetchers"] as? JsonArray)
        )
    )
}

private fun mergeNormalizedComponents(vararg arrays: JsonArray): JsonArray {
    val merged = arrays
        .flatMap { it }
        .mapNotNull { it as? JsonObject }
        .distinctBy { component ->
            buildString {
                append(component["skillId"])
                append('|')
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

    return JsonArray(merged)
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
                append(component["skillId"])
                append('|')
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

private fun normalizeSkillComponents(
    skills: JsonArray?,
    rootTargetDateMs: Long?
): JsonArray {
    if (skills == null) return JsonArray(emptyList())

    val normalized = skills
        .mapNotNull { normalizeSkillComponent(it as? JsonObject, rootTargetDateMs) }
        .distinctBy { component ->
            buildString {
                append(component["skillId"])
                append('|')
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

private fun normalizeMisplacedUiComponents(
    candidates: JsonArray?,
    rootTargetDateMs: Long?
): JsonArray {
    if (candidates == null) return JsonArray(emptyList())

    val normalized = candidates
        .mapNotNull { candidate ->
            val obj = candidate as? JsonObject ?: return@mapNotNull null
            if ("cronExpression" in obj || "params" in obj) return@mapNotNull null

            normalizeSkillComponent(obj, rootTargetDateMs)
                ?: normalizeComponent(obj, rootTargetDateMs)
        }
        .distinctBy { component ->
            buildString {
                append(component["skillId"])
                append('|')
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

private fun normalizeSkillComponent(
    skill: JsonObject?,
    rootTargetDateMs: Long?
): JsonObject? {
    skill ?: return null
    val requestedSkillId = skill["skill"].stringValue()
        .ifBlank { skill["name"].stringValue() }
        .ifBlank { skill["skillId"].stringValue() }
    val normalizedSkillId = UiSkillRegistry.resolve(requestedSkillId)?.id ?: requestedSkillId.ifBlank { null }
    val definition = normalizedSkillId?.let(UiSkillRegistry::resolve)
    val forcedType = definition?.componentType
    return normalizeComponent(
        component = skill,
        rootTargetDateMs = rootTargetDateMs,
        forcedType = forcedType,
        skillId = normalizedSkillId,
        skillRuntime = definition?.runtime
    )
}

private fun normalizeComponent(
    component: JsonObject?,
    rootTargetDateMs: Long?,
    forcedType: ComponentType? = null,
    skillId: String? = null,
    skillRuntime: UiSkillRuntime? = null
): JsonObject? {
    component ?: return null
    val type = forcedType ?: component.resolveComponentType() ?: return null
    val config = normalizeComponentConfig(
        type = type,
        component = component,
        rootTargetDateMs = rootTargetDateMs
    )
    val effectiveSkillId = skillId
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: component["skillId"].stringValue().takeIf { it.isNotBlank() }
        ?: component["skill"].stringValue().takeIf { it.isNotBlank() }
    val effectiveRuntime = skillRuntime
        ?: component["skillRuntime"].enumValueOrNull<UiSkillRuntime>()
        ?: effectiveSkillId?.let { SkillRegistry.resolveUi(it)?.runtime }

    return JsonObject(
        mapOf(
            "skillId" to (effectiveSkillId?.let(::JsonPrimitive) ?: JsonNull),
            "skillRuntime" to (effectiveRuntime?.let { JsonPrimitive(it.name) } ?: JsonNull),
            "type" to JsonPrimitive(type.name),
            "sortOrder" to JsonPrimitive(0),
            "config" to config,
            "populatedFromInput" to JsonPrimitive(component["populatedFromInput"].booleanValue(default = false)),
            "needsClarification" to JsonPrimitive(component["needsClarification"].booleanValue(default = false))
        )
    )
}

private fun normalizeFunctionSkills(
    skills: JsonArray?
): JsonArray {
    if (skills == null) return JsonArray(emptyList())

    val normalized = skills
        .mapNotNull { normalizeFunctionSkill(it as? JsonObject) }
        .distinctBy { skill ->
            buildString {
                append(skill["skillId"])
                append('|')
                append(skill["runtime"])
                append('|')
                append(skill["config"])
                append('|')
                append(skill["enabled"])
                append('|')
                append(skill["needsClarification"])
            }
        }

    return JsonArray(normalized)
}

private fun normalizeFunctionSkill(
    skill: JsonObject?
): JsonObject? {
    skill ?: return null

    val requestedSkillId = skill["skill"].stringValue()
        .ifBlank { skill["name"].stringValue() }
        .ifBlank { skill["skillId"].stringValue() }
    val normalizedSkillId = SkillRegistry.resolveFunction(requestedSkillId)?.id
        ?: requestedSkillId.ifBlank { null }
        ?: return null
    val runtime = skill["runtime"].enumValueOrNull<FunctionSkillRuntime>()
        ?: SkillRegistry.resolveFunction(normalizedSkillId)?.runtime
        ?: FunctionSkillRuntime.PROMPT_AUGMENTATION
    val config = (skill["config"] as? JsonObject) ?: JsonObject(emptyMap())

    return JsonObject(
        mapOf(
            "skillId" to JsonPrimitive(normalizedSkillId),
            "runtime" to JsonPrimitive(runtime.name),
            "enabled" to JsonPrimitive(skill["enabled"].booleanValue(default = true)),
            "config" to config,
            "populatedFromInput" to JsonPrimitive(skill["populatedFromInput"].booleanValue(default = false)),
            "needsClarification" to JsonPrimitive(skill["needsClarification"].booleanValue(default = false))
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
            merged["label"] = JsonPrimitive(
                sanitizeGeneratedText(merged["label"].stringValue()).ifBlank { "Checklist" }
            )
            merged["allowAddItems"] = JsonPrimitive(merged["allowAddItems"].booleanValue(default = true))
            normalizeChecklistItems(merged["items"])?.let { merged["items"] = it } ?: merged.remove("items")
        }

        ComponentType.COUNTDOWN -> {
            val targetDate = merged["targetDate"].epochMillisOrNull() ?: rootTargetDateMs ?: 0L
            merged["targetDate"] = JsonPrimitive(targetDate)
            merged["label"] = JsonPrimitive(
                sanitizeGeneratedText(merged["label"].stringValue()).ifBlank { "Countdown" }
            )
        }

        ComponentType.NOTES -> {
            merged["text"] = JsonPrimitive(sanitizeGeneratedText(merged["text"].stringValue()))
            merged["isMarkdown"] = JsonPrimitive(merged["isMarkdown"].booleanValue(default = true))
        }

        ComponentType.PROGRESS_BAR -> {
            merged["source"] = JsonPrimitive(merged["source"].stringValue().ifBlank { "MANUAL" })
            merged["label"] = JsonPrimitive(sanitizeGeneratedText(merged["label"].stringValue()))
            merged["manualProgress"] = merged["manualProgress"].floatValueOrNull()?.let(::JsonPrimitive) ?: JsonNull
        }

        ComponentType.HABIT_RING -> {
            merged["frequency"] = JsonPrimitive(merged["frequency"].stringValue().ifBlank { "DAILY" })
            merged["label"] = JsonPrimitive(sanitizeGeneratedText(merged["label"].stringValue()))
            merged["targetCount"] = merged["targetCount"].intValueOrNull()?.let(::JsonPrimitive) ?: JsonNull
            merged["completedToday"] = JsonPrimitive(merged["completedToday"].booleanValue(default = false))
            merged["streakCount"] = JsonPrimitive(merged["streakCount"].intValue(default = 0))
        }

        ComponentType.METRIC_TRACKER -> {
            merged["unit"] = JsonPrimitive(sanitizeGeneratedText(merged["unit"].stringValue()))
            merged["label"] = JsonPrimitive(sanitizeGeneratedText(merged["label"].stringValue()))
            merged["goal"] = merged["goal"].floatValueOrNull()?.let(::JsonPrimitive) ?: JsonNull
            merged["history"] = normalizeNumberArray(merged["history"])
        }

        ComponentType.DATA_FEED -> {
            merged["fetcherConfigId"] = JsonPrimitive(merged["fetcherConfigId"].stringValue())
            merged["displayLabel"] = JsonPrimitive(
                sanitizeGeneratedText(merged["displayLabel"].stringValue())
            )
            val status = merged["status"].enumValueOrDefault(DataFeedStatus.DATA)
            merged["status"] = JsonPrimitive(status.name)
            merged["value"] = merged["value"]?.let(::normalizeOptionalString) ?: JsonNull
            merged["lastValue"] = merged["lastValue"]?.let(::normalizeOptionalString) ?: JsonNull
            merged["lastUpdatedAt"] = merged["lastUpdatedAt"].epochMillisOrNull()?.let(::JsonPrimitive) ?: JsonNull
            merged["errorMessage"] = merged["errorMessage"]?.let(::normalizeOptionalString) ?: JsonNull
        }

        ComponentType.HOSTED_UI -> {
            val runtime = merged["runtime"].enumValueOrDefault(UiSkillRuntime.HTML_JS)
            merged["skillId"] = JsonPrimitive(merged["skillId"].stringValue())
            merged["runtime"] = JsonPrimitive(runtime.name)
            merged["displayLabel"] = JsonPrimitive(
                sanitizeGeneratedText(merged["displayLabel"].stringValue())
            )
            merged["composeHostId"] = merged["composeHostId"]?.let(::normalizeOptionalString) ?: JsonNull
            merged["htmlDocument"] = merged["htmlDocument"]?.let(::normalizeOptionalString) ?: JsonNull
            merged["sourceAssetPath"] = merged["sourceAssetPath"]?.let(::normalizeOptionalString) ?: JsonNull
            merged["entrypoint"] = JsonPrimitive(
                merged["entrypoint"].stringValue().ifBlank { "ai_edge_gallery_get_result" }
            )
            merged["props"] = (merged["props"] as? JsonObject) ?: JsonObject(emptyMap())
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
    this["skill"].stringValue()
        .takeIf { it.isNotBlank() }
        ?.let { SkillRegistry.resolveUi(it)?.componentType }
        ?.let { return it }
    this["skillId"].stringValue()
        .takeIf { it.isNotBlank() }
        ?.let { SkillRegistry.resolveUi(it)?.componentType }
        ?.let { return it }

    return when {
        hasAnyKey("htmlDocument", "sourceAssetPath", "composeHostId", "props") -> ComponentType.HOSTED_UI
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
    val value = sanitizeGeneratedText(element.stringValue()).ifBlank { "" }
    return if (value.isBlank()) JsonNull else JsonPrimitive(value)
}

private fun sanitizeGeneratedText(text: String): String {
    return DraftContentQuality.sanitizeGeneratedText(text)
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
                            "true", "1", "yes" -> true
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

// ── Semantic Decomposition Layer ──────────────────────────────────────────────

internal fun extractSemanticFrame(
    root: JsonObject,
    taskRoot: JsonObject? = null
): SemanticFrame {
    val semantic = root["semantic"] as? JsonObject
        ?: (taskRoot?.get("semantic") as? JsonObject)
        ?: ((root["analysis"] as? JsonObject)?.get("semantic") as? JsonObject)
        ?: return SemanticFrame.EMPTY

    val action = semantic["action"].stringValue()
    val subject = semantic["subject"].stringValue()
    val goal = semantic["goal"].stringValue()
    val frequency = semantic["frequency"].stringValue()
    val items = extractSemanticItems(semantic["items"])

    if (action.isBlank() && items.isEmpty() && subject.isBlank() && goal.isBlank() && frequency.isBlank()) {
        return SemanticFrame.EMPTY
    }

    return SemanticFrame(action = action, items = items, subject = subject, goal = goal, frequency = frequency)
}

private fun extractSemanticItems(element: JsonElement?): List<String> {
    return when (element) {
        is JsonArray -> element.mapNotNull { item ->
            item.stringValue()
                .trim()
                .takeIf { it.isNotBlank() && it.length <= MAX_SEMANTIC_ITEM_LENGTH }
        }.distinct()

        is JsonPrimitive -> {
            val raw = element.contentOrNull?.trim().orEmpty()
            if (raw.isBlank()) {
                emptyList()
            } else {
                ChecklistInputExtraction.extract(raw)
                    .ifEmpty {
                        raw.split(Regex("[,;\\n]+"))
                            .map { it.trim() }
                            .filter { it.isNotBlank() && looksLikeAtomicSemanticItem(it) }
                            .distinct()
                    }
                    .ifEmpty {
                        listOf(raw).filter { it.length <= MAX_SEMANTIC_ITEM_LENGTH && looksLikeAtomicSemanticItem(it) }
                    }
            }
        }

        else -> emptyList()
    }
}

internal fun applySemanticToComponents(
    components: JsonArray,
    frame: SemanticFrame
): JsonArray {
    if (frame == SemanticFrame.EMPTY) return components

    val updated = components.map { element ->
        val component = element as? JsonObject ?: return@map element
        val type = component["type"].stringValue()

        when {
            type == ComponentType.CHECKLIST.name && frame.hasItems && semanticItemsAreSpecific(frame) ->
                applySemanticToChecklist(component, frame)
            type == ComponentType.HABIT_RING.name && frame.hasFrequency ->
                applySemanticToHabitRing(component, frame)
            else -> component
        }
    }

    return JsonArray(updated)
}

private fun applySemanticToChecklist(
    component: JsonObject,
    frame: SemanticFrame
): JsonObject {
    val config = component["config"] as? JsonObject ?: return component

    val semanticItems = JsonArray(
        frame.items.map { itemLabel ->
            JsonObject(
                mapOf(
                    "label" to JsonPrimitive(itemLabel),
                    "isSuggested" to JsonPrimitive(false)
                )
            )
        }
    )

    val existingItems = config["items"] as? JsonArray
    val shouldReplace = existingItems == null
        || existingItems.isEmpty()
        || existingItemsLookNoisy(existingItems, frame)

    if (!shouldReplace) return component

    val updatedLabel = if (frame.hasSubject) {
        JsonPrimitive(frame.subject)
    } else {
        config["label"] ?: JsonPrimitive("Checklist")
    }

    val updatedConfig = JsonObject(
        config + mapOf(
            "items" to semanticItems,
            "label" to updatedLabel
        )
    )

    return JsonObject(
        component + mapOf(
            "config" to updatedConfig,
            "populatedFromInput" to JsonPrimitive(true),
            "needsClarification" to JsonPrimitive(false)
        )
    )
}

private fun applySemanticToHabitRing(
    component: JsonObject,
    frame: SemanticFrame
): JsonObject {
    val config = component["config"] as? JsonObject ?: return component
    val normalizedFrequency = when (frame.frequency.trim().uppercase(Locale.US)) {
        "WEEKLY", "WEEK" -> "WEEKLY"
        else -> "DAILY"
    }
    val updatedConfig = JsonObject(config + ("frequency" to JsonPrimitive(normalizedFrequency)))
    return JsonObject(component + ("config" to updatedConfig))
}

private fun existingItemsLookNoisy(
    items: JsonArray,
    frame: SemanticFrame
): Boolean {
    if (items.isEmpty()) return true

    val labels = items.mapNotNull { item ->
        when (item) {
            is JsonPrimitive -> item.contentOrNull?.trim()
            is JsonObject -> item["label"].stringValue().takeIf { it.isNotBlank() }
            else -> null
        }
    }

    if (labels.isEmpty()) return true

    // If many items are long (>4 words), they likely contain description text
    val longItemRatio = labels.count { it.split(Regex("\\s+")).size > 4 }.toFloat() / labels.size
    if (longItemRatio >= 0.5f) return true

    // If any item contains action words from the semantic frame, it's description leakage
    val actionWords = frame.action.lowercase()
        .split(Regex("\\s+"))
        .filter { it.length >= 3 }
    if (actionWords.isNotEmpty()) {
        val leakyCount = labels.count { label ->
            val labelLower = label.lowercase()
            actionWords.any { verb -> labelLower.contains(verb) }
        }
        if (leakyCount.toFloat() / labels.size > 0.3f) return true
    }

    return false
}

private fun semanticItemsAreSpecific(frame: SemanticFrame): Boolean {
    if (!frame.hasItems) return false

    val candidateItems = frame.items.filter(::looksLikeAtomicSemanticItem)
    if (candidateItems.isEmpty()) return false

    val referenceTokens = tokenizeSemanticText(
        listOf(frame.action, frame.subject, frame.goal)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    )

    val novelItems = candidateItems.count { item ->
        val itemTokens = tokenizeSemanticText(item)
        itemTokens.isNotEmpty() && itemTokens.any { token -> token !in referenceTokens }
    }

    return when (candidateItems.size) {
        1 -> novelItems == 1
        else -> novelItems * 2 >= candidateItems.size
    }
}

private const val MAX_SEMANTIC_ITEM_LENGTH = 80

private fun looksLikeAtomicSemanticItem(item: String): Boolean {
    if (item.length > MAX_SEMANTIC_ITEM_LENGTH) return false
    val words = item.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return words.size in 1..4
}

private fun tokenizeSemanticText(text: String): Set<String> {
    return text.lowercase()
        .split(Regex("""[^\p{L}\p{N}]+"""))
        .mapNotNull { token ->
            token.trim()
                .takeIf { it.length >= 3 }
                ?.let(::normalizeSemanticToken)
        }
        .toSet()
}

private fun normalizeSemanticToken(token: String): String {
    return if (token.endsWith("s") && token.length > 4) {
        token.dropLast(1)
    } else {
        token
    }
}

// ── End Semantic Decomposition Layer ─────────────────────────────────────────

private val COMPONENT_METADATA_KEYS = setOf(
    "type",
    "sortOrder",
    "skillId",
    "skill",
    "skillRuntime",
    "config",
    "populatedFromInput",
    "needsClarification"
)

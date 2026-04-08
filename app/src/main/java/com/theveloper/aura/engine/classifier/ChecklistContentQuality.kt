package com.theveloper.aura.engine.classifier

import com.theveloper.aura.engine.dsl.ChecklistItemDSL

internal object ChecklistContentQuality {

    fun sanitizeDslItems(
        items: List<ChecklistItemDSL>,
        taskTitle: String = ""
    ): List<ChecklistItemDSL> {
        val sanitized = mutableListOf<ChecklistItemDSL>()

        items.forEach { item ->
            val normalizedLabel = normalizeCandidate(item.label)
            if (!looksLikeConcreteItem(normalizedLabel, taskTitle)) return@forEach

            val candidate = item.copy(label = normalizedLabel)
            val relatedIndex = sanitized.indexOfFirst { existing ->
                areRelatedKeys(comparisonKey(existing.label), comparisonKey(normalizedLabel))
            }

            if (relatedIndex == -1) {
                sanitized += candidate
            } else {
                val existing = sanitized[relatedIndex]
                sanitized[relatedIndex] = preferMoreSpecific(existing, candidate, taskTitle)
            }
        }

        return sanitized
    }

    fun sanitizeItems(
        items: List<String>,
        taskTitle: String = ""
    ): List<String> {
        return sanitizeDslItems(
            items = items.map(::ChecklistItemDSL),
            taskTitle = taskTitle
        ).map { it.label }
    }

    fun normalizeCandidate(raw: String): String {
        val stripped = raw
            .trim()
            .replace(CHECKBOX_PREFIX_REGEX, "")
            .replace(LIST_PREFIX_REGEX, "")
            .replace(MULTISPACE_REGEX, " ")
            .trim(' ', '.', ':', ';', ',')
        if (stripped.isBlank()) return ""

        val rawTokens = stripped
            .split(WHITESPACE_REGEX)
            .map { token -> token.trim(' ', '.', ':', ';', ',') }
            .filter { token -> token.isNotBlank() }

        val trimmedLeadingFragments = rawTokens.dropWhile { token ->
            rawTokens.size > 1 && isShortAlphabeticFragment(token)
        }

        val compacted = mutableListOf<String>()
        trimmedLeadingFragments.forEachIndexed { index, token ->
            val next = trimmedLeadingFragments.getOrNull(index + 1)

            if (
                isShortAlphabeticFragment(token) &&
                next != null &&
                next.length > token.length &&
                next.startsWith(token, ignoreCase = true)
            ) {
                return@forEachIndexed
            }

            if (compacted.lastOrNull()?.equals(token, ignoreCase = true) == true) {
                return@forEachIndexed
            }

            compacted += token
        }

        return compacted
            .joinToString(" ")
            .replace(MULTISPACE_REGEX, " ")
            .trim(' ', '.', ':', ';', ',')
    }

    fun looksLikeConcreteItem(
        candidate: String,
        taskTitle: String = ""
    ): Boolean {
        val normalized = normalizeCandidate(candidate)
        if (normalized.isBlank()) return false

        val normalizedLower = normalized.lowercase()
        if (normalizedLower in BANNED_WHOLE_LINES) return false
        if (normalized.any { char -> char == '?' || char == '!' || char == ':' }) return false

        val rawTokens = normalized.split(WHITESPACE_REGEX).filter { token -> token.isNotBlank() }
        if (rawTokens.size !in 1..6) return false
        if (hasRunawayTokenRepetition(rawTokens)) return false

        val loweredTokens = rawTokens.map { token -> token.lowercase() }
        val semanticTokens = tokenizeText(normalized)
        if (semanticTokens.isEmpty()) return false

        if (semanticTokens.size == 1 && semanticTokens.first() in GENERIC_SINGLE_ITEM_TOKENS) {
            return false
        }

        val titleTokens = tokenizeText(taskTitle)
        val concreteTokens = semanticTokens - titleTokens - ABSTRACT_CONTAINER_TOKENS
        if (concreteTokens.isEmpty()) return false

        val abstractTokenCount = loweredTokens.count { token -> token in ABSTRACT_CONTAINER_TOKENS }
        if (abstractTokenCount > 0 && concreteTokens.size <= 1) return false

        return true
    }

    fun itemsLookUsable(
        items: List<String>,
        taskTitle: String = ""
    ): Boolean {
        val cleaned = sanitizeItems(items, taskTitle)
        if (cleaned.isEmpty()) return false

        val titleTokens = tokenizeText(taskTitle)
        val informativeCount = cleaned.count { label ->
            val concreteTokens = tokenizeText(label) - titleTokens - ABSTRACT_CONTAINER_TOKENS
            concreteTokens.isNotEmpty()
        }
        if (informativeCount == 0) return false

        val genericOverlapRatio = cleaned.count { label ->
            val itemTokens = tokenizeText(label)
            itemTokens.isNotEmpty() &&
                titleTokens.isNotEmpty() &&
                itemTokens.count { token -> token in titleTokens }.toFloat() / itemTokens.size.toFloat() >= 0.67f
        }.toFloat() / cleaned.size.toFloat()

        return !(cleaned.size <= 3 && genericOverlapRatio >= 0.67f)
    }

    fun tokenizeText(text: String): Set<String> {
        return text.lowercase()
            .split(NON_WORD_SPLIT_REGEX)
            .mapNotNull { token ->
                token.trim()
                    .takeIf { it.length >= 3 }
                    ?.removeSuffix("s")
            }
            .toSet()
    }

    fun comparisonKey(item: String): String {
        return normalizeCandidate(item).lowercase()
    }

    fun areRelatedItems(
        left: String,
        right: String
    ): Boolean {
        return areRelatedKeys(comparisonKey(left), comparisonKey(right))
    }

    private fun preferMoreSpecific(
        existing: ChecklistItemDSL,
        candidate: ChecklistItemDSL,
        taskTitle: String
    ): ChecklistItemDSL {
        val existingScore = specificityScore(existing.label, taskTitle)
        val candidateScore = specificityScore(candidate.label, taskTitle)

        return when {
            candidateScore > existingScore -> candidate.copy(isSuggested = existing.isSuggested && candidate.isSuggested)
            existingScore > candidateScore -> existing.copy(isSuggested = existing.isSuggested && candidate.isSuggested)
            candidate.label.length > existing.label.length ->
                candidate.copy(isSuggested = existing.isSuggested && candidate.isSuggested)
            else -> existing.copy(isSuggested = existing.isSuggested && candidate.isSuggested)
        }
    }

    private fun specificityScore(
        item: String,
        taskTitle: String
    ): Int {
        val concreteTokenCount = (tokenizeText(item) - tokenizeText(taskTitle) - ABSTRACT_CONTAINER_TOKENS).size
        return concreteTokenCount * 10 + item.length.coerceAtMost(40)
    }

    private fun areRelatedKeys(
        left: String,
        right: String
    ): Boolean {
        return left == right ||
            left.endsWith(" $right") ||
            right.endsWith(" $left")
    }

    private fun hasRunawayTokenRepetition(tokens: List<String>): Boolean {
        val lowered = tokens.map { token -> token.lowercase() }
        val counts = lowered.groupingBy { it }.eachCount()
        val mostRepeated = counts.values.maxOrNull() ?: 0

        return when {
            mostRepeated >= 3 -> true
            lowered.size >= 4 && counts.size * 2 < lowered.size -> true
            else -> false
        }
    }

    private fun isShortAlphabeticFragment(token: String): Boolean {
        return token.length <= 2 && token.all(Char::isLetter)
    }

    private val BANNED_WHOLE_LINES = setOf(
        "true",
        "false",
        "null",
        "none",
        "final output",
        "define next step",
        "execute",
        "review result",
        "definir siguiente paso",
        "ejecutar",
        "revisar resultado"
    )

    private val ABSTRACT_CONTAINER_TOKENS = setOf(
        "ingredient", "ingredients", "ingrediente", "ingredientes",
        "item", "items", "step", "steps", "paso", "pasos",
        "recipe", "recipes", "receta", "recetas",
        "shopping", "compras", "compra", "list", "lista", "listas",
        "mix", "mixture", "mezcla", "bundle", "kit", "set", "supply", "supplies",
        "instruction", "instructions", "instruccion", "instrucciones",
        "guide", "guides", "tip", "tips", "nota", "notas", "note", "notes"
    )

    private val GENERIC_SINGLE_ITEM_TOKENS = ABSTRACT_CONTAINER_TOKENS + setOf("mold", "mould")

    private val CHECKBOX_PREFIX_REGEX = Regex("""^\[\s*[xX ]?\s*]\s+""")
    private val LIST_PREFIX_REGEX = Regex("""^\s*(?:[-*•]\s+|\d+[.)]\s+)""")
    private val MULTISPACE_REGEX = Regex("""\s+""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val NON_WORD_SPLIT_REGEX = Regex("""[^\p{L}\p{N}]+""")
}

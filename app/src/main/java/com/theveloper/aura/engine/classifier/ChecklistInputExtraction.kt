package com.theveloper.aura.engine.classifier

internal object ChecklistInputExtraction {

    const val CLARIFICATION_ANSWER_PREFIX = ClassifierInputProtocol.CLARIFICATION_TAG

    fun extract(rawInput: String): List<String> {
        val sanitizedLines = sanitizeLines(rawInput)

        sanitizedLines
            .asSequence()
            .mapNotNull { line -> ClassifierInputProtocol.extractTaggedValue(line, CLARIFICATION_ANSWER_PREFIX) }
            .filter { it.isNotBlank() }
            .flatMap(::splitItems)
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        val structuralLineItems = sanitizedLines
            .mapNotNull(::extractStructuralLineItem)
            .distinct()
        if (structuralLineItems.size >= 2) return structuralLineItems

        val labeledCandidates = sanitizedLines
            .mapNotNull { line ->
                line.substringAfter(':', missingDelimiterValue = "")
                    .trim()
                    .takeIf { it.isNotBlank() }
            }

        labeledCandidates.forEach { candidate ->
            parseInlineAtomicList(candidate, minimumItems = 2)?.let { return it }
        }

        return parseInlineAtomicList(sanitizedLines.joinToString(" "), minimumItems = 3).orEmpty()
    }

    private fun sanitizeLines(rawInput: String): List<String> {
        return rawInput.lineSequence()
            .map { it.trim() }
            .filterNot { line ->
                line.isBlank() ||
                    ClassifierInputProtocol.extractTaggedValue(line, ClassifierInputProtocol.PREFERRED_TITLE_TAG) != null ||
                    ClassifierInputProtocol.extractTaggedValue(line, ClassifierInputProtocol.TASK_TYPE_HINT_TAG) != null
            }
            .toList()
    }

    private fun parseInlineAtomicList(
        raw: String,
        minimumItems: Int
    ): List<String>? {
        if (!raw.contains(',') && !raw.contains(';')) return null
        val normalizedItems = splitItems(raw)
        if (normalizedItems.size < minimumItems) return null
        val items = normalizedItems.filter(::looksLikeAtomicItem)
        if (items.size < minimumItems) return null
        val atomicRatio = items.size.toFloat() / normalizedItems.size.toFloat()
        if (atomicRatio < 0.75f) return null
        return items
    }

    private fun splitItems(raw: String): List<String> {
        return raw.split(ITEM_SEPARATOR_REGEX)
            .map(::normalizeItemText)
            .filter { it.isNotBlank() && it.length > 1 }
            .distinct()
    }

    private fun extractStructuralLineItem(line: String): String? {
        val match = STRUCTURAL_LINE_ITEM_REGEX.matchEntire(line.trim()) ?: return null
        return normalizeItemText(match.groupValues[1]).takeIf { it.isNotBlank() }
    }

    private fun normalizeItemText(raw: String): String {
        return raw
            .trim()
            .replace(CHECKBOX_PREFIX_REGEX, "")
            .trim('-', '*', '•', '.', ':')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun looksLikeAtomicItem(item: String): Boolean {
        val tokens = item.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size !in 1..4) return false
        return item.none { it == '?' || it == '!' || it == ':' }
    }

    private val ITEM_SEPARATOR_REGEX = Regex("[,;\\n]+")
    private val STRUCTURAL_LINE_ITEM_REGEX = Regex(
        """(?:[-*•]\s+|\[\s*[xX ]?\s*]\s+|\d+[.)]\s+)(.+)"""
    )
    private val CHECKBOX_PREFIX_REGEX = Regex("""^\[\s*[xX ]?\s*]\s+""")
}

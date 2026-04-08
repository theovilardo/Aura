package com.theveloper.aura.engine.classifier

internal object DraftContentQuality {

    fun sanitizeGeneratedText(text: String): String {
        val trimmed = text.trim()
        return if (looksLikePromptSchemaPlaceholder(trimmed)) {
            ""
        } else {
            collapseExcessiveLineRepetition(trimmed)
        }
    }

    fun isThinNotes(text: String): Boolean {
        val trimmed = sanitizeGeneratedText(text)
        if (trimmed.isBlank()) return true

        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return true
        if (containsHollowScaffolding(lines)) return true
        if (hasStructuredMarkdown(lines)) {
            val contentLines = lines.filterNot { MARKDOWN_HEADING_REGEX.containsMatchIn(it) }
            return contentLines.size < 2
        }

        val wordCount = WORD_REGEX.findAll(trimmed).count()
        if (wordCount <= 12) return true

        val sentenceCount = SENTENCE_END_REGEX.findAll(trimmed).count()
        return lines.size <= 2 && sentenceCount <= 2 && wordCount <= 24
    }

    fun looksLikePromptSchemaPlaceholder(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        val normalized = trimmed.lowercase()
        return PLACEHOLDER_PATTERNS.any { pattern -> pattern.containsMatchIn(normalized) }
    }

    private fun hasStructuredMarkdown(lines: List<String>): Boolean {
        return lines.any { line ->
            MARKDOWN_HEADING_REGEX.containsMatchIn(line) ||
                BULLET_LINE_REGEX.containsMatchIn(line) ||
                TABLE_ROW_REGEX.containsMatchIn(line) ||
                CODE_FENCE_REGEX.containsMatchIn(line)
        }
    }

    private fun containsHollowScaffolding(lines: List<String>): Boolean {
        val placeholderLineCount = lines.count { line ->
            BRACKET_PLACEHOLDER_REGEX.containsMatchIn(line) ||
                PLACEHOLDER_LINE_REGEX.containsMatchIn(line)
        }
        if (placeholderLineCount > 0) return true

        val contentLines = lines.filterNot { MARKDOWN_HEADING_REGEX.containsMatchIn(it) }
        if (contentLines.isEmpty()) return true

        val bracketedRatio = contentLines.count { line ->
            BRACKETED_CONTENT_REGEX.containsMatchIn(line)
        }.toFloat() / contentLines.size.toFloat()

        return bracketedRatio >= 0.5f
    }

    private fun collapseExcessiveLineRepetition(text: String): String {
        if (text.isBlank()) return ""

        val keptLines = mutableListOf<String>()
        val seenCounts = linkedMapOf<String, Int>()
        var previousKey: String? = null
        var pendingBlankLine = false

        text.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            val normalized = normalizeLineForRepetition(line)

            if (normalized.isBlank()) {
                if (keptLines.isNotEmpty()) pendingBlankLine = true
                previousKey = null
                return@forEach
            }

            if (DANGLING_LINE_REGEX.matches(line)) return@forEach
            if (normalized == previousKey) return@forEach

            val nextCount = seenCounts.getOrDefault(normalized, 0) + 1
            if (nextCount > allowedOccurrencesForLine(line, normalized)) {
                previousKey = normalized
                return@forEach
            }

            if (pendingBlankLine && keptLines.isNotEmpty()) {
                keptLines += ""
                pendingBlankLine = false
            }

            keptLines += line
            seenCounts[normalized] = nextCount
            previousKey = normalized
        }

        return keptLines.joinToString("\n").trim()
    }

    private fun normalizeLineForRepetition(line: String): String {
        return line
            .trim()
            .replace(Regex("""^\s*(?:[-*•]|\d+[.)])\s+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '.', ':', ';', ',')
            .lowercase()
    }

    private fun allowedOccurrencesForLine(line: String, normalized: String): Int {
        return when {
            MARKDOWN_HEADING_REGEX.containsMatchIn(line) -> 1
            BULLET_LINE_REGEX.containsMatchIn(line) -> 1
            normalized.split(Regex("""\s+""")).size <= 4 -> 1
            else -> 2
        }
    }

    private val WORD_REGEX = Regex("""[\p{L}\p{N}]+(?:['’-][\p{L}\p{N}]+)?""")
    private val SENTENCE_END_REGEX = Regex("""[.!?]+""")
    private val MARKDOWN_HEADING_REGEX = Regex("""^\s*#{1,6}\s+\S""")
    private val BULLET_LINE_REGEX = Regex("""^\s*(?:[-*•]|\d+[.)])\s+\S""")
    private val DANGLING_LINE_REGEX = Regex("""^\s*(?:[-*•]+|[:;,.\-]+)\s*$""")
    private val TABLE_ROW_REGEX = Regex("""^\s*\|.+\|\s*$""")
    private val CODE_FENCE_REGEX = Regex("""^\s*```""")
    private val BRACKET_PLACEHOLDER_REGEX = Regex("""^\s*(?:[-*•]|\d+[.)])?\s*\[[^]]{6,}]$""")
    private val BRACKETED_CONTENT_REGEX = Regex("""^\s*(?:[-*•]|\d+[.)])?\s*\[[^]]+]\s*$""")
    private val PLACEHOLDER_LINE_REGEX = Regex(
        """^\s*(?:[-*•]|\d+[.)])?\s*(?:<[^>]+>|_{3,}|\.{3,})\s*$"""
    )
    private val PLACEHOLDER_PATTERNS = listOf(
        Regex("""\bmarkdown in the user's language\b"""),
        Regex("""\bshort label in the user's language\b"""),
        Regex("""\bdisplay label in the user's language\b"""),
        Regex("""\bprecise unit in the user's language(?: or notation)?\b"""),
        Regex("""\breturn only valid json\b"""),
        Regex("""\brequired\.?\s*never empty\b"""),
        Regex("""\bsame language as the user's input\b""")
    )
}

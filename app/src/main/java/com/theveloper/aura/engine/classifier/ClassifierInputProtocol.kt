package com.theveloper.aura.engine.classifier

internal object ClassifierInputProtocol {

    const val PREFERRED_TITLE_TAG = "[[preferred_title]]:"
    const val TASK_TYPE_HINT_TAG = "[[task_type_hint]]:"
    const val CLARIFICATION_TAG = "[[clarification]]:"

    private val SYSTEM_TAGS = listOf(
        PREFERRED_TITLE_TAG,
        TASK_TYPE_HINT_TAG,
        CLARIFICATION_TAG
    )

    fun isSystemLine(line: String): Boolean {
        val trimmed = line.trim()
        return SYSTEM_TAGS.any { tag -> trimmed.startsWith(tag, ignoreCase = true) }
    }

    fun extractTaggedValue(line: String, tag: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith(tag, ignoreCase = true)) return null
        return trimmed.substring(tag.length).trim()
    }
}

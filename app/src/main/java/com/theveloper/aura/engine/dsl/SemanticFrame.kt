package com.theveloper.aura.engine.dsl

/**
 * Transient semantic decomposition of a user prompt.
 * Extracted from the LLM's JSON output at parse time, used to deterministically
 * populate component configs, then discarded.
 *
 * The model is instructed to decompose the input into:
 * - [action]: what the user wants to do (verb phrase)
 * - [items]: individual atomic data objects / steps / elements
 * - [subject]: the context, target, or topic of the task
 */
internal data class SemanticFrame(
    val action: String,
    val items: List<String>,
    val subject: String
) {
    val hasItems: Boolean get() = items.isNotEmpty()
    val hasSubject: Boolean get() = subject.isNotBlank()

    companion object {
        val EMPTY = SemanticFrame(action = "", items = emptyList(), subject = "")
    }
}

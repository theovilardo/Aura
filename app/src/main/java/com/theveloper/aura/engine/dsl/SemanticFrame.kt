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
 * - [goal]: measurable outcome or end result, if any (e.g. "lose fat", "run 5km", "save $500")
 * - [frequency]: recurrence hint, if any (e.g. "daily", "weekly", "routine")
 *
 * [goal] and [frequency] are informational only — they guide component selection
 * in the LLM prompt but are not directly applied to component configs by the bridge layer.
 */
internal data class SemanticFrame(
    val action: String,
    val items: List<String>,
    val subject: String,
    val goal: String = "",
    val frequency: String = ""
) {
    val hasItems: Boolean get() = items.isNotEmpty()
    val hasSubject: Boolean get() = subject.isNotBlank()
    val hasGoal: Boolean get() = goal.isNotBlank()
    val hasFrequency: Boolean get() = frequency.isNotBlank()

    companion object {
        val EMPTY = SemanticFrame(action = "", items = emptyList(), subject = "", goal = "", frequency = "")
    }
}

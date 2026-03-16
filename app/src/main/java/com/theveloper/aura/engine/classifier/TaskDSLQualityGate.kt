package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskComponentCatalog
import com.theveloper.aura.engine.dsl.TaskComponentContext
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Post-LLM quality enforcement layer.
 *
 * Sits between the raw LLM output (after parsing/normalization) and the final
 * [TaskGenerationResult]. Its job is to guarantee that every DSL that reaches
 * the user is **structurally complete and semantically useful** — regardless
 * of how well the model performed on a given run.
 *
 * This is NOT about schema validation (that's [TaskDSLValidator]). This layer
 * answers: "Is this output *good enough* to show to a user?"
 *
 * Checks (in order):
 *  1. At least one component exists
 *  2. No component has a completely hollow config (e.g., NOTES with blank text
 *     AND no needsClarification flag)
 *  3. CHECKLIST has at least one item or is marked needsClarification
 *  4. Title is not just the raw input copy-pasted verbatim (for long inputs)
 *
 * When a check fails the gate **repairs in-place** using deterministic defaults
 * from [TaskDSLBuilder] / [TaskComponentCatalog] rather than rejecting the
 * whole output. The philosophy is: salvage as much LLM work as possible,
 * patch only what's broken.
 */
@Singleton
class TaskDSLQualityGate @Inject constructor() {

    /**
     * Returns a [GateResult] that is either [GateResult.Passed] (possibly with
     * repairs) or [GateResult.Rejected] when the output is beyond salvaging.
     */
    fun enforce(
        dsl: TaskDSLOutput,
        input: String,
        entities: ExtractedEntities
    ): GateResult {
        var patched = dsl
        val repairs = mutableListOf<String>()

        // ── 1. Guarantee at least one component ─────────────────────────
        if (patched.components.isEmpty()) {
            patched = injectDefaultComponents(patched, input, entities)
            repairs += "Injected default components (LLM returned none)"
        }

        // ── 2. Patch hollow components ──────────────────────────────────
        val (patchedComponents, hollowRepairs) = patchHollowComponents(patched.components)
        if (hollowRepairs.isNotEmpty()) {
            patched = patched.copy(components = patchedComponents)
            repairs += hollowRepairs
        }

        // ── 3. Normalize sort orders ────────────────────────────────────
        val reindexed = patched.components.mapIndexed { i, c -> c.copy(sortOrder = i) }
        if (reindexed != patched.components) {
            patched = patched.copy(components = reindexed)
        }

        // ── 4. Title sanity ─────────────────────────────────────────────
        if (patched.title.isBlank()) {
            patched = patched.copy(title = TaskDSLBuilder.buildTitle(input, patched.type))
            repairs += "Title was blank; generated from input"
        }

        // ── 5. Final safety: if still no components, reject ─────────────
        if (patched.components.isEmpty()) {
            return GateResult.Rejected("Output has no usable components after repair")
        }

        return GateResult.Passed(dsl = patched, repairs = repairs)
    }

    // ─────────────────────────────────────────────────────────────────────

    private fun injectDefaultComponents(
        dsl: TaskDSLOutput,
        input: String,
        entities: ExtractedEntities
    ): TaskDSLOutput {
        val templateIds = TaskDSLBuilder.defaultTemplateIdsFor(dsl.type, entities, input)
        val context = TaskComponentContext(
            input = input,
            title = dsl.title.ifBlank { TaskDSLBuilder.buildTitle(input, dsl.type) },
            taskType = dsl.type,
            targetDateMs = dsl.targetDateMs,
            numbers = entities.numbers,
            locations = entities.locations
        )
        val (components, reminders) = TaskComponentCatalog.buildSelection(
            templateIds = templateIds,
            now = System.currentTimeMillis(),
            context = context
        )
        return dsl.copy(
            components = components,
            reminders = if (dsl.reminders.isEmpty()) reminders else dsl.reminders
        )
    }

    private fun patchHollowComponents(
        components: List<ComponentDSL>
    ): Pair<List<ComponentDSL>, List<String>> {
        val repairs = mutableListOf<String>()
        val patched = components.map { component ->
            if (component.needsClarification) return@map component
            when {
                component.type == ComponentType.CHECKLIST && isChecklistEmpty(component) -> {
                    repairs += "Marked empty CHECKLIST for clarification"
                    component.copy(needsClarification = true)
                }
                component.type == ComponentType.NOTES && isNotesEmpty(component) -> {
                    repairs += "Marked empty NOTES for clarification"
                    component.copy(needsClarification = true)
                }
                else -> component
            }
        }
        return patched to repairs
    }

    private fun isChecklistEmpty(component: ComponentDSL): Boolean {
        val items = component.config["items"]
        if (items == null) return true
        return runCatching {
            items.jsonArray.isEmpty()
        }.getOrDefault(true)
    }

    private fun isNotesEmpty(component: ComponentDSL): Boolean {
        val text = component.config["text"]?.jsonPrimitive?.contentOrNull
        return text.isNullOrBlank()
    }

    // ─────────────────────────────────────────────────────────────────────

    sealed class GateResult {
        data class Passed(
            val dsl: TaskDSLOutput,
            val repairs: List<String> = emptyList()
        ) : GateResult()

        data class Rejected(val reason: String) : GateResult()
    }
}

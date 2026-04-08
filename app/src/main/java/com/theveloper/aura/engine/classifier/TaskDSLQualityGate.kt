package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.engine.dsl.ChecklistDslItems
import com.theveloper.aura.engine.dsl.ChecklistItemDSL
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskComponentCatalog
import com.theveloper.aura.engine.dsl.TaskComponentContext
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Post-LLM quality enforcement layer.
 *
 * This gate is intentionally conservative: it repairs structural issues using
 * information already present in the prompt or in the draft, but it avoids
 * domain-specific content generation. Rich content should come from the model
 * itself (or a rescue pass), not from hardcoded app logic.
 */
@Singleton
class TaskDSLQualityGate @Inject constructor() {

    fun enforce(
        dsl: TaskDSLOutput,
        input: String,
        entities: ExtractedEntities
    ): GateResult {
        var patched = dsl
        val repairs = mutableListOf<String>()

        if (patched.components.isEmpty()) {
            patched = injectDefaultComponents(patched, input, entities)
            repairs += "Injected default components (draft had none)"
        }

        val explicitItems = ChecklistInputExtraction.extract(input)

        val (coveredDsl, coverageRepairs) = ensureChecklistCoverage(
            dsl = patched,
            explicitItems = explicitItems
        )
        if (coverageRepairs.isNotEmpty()) {
            patched = coveredDsl
            repairs += coverageRepairs
        }

        val (patchedComponents, hollowRepairs) = patchHollowComponents(
            components = patched.components,
            explicitItems = explicitItems,
            taskTitle = patched.title.ifBlank { input }
        )
        if (hollowRepairs.isNotEmpty()) {
            patched = patched.copy(components = patchedComponents)
            repairs += hollowRepairs
        }

        val (curatedComponents, curationRepairs) = pruneLowValueComponents(patched.components)
        if (curationRepairs.isNotEmpty()) {
            patched = patched.copy(components = curatedComponents)
            repairs += curationRepairs
        }

        val reindexed = patched.components.mapIndexed { index, component ->
            component.copy(sortOrder = index)
        }
        if (reindexed != patched.components) {
            patched = patched.copy(components = reindexed)
        }

        if (patched.title.isBlank()) {
            patched = patched.copy(title = TaskDSLBuilder.buildTitle(input, patched.type))
            repairs += "Title was blank; generated from input"
        }

        if (patched.components.isEmpty() || patched.components.none(::isUsableComponent)) {
            patched = synthesizeClarificationScaffold(patched, input)
            repairs += "Inserted checklist clarification scaffold because the draft was hollow"
        }

        return GateResult.Passed(dsl = patched, repairs = repairs)
    }

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

    private fun ensureChecklistCoverage(
        dsl: TaskDSLOutput,
        explicitItems: List<String>
    ): Pair<TaskDSLOutput, List<String>> {
        if (explicitItems.isEmpty() || dsl.components.any { it.type == ComponentType.CHECKLIST }) {
            return dsl to emptyList()
        }

        val label = dsl.title.ifBlank { explicitItems.first().take(24) }
        val checklist = ComponentDSL(
            skillId = "checklist",
            type = ComponentType.CHECKLIST,
            sortOrder = dsl.components.size,
            config = ChecklistDslItems.withItems(
                config = JsonObject(
                    mapOf(
                        "config_type" to JsonPrimitive(ComponentType.CHECKLIST.name),
                        "label" to JsonPrimitive(label),
                        "allowAddItems" to JsonPrimitive(true)
                    )
                ),
                items = explicitItems.map(::ChecklistItemDSL)
            ),
            populatedFromInput = true
        )

        return dsl.copy(components = dsl.components + checklist) to
            listOf("Added checklist from explicit items already present in input")
    }

    private fun patchHollowComponents(
        components: List<ComponentDSL>,
        explicitItems: List<String>,
        taskTitle: String
    ): Pair<List<ComponentDSL>, List<String>> {
        val repairs = mutableListOf<String>()

        val patched = components.map { component ->
            when (component.type) {
                ComponentType.CHECKLIST -> {
                    val patch = patchChecklist(component, explicitItems, taskTitle)
                    if (patch != null) {
                        repairs += patch.second
                        patch.first
                    } else {
                        component
                    }
                }

                else -> component
            }
        }

        return patched to repairs.distinct()
    }

    private fun patchChecklist(
        component: ComponentDSL,
        explicitItems: List<String>,
        taskTitle: String
    ): Pair<ComponentDSL, String>? {
        val currentItems = ChecklistDslItems.parse(component.config)
        val sanitizedCurrentItems = ChecklistContentQuality.sanitizeDslItems(currentItems, taskTitle)

        if (sanitizedCurrentItems.isNotEmpty()) {
            return if (sanitizedCurrentItems == currentItems) {
                null
            } else {
                component.copy(
                    config = ChecklistDslItems.withItems(component.config, sanitizedCurrentItems),
                    needsClarification = false
                ) to "Sanitized noisy CHECKLIST items"
            }
        }

        return if (explicitItems.isNotEmpty()) {
            component.copy(
                config = ChecklistDslItems.withItems(
                    config = component.config,
                    items = explicitItems.map(::ChecklistItemDSL)
                ),
                populatedFromInput = true,
                needsClarification = false
            ) to "Filled CHECKLIST from explicit items already present in input"
        } else {
            component.copy(
                config = ChecklistDslItems.withItems(component.config, emptyList()),
                needsClarification = true
            ) to "Marked empty CHECKLIST for clarification"
        }
    }

    private fun pruneLowValueComponents(
        components: List<ComponentDSL>
    ): Pair<List<ComponentDSL>, List<String>> {
        val hasConcreteNonNotes = components.any { component ->
            component.type != ComponentType.NOTES && isStrongNonNotesComponent(component)
        }
        val hasHighValueNotes = components.any { component ->
            component.type == ComponentType.NOTES && !isNotesLowValue(component)
        }
        if (!hasConcreteNonNotes && !hasHighValueNotes) return components to emptyList()

        val repairs = mutableListOf<String>()
        val filtered = components.filterNot { component ->
            when {
                component.type == ComponentType.NOTES && hasConcreteNonNotes && isNotesLowValue(component) -> {
                    repairs += "Removed low-value NOTES because stronger UI content was available"
                    true
                }

                component.type == ComponentType.CHECKLIST && hasHighValueNotes && isChecklistLowValue(component) -> {
                    repairs += "Removed low-value CHECKLIST because stronger notes content was available"
                    true
                }

                else -> false
            }
        }

        return if (repairs.isEmpty()) {
            components to emptyList()
        } else {
            filtered to repairs.distinct()
        }
    }

    private fun isUsableComponent(component: ComponentDSL): Boolean {
        return when (component.type) {
            ComponentType.CHECKLIST -> {
                ChecklistDslItems.parse(component.config).isNotEmpty() || component.needsClarification
            }

            ComponentType.NOTES -> !isNotesLowValue(component)

            ComponentType.COUNTDOWN -> {
                val targetDate = component.config["targetDate"]?.jsonPrimitive?.longOrNull ?: 0L
                targetDate > 0L || component.needsClarification
            }

            else -> true
        }
    }

    private fun isNotesEmpty(component: ComponentDSL): Boolean {
        val text = component.config["text"]?.jsonPrimitive?.contentOrNull
        return DraftContentQuality.sanitizeGeneratedText(text.orEmpty()).isBlank()
    }

    private fun isNotesLowValue(component: ComponentDSL): Boolean {
        val text = component.config["text"]?.jsonPrimitive?.contentOrNull
        return text.isNullOrBlank() || DraftContentQuality.isThinNotes(text)
    }

    private fun isChecklistLowValue(component: ComponentDSL): Boolean {
        val rawItems = ChecklistDslItems.parse(component.config)
        if (rawItems.isEmpty()) return true
        val sanitizedItems = ChecklistContentQuality.sanitizeDslItems(
            items = rawItems,
            taskTitle = ""
        )
        return sanitizedItems.isEmpty()
    }

    private fun isStrongNonNotesComponent(component: ComponentDSL): Boolean {
        return when (component.type) {
            ComponentType.CHECKLIST -> !isChecklistLowValue(component)
            ComponentType.NOTES -> false
            else -> isUsableComponent(component)
        }
    }

    private fun synthesizeClarificationScaffold(
        dsl: TaskDSLOutput,
        input: String
    ): TaskDSLOutput {
        val title = dsl.title.ifBlank { TaskDSLBuilder.buildTitle(input, dsl.type) }
        val scaffold = ComponentDSL(
            skillId = "checklist",
            type = ComponentType.CHECKLIST,
            sortOrder = 0,
            config = JsonObject(
                mapOf(
                    "config_type" to JsonPrimitive(ComponentType.CHECKLIST.name),
                    "label" to JsonPrimitive(title),
                    "allowAddItems" to JsonPrimitive(true)
                )
            ),
            needsClarification = true
        )
        return dsl.copy(components = listOf(scaffold))
    }

    sealed class GateResult {
        data class Passed(
            val dsl: TaskDSLOutput,
            val repairs: List<String> = emptyList()
        ) : GateResult()

        data class Rejected(val reason: String) : GateResult()
    }
}

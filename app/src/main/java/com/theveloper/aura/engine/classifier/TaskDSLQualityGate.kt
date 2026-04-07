package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.ChecklistDslItems
import com.theveloper.aura.engine.dsl.ChecklistItemDSL
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.TaskComponentCatalog
import com.theveloper.aura.engine.dsl.TaskComponentContext
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        val (patchedComponents, hollowRepairs) = patchHollowComponents(
            dsl = patched,
            input = input
        )
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
        dsl: TaskDSLOutput,
        input: String
    ): Pair<List<ComponentDSL>, List<String>> {
        val repairs = mutableListOf<String>()
        val patched = dsl.components.map { component ->
            if (component.needsClarification) return@map component
            when {
                component.type == ComponentType.CHECKLIST &&
                    (isChecklistEmpty(component) || shouldUpgradeLearningChecklist(component, dsl.type, input)) -> {
                    synthesizeChecklistContent(
                        component = component,
                        taskType = dsl.type,
                        input = input
                    )?.also {
                        repairs += if (isChecklistEmpty(component)) {
                            "Generated structured checklist for hollow CHECKLIST"
                        } else {
                            "Upgraded generic CHECKLIST into roadmap milestones"
                        }
                    } ?: run {
                        repairs += "Marked empty CHECKLIST for clarification"
                        component.copy(needsClarification = true)
                    }
                }
                component.type == ComponentType.NOTES &&
                    (isNotesEmpty(component) || shouldUpgradeLearningNotes(component, dsl.type, input)) -> {
                    synthesizeNotesContent(
                        component = component,
                        taskType = dsl.type,
                        input = input
                    )?.also {
                        repairs += if (isNotesEmpty(component)) {
                            "Generated roadmap content for hollow NOTES"
                        } else {
                            "Upgraded thin NOTES into structured roadmap content"
                        }
                    } ?: run {
                        repairs += "Marked empty NOTES for clarification"
                        component.copy(needsClarification = true)
                    }
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

    private fun shouldUpgradeLearningNotes(
        component: ComponentDSL,
        taskType: TaskType,
        input: String
    ): Boolean {
        if (!looksLikeLearningRoadmapRequest(input, taskType)) return false
        val text = component.config["text"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (text.isBlank()) return false
        return looksLikeThinKeywordDump(text)
    }

    private fun shouldUpgradeLearningChecklist(
        component: ComponentDSL,
        taskType: TaskType,
        input: String
    ): Boolean {
        if (!looksLikeLearningRoadmapRequest(input, taskType)) return false
        val labels = ChecklistDslItems.parse(component.config).map { it.label.lowercase() }
        if (labels.isEmpty()) return false
        val genericCount = labels.count { label ->
            GENERIC_ROADMAP_TERMS.any { term -> label.contains(term) } ||
                COMMON_TECH_TOPICS.any { topic -> label.contains(topic) }
        }
        return genericCount == labels.size
    }

    private fun synthesizeNotesContent(
        component: ComponentDSL,
        taskType: TaskType,
        input: String
    ): ComponentDSL? {
        if (!looksLikeLearningRoadmapRequest(input, taskType)) return null
        val spanish = isLikelySpanish(input)
        val topic = inferLearningTopic(input)
        val text = buildLearningRoadmapNotes(topic, spanish)
        val updatedConfig = JsonObject(
            component.config + mapOf(
                "text" to JsonPrimitive(text),
                "isMarkdown" to JsonPrimitive(true)
            )
        )
        return component.copy(
            config = updatedConfig,
            populatedFromInput = true,
            needsClarification = false
        )
    }

    private fun synthesizeChecklistContent(
        component: ComponentDSL,
        taskType: TaskType,
        input: String
    ): ComponentDSL? {
        if (!looksLikeLearningRoadmapRequest(input, taskType)) return null
        val spanish = isLikelySpanish(input)
        val items = buildLearningRoadmapChecklist(spanish)
        val label = if (spanish) "Hitos del roadmap" else "Roadmap milestones"
        val updatedConfig = ChecklistDslItems.withItems(
            config = JsonObject(
                component.config + mapOf(
                    "label" to JsonPrimitive(label),
                    "allowAddItems" to JsonPrimitive(true)
                )
            ),
            items = items
        )
        return component.copy(
            config = updatedConfig,
            populatedFromInput = true,
            needsClarification = false
        )
    }

    private fun buildLearningRoadmapChecklist(spanish: Boolean): List<ChecklistItemDSL> {
        return if (spanish) {
            listOf(
                ChecklistItemDSL("Fundamentos de programación"),
                ChecklistItemDSL("Sintaxis básica de Kotlin"),
                ChecklistItemDSL("Nullability y colecciones"),
                ChecklistItemDSL("POO, data classes y sealed classes"),
                ChecklistItemDSL("Lambdas, extensiones y funciones de orden superior"),
                ChecklistItemDSL("Coroutines y Flow"),
                ChecklistItemDSL("Proyecto de consola"),
                ChecklistItemDSL("Proyecto con API o Android")
            )
        } else {
            listOf(
                ChecklistItemDSL("Programming fundamentals"),
                ChecklistItemDSL("Basic Kotlin syntax"),
                ChecklistItemDSL("Nullability and collections"),
                ChecklistItemDSL("OOP, data classes, and sealed classes"),
                ChecklistItemDSL("Lambdas, extensions, and higher-order functions"),
                ChecklistItemDSL("Coroutines and Flow"),
                ChecklistItemDSL("Console project"),
                ChecklistItemDSL("API or Android project")
            )
        }
    }

    private fun buildLearningRoadmapNotes(topic: String, spanish: Boolean): String {
        return if (spanish) {
            """
            ## Objetivo
            Aprender $topic con una base sólida y avanzar hasta construir proyectos reales.

            ## Roadmap sugerido
            ### Fase 1: Fundamentos
            - Variables, tipos, operadores y control de flujo.
            - Funciones, parámetros y organización básica del código.
            - Nullability, colecciones y manejo de errores simples.

            ### Fase 2: Kotlin idiomático
            - `data class`, `object`, `sealed class` y extensiones.
            - Lambdas, funciones de orden superior y colecciones funcionales.
            - Estructura de paquetes, módulos y buenas prácticas de legibilidad.

            ### Fase 3: Programación práctica
            - Testing básico, debugging y lectura de stack traces.
            - Consumo de APIs, serialización y manejo de estado.
            - Coroutines y Flow para tareas asíncronas.

            ### Fase 4: Proyectos
            - Mini proyecto de consola para afianzar sintaxis.
            - Proyecto que consuma una API.
            - Proyecto Android con Kotlin y Compose si querés ir a mobile.

            ## Cómo estudiar
            - Alterná bloques cortos de teoría con práctica diaria.
            - Cerrá cada fase con un mini proyecto.
            - Tomá notas de conceptos, errores comunes y decisiones importantes.

            ## Referencias útiles
            - Documentación oficial de Kotlin: https://kotlinlang.org/docs/home.html
            - Kotlin Koans: https://play.kotlinlang.org/koans/overview
            - Android + Kotlin: https://developer.android.com/kotlin
            - Coroutines overview: https://kotlinlang.org/docs/coroutines-overview.html

            ## Siguiente paso
            Empezá por sintaxis básica, funciones y nullability, y cerrá esa etapa con un proyecto pequeño que puedas terminar.
            """.trimIndent()
        } else {
            """
            ## Goal
            Learn $topic with a solid foundation and progress toward building real projects.

            ## Suggested roadmap
            ### Phase 1: Fundamentals
            - Variables, types, operators, and control flow.
            - Functions, parameters, and basic code organization.
            - Nullability, collections, and simple error handling.

            ### Phase 2: Idiomatic Kotlin
            - `data class`, `object`, `sealed class`, and extensions.
            - Lambdas, higher-order functions, and functional collection APIs.
            - Packages, modules, and readable code structure.

            ### Phase 3: Practical programming
            - Basic testing, debugging, and reading stack traces.
            - API consumption, serialization, and state handling.
            - Coroutines and Flow for asynchronous work.

            ### Phase 4: Projects
            - A small console project to reinforce syntax.
            - A project that consumes an API.
            - An Android project with Kotlin and Compose if you want the mobile path.

            ## Study approach
            - Alternate short theory blocks with daily practice.
            - Finish each phase with a small project.
            - Keep notes about concepts, common mistakes, and important decisions.

            ## Useful references
            - Official Kotlin docs: https://kotlinlang.org/docs/home.html
            - Kotlin Koans: https://play.kotlinlang.org/koans/overview
            - Android + Kotlin: https://developer.android.com/kotlin
            - Coroutines overview: https://kotlinlang.org/docs/coroutines-overview.html

            ## Next step
            Start with syntax, functions, and nullability, then close that phase with a small project you can finish quickly.
            """.trimIndent()
        }
    }

    private fun looksLikeLearningRoadmapRequest(input: String, taskType: TaskType): Boolean {
        if (taskType !in setOf(TaskType.GOAL, TaskType.GENERAL, TaskType.PROJECT)) return false
        val normalized = input.lowercase()
        val hasLearningSignal = LEARNING_SIGNAL.containsMatchIn(normalized)
        val hasRoadmapSignal = ROADMAP_SIGNAL.containsMatchIn(normalized)
        val hasReferenceSignal = REFERENCE_SIGNAL.containsMatchIn(normalized)
        return hasRoadmapSignal || (hasLearningSignal && hasReferenceSignal)
    }

    private fun looksLikeThinKeywordDump(text: String): Boolean {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty() || lines.size > 8) return false
        if (lines.any { it.startsWith("##") }) return false
        return lines.all { line ->
            val cleaned = line.removePrefix("-").trim()
            cleaned.split(Regex("\\s+")).size <= 4
        }
    }

    private fun inferLearningTopic(input: String): String {
        return COMMON_TECH_TOPICS.firstOrNull { topic ->
            input.contains(topic, ignoreCase = true)
        }?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            ?: "este tema"
    }

    private fun isLikelySpanish(input: String): Boolean {
        return input.any { it in "áéíóúñ¿¡" } || SPANISH_SIGNAL.containsMatchIn(input.lowercase())
    }

    // ─────────────────────────────────────────────────────────────────────

    sealed class GateResult {
        data class Passed(
            val dsl: TaskDSLOutput,
            val repairs: List<String> = emptyList()
        ) : GateResult()

        data class Rejected(val reason: String) : GateResult()
    }

    private companion object {
        val LEARNING_SIGNAL = Regex(
            """\b(learn|learning|study|studying|aprender|aprendizaje|estudiar|programar|programación|coding|code)\b""",
            RegexOption.IGNORE_CASE
        )
        val ROADMAP_SIGNAL = Regex(
            """\b(roadmap|guide|guides|guia|guía|plan de estudio|ruta)\b""",
            RegexOption.IGNORE_CASE
        )
        val REFERENCE_SIGNAL = Regex(
            """\b(reference|references|resource|resources|documentation|docs|links?|sources?|referencias?|recursos|documentación|documentacion)\b""",
            RegexOption.IGNORE_CASE
        )
        val SPANISH_SIGNAL = Regex(
            """\b(quiero|necesito|aprender|guia|guía|tambien|también|referencias|programar|programación)\b""",
            RegexOption.IGNORE_CASE
        )
        val COMMON_TECH_TOPICS = listOf(
            "kotlin",
            "python",
            "javascript",
            "typescript",
            "java",
            "swift",
            "rust",
            "go",
            "c#",
            "c++"
        )
        val GENERIC_ROADMAP_TERMS = setOf(
            "roadmap",
            "guide",
            "guides",
            "reference",
            "references",
            "resource",
            "resources",
            "documentation",
            "docs",
            "link",
            "links",
            "source",
            "sources",
            "guia",
            "guía",
            "referencias",
            "documentación",
            "documentacion"
        )
    }
}

package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.ReminderDSL
import com.theveloper.aura.engine.dsl.TaskComponentCatalog
import com.theveloper.aura.engine.dsl.TaskComponentContext
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class OnDeviceTaskDslRequest(
    val input: String,
    val intentResult: IntentResult,
    val extractedEntities: ExtractedEntities,
    val llmContext: LLMClassificationContext
)

data class OnDeviceTaskDslResult(
    val dsl: TaskDSLOutput,
    val confidence: Float,
    val source: TaskGenerationSource
)

interface OnDeviceTaskDslService {
    suspend fun compose(request: OnDeviceTaskDslRequest): OnDeviceTaskDslResult
}

@Singleton
class HeuristicOnDeviceTaskDslService @Inject constructor() : OnDeviceTaskDslService {

    override suspend fun compose(request: OnDeviceTaskDslRequest): OnDeviceTaskDslResult {
        val input = request.input.trim()
        val signals = PromptSignals.from(input)
        val taskType = resolveTaskType(request.intentResult, signals)
        val title = TaskDSLBuilder.buildTitle(input, taskType)
        val targetDateMs = request.extractedEntities.dateTimes.firstOrNull()
        val context = TaskComponentContext(
            input = input,
            title = title,
            taskType = taskType,
            targetDateMs = targetDateMs,
            numbers = request.extractedEntities.numbers,
            locations = request.extractedEntities.locations
        )

        val templateIds = linkedSetOf<String>().apply {
            addAll(TaskDSLBuilder.defaultTemplateIdsFor(taskType, input))
            addAll(additionalTemplatesFor(taskType, signals))
            if (isEmpty()) {
                add("notes_brain_dump")
            }
        }.toList()

        val now = System.currentTimeMillis()
        val (components, reminders) = TaskComponentCatalog.buildSelection(
            templateIds = templateIds,
            now = now,
            context = context
        )

        val priority = inferPriority(input, targetDateMs, signals)
        val confidence = inferConfidence(request.intentResult.confidence, signals, templateIds.size)
        val source = if (signals.enrichesUi || templateIds.size > TaskDSLBuilder.defaultTemplateIdsFor(taskType, input).size) {
            TaskGenerationSource.LOCAL_AI
        } else {
            TaskGenerationSource.RULES
        }

        return OnDeviceTaskDslResult(
            dsl = TaskDSLOutput(
                title = title,
                type = taskType,
                priority = priority,
                targetDateMs = targetDateMs,
                components = components,
                reminders = normalizeReminders(reminders)
            ),
            confidence = confidence,
            source = source
        )
    }

    private fun resolveTaskType(intentResult: IntentResult, signals: PromptSignals): TaskType {
        val scores = mutableMapOf<TaskType, Float>().withDefault { 0f }
        TaskType.entries.forEach { taskType ->
            scores[taskType] = if (taskType == intentResult.taskType) intentResult.confidence else 0.05f
        }

        fun bump(taskType: TaskType, delta: Float) {
            scores[taskType] = scores.getValue(taskType) + delta
        }

        if (signals.travelRelated) bump(TaskType.TRAVEL, 0.35f)
        if (signals.financeRelated) bump(TaskType.FINANCE, 0.35f)
        if (signals.healthRelated) bump(TaskType.HEALTH, 0.32f)
        if (signals.habitRelated) bump(TaskType.HABIT, 0.28f)
        if (signals.projectRelated) bump(TaskType.PROJECT, 0.28f)
        if (signals.eventOrDeadline) bump(TaskType.GENERAL, 0.08f)

        return scores.maxByOrNull { it.value }?.key ?: intentResult.taskType
    }

    private fun additionalTemplatesFor(taskType: TaskType, signals: PromptSignals): List<String> {
        return buildList {
            when (taskType) {
                TaskType.TRAVEL -> {
                    if (signals.documents) add("travel_documents_checklist")
                    if (signals.itinerary || signals.bookingRelated) add("travel_itinerary_notes")
                    if (signals.weatherRelated) add("feed_weather")
                    if (signals.financeRelated || signals.budgetRelated) {
                        add("progress_budget")
                        add("feed_exchange")
                    }
                    if (signals.checklistHeavy) add("action_checklist")
                }
                TaskType.HABIT -> {
                    if (signals.hydrationRelated) add("metric_hydration")
                    if (signals.activityRelated) add("metric_steps")
                    if (signals.sleepRelated) add("metric_sleep")
                    if (signals.journalRelated) add("journal_reflection")
                    if (signals.deadlineRelated) add("deadline_countdown")
                }
                TaskType.HEALTH -> {
                    if (signals.medicationRelated) add("medication_checklist")
                    if (signals.hydrationRelated) add("metric_hydration")
                    if (signals.activityRelated) add("metric_steps")
                    if (signals.sleepRelated) add("metric_sleep")
                    if (signals.trackingRelated && !signals.hydrationRelated && !signals.activityRelated && !signals.sleepRelated) {
                        add("metric_weight")
                    }
                    if (signals.deadlineRelated || signals.medicationRelated) add("deadline_countdown")
                    if (signals.journalRelated || signals.medicationRelated) add("notes_clinic")
                }
                TaskType.PROJECT -> {
                    if (signals.meetingRelated) add("notes_meeting")
                    if (signals.researchRelated || signals.studyRelated) add("study_plan_notes")
                    if (signals.deadlineRelated) add("deadline_countdown")
                    if (signals.budgetRelated) add("progress_budget")
                    if (signals.trackingRelated || signals.launchRelated) add("progress_sprint")
                    if (signals.checklistHeavy) add("action_checklist")
                    if (!signals.meetingRelated && !signals.researchRelated) add("notes_brain_dump")
                }
                TaskType.FINANCE -> {
                    if (signals.paymentRelated) add("payment_countdown")
                    if (signals.paymentRelated) add("finance_payment_checklist")
                    if (signals.budgetRelated || signals.savingsRelated) add("progress_budget")
                    if (signals.financeRelated || signals.travelRelated) add("feed_exchange")
                    if (signals.budgetRelated || signals.paymentRelated) add("budget_snapshot_notes")
                }
                TaskType.GENERAL -> {
                    if (signals.deadlineRelated) add("deadline_countdown")
                    if (signals.checklistHeavy) add("action_checklist")
                    if (signals.meetingRelated) add("notes_meeting")
                    if (signals.studyRelated || signals.researchRelated) add("study_plan_notes")
                    if (signals.journalRelated) add("journal_reflection")
                    if (signals.paymentRelated) add("finance_payment_checklist")
                    if (signals.financeRelated) add("progress_budget")
                }
            }
        }
    }

    private fun inferPriority(input: String, targetDateMs: Long?, signals: PromptSignals): Int {
        val normalized = input.lowercase()
        return when {
            signals.urgent || Regex("\\bhoy\\b|\\bmanana\\b|\\bmañana\\b|asap|urgent", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> 3
            targetDateMs != null || signals.deadlineRelated || signals.paymentRelated -> 2
            signals.projectRelated || signals.financeRelated || signals.healthRelated -> 1
            else -> 0
        }
    }

    private fun inferConfidence(intentConfidence: Float, signals: PromptSignals, templateCount: Int): Float {
        val signalBoost = (signals.signalCount * 0.05f).coerceAtMost(0.25f)
        val templateBoost = max(0, templateCount - 1) * 0.03f
        return (intentConfidence.coerceAtLeast(0.38f) + signalBoost + templateBoost).coerceAtMost(0.96f)
    }

    private fun normalizeReminders(reminders: List<ReminderDSL>): List<ReminderDSL> {
        return reminders
            .sortedBy { it.scheduledAtMs }
            .distinctBy { it.scheduledAtMs to it.intervalDays }
    }
}

private data class PromptSignals(
    val travelRelated: Boolean,
    val financeRelated: Boolean,
    val budgetRelated: Boolean,
    val paymentRelated: Boolean,
    val healthRelated: Boolean,
    val habitRelated: Boolean,
    val projectRelated: Boolean,
    val eventOrDeadline: Boolean,
    val itinerary: Boolean,
    val bookingRelated: Boolean,
    val documents: Boolean,
    val weatherRelated: Boolean,
    val meetingRelated: Boolean,
    val researchRelated: Boolean,
    val studyRelated: Boolean,
    val journalRelated: Boolean,
    val medicationRelated: Boolean,
    val hydrationRelated: Boolean,
    val activityRelated: Boolean,
    val sleepRelated: Boolean,
    val trackingRelated: Boolean,
    val checklistHeavy: Boolean,
    val launchRelated: Boolean,
    val deadlineRelated: Boolean,
    val savingsRelated: Boolean,
    val urgent: Boolean
) {
    val signalCount: Int = listOf(
        travelRelated,
        financeRelated,
        budgetRelated,
        paymentRelated,
        healthRelated,
        habitRelated,
        projectRelated,
        eventOrDeadline,
        itinerary,
        bookingRelated,
        documents,
        weatherRelated,
        meetingRelated,
        researchRelated,
        studyRelated,
        journalRelated,
        medicationRelated,
        hydrationRelated,
        activityRelated,
        sleepRelated,
        trackingRelated,
        checklistHeavy,
        launchRelated,
        deadlineRelated,
        savingsRelated,
        urgent
    ).count { it }

    val enrichesUi: Boolean = listOf(
        budgetRelated,
        paymentRelated,
        itinerary,
        documents,
        meetingRelated,
        researchRelated,
        studyRelated,
        journalRelated,
        medicationRelated,
        hydrationRelated,
        activityRelated,
        sleepRelated,
        trackingRelated,
        checklistHeavy,
        launchRelated
    ).any { it }

    companion object {
        fun from(input: String): PromptSignals {
            fun has(pattern: String): Boolean {
                return Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(input)
            }

            return PromptSignals(
                travelRelated = has("\\bviaj\\w*|vuelo\\w*|hotel\\w*|maleta\\b|aeropuerto\\b|itinerario\\b|pasaporte\\b|reserva\\w*"),
                financeRelated = has("\\bpagar\\b|presupuesto\\b|gasto\\w*|dinero\\b|ahorro\\w*|deuda\\b|dolar\\b|usd\\b|eur\\b|ars\\b|alquiler\\b|factura\\b"),
                budgetRelated = has("\\bpresupuesto\\b|ahorro\\w*|gasto\\w*|costos?\\b|cashflow\\b|finanzas?\\b"),
                paymentRelated = has("\\bpagar\\b|pago\\b|factura\\b|vencim\\w*|cuota\\b|alquiler\\b|transferencia\\b"),
                healthRelated = has("\\bsalud\\b|medic\\w*|tratamiento\\b|peso\\b|agua\\b|hidrata\\w*|sueno\\b|sueño\\b|pasos\\b|gym\\b|gimnasio\\b|entren\\w*"),
                habitRelated = has("\\bhabito\\b|h[aá]bito\\b|rutina\\b|diario\\b|diariamente\\b|cada\\s+\\d+\\s+(hora|horas|dia|dias|d[ií]as)\\b"),
                projectRelated = has("\\bproyecto\\b|roadmap\\b|entrega\\b|lanzamiento\\b|reunion\\b|reuni[oó]n\\b|investigar\\b|dise[ñn]ar\\b|implementar\\b|estudiar\\b|curso\\b"),
                eventOrDeadline = has("\\bhoy\\b|\\bmanana\\b|\\bmañana\\b|\\bviernes\\b|\\blunes\\b|\\bmartes\\b|\\bmiercoles\\b|\\bmiércoles\\b|\\bjueves\\b|\\bsabado\\b|\\bsábado\\b|\\bdomingo\\b|deadline\\b|fecha\\b"),
                itinerary = has("\\bitinerario\\b|agenda\\b|plan\\s+de\\s+viaje\\b|recorrido\\b"),
                bookingRelated = has("\\breserva\\w*|booking\\b|check-in\\b|hotel\\b|vuelo\\w*"),
                documents = has("\\bpasaporte\\b|visa\\b|seguro\\b|document\\w*|boarding\\b"),
                weatherRelated = has("\\bclima\\b|weather\\b|temperatura\\b|lluvia\\b"),
                meetingRelated = has("\\breunion\\b|reuni[oó]n\\b|kickoff\\b|retro\\b|one-on-one\\b|meeting\\b"),
                researchRelated = has("\\binvestigar\\b|research\\b|benchmark\\b|comparar\\b|analizar\\b"),
                studyRelated = has("\\bestudiar\\b|curso\\b|clase\\b|examen\\b|aprender\\b|repasar\\b"),
                journalRelated = has("\\bjournal\\b|bitacora\\b|bit[aá]cora\\b|reflexi[oó]n\\b|diario\\b"),
                medicationRelated = has("\\bmedic\\w*|pastilla\\b|dosis\\b|tratamiento\\b|terapia\\b"),
                hydrationRelated = has("tomar\\s+agua|hidrata\\w*|agua\\b"),
                activityRelated = has("\\bpasos\\b|caminar\\b|correr\\b|entren\\w*|gym\\b|gimnasio\\b|workout\\b"),
                sleepRelated = has("\\bsueno\\b|sue[ñn]o\\b|dormir\\b|descanso\\b"),
                trackingRelated = has("\\btrack\\b|seguimiento\\b|medir\\b|avance\\b|progreso\\b|monitor\\w*"),
                checklistHeavy = has("\\blista\\b|checklist\\b|pasos\\b|preparar\\b|organizar\\b"),
                launchRelated = has("\\blanzamiento\\b|release\\b|sprint\\b|roadmap\\b"),
                deadlineRelated = has("\\bdeadline\\b|vence\\b|entrega\\b|fecha\\b|hoy\\b|\\bmanana\\b|\\bmañana\\b"),
                savingsRelated = has("\\bahorro\\w*|save\\b|meta\\s+de\\s+ahorro\\b"),
                urgent = has("\\burgente\\b|asap\\b|ahora\\b|cuanto\\s+antes\\b")
            )
        }
    }
}

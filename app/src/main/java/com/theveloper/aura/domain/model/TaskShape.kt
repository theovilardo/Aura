package com.theveloper.aura.domain.model

enum class TaskShape(
    val taskType: TaskType,
    val displayName: String,
    val shortDescription: String,
    val coreComponents: Set<ComponentType>,
    val optionalComponents: Set<ComponentType>
) {
    TRAVEL(
        taskType = TaskType.TRAVEL,
        displayName = "Viaje",
        shortDescription = "Cuenta regresiva, preparación y presupuesto del viaje.",
        coreComponents = setOf(ComponentType.COUNTDOWN, ComponentType.CHECKLIST),
        optionalComponents = setOf(ComponentType.METRIC_TRACKER, ComponentType.NOTES)
    ),
    HABIT(
        taskType = TaskType.HABIT,
        displayName = "Hábito",
        shortDescription = "Seguimiento repetible con ritmo y registro diario.",
        coreComponents = setOf(ComponentType.HABIT_RING),
        optionalComponents = setOf(ComponentType.METRIC_TRACKER, ComponentType.NOTES)
    ),
    HEALTH(
        taskType = TaskType.HEALTH,
        displayName = "Salud",
        shortDescription = "Métricas, progreso y chequeos para objetivos de bienestar.",
        coreComponents = setOf(ComponentType.METRIC_TRACKER, ComponentType.PROGRESS_BAR),
        optionalComponents = setOf(ComponentType.HABIT_RING, ComponentType.COUNTDOWN, ComponentType.NOTES)
    ),
    PROJECT(
        taskType = TaskType.PROJECT,
        displayName = "Proyecto",
        shortDescription = "Pasos, progreso y contexto para trabajo estructurado.",
        coreComponents = setOf(ComponentType.CHECKLIST, ComponentType.PROGRESS_BAR),
        optionalComponents = setOf(ComponentType.COUNTDOWN, ComponentType.NOTES)
    ),
    FINANCE(
        taskType = TaskType.FINANCE,
        displayName = "Meta financiera",
        shortDescription = "Monto objetivo, avance y próximos vencimientos.",
        coreComponents = setOf(ComponentType.METRIC_TRACKER, ComponentType.PROGRESS_BAR),
        optionalComponents = setOf(ComponentType.COUNTDOWN, ComponentType.NOTES)
    ),
    EVENT(
        taskType = TaskType.EVENT,
        displayName = "Evento",
        shortDescription = "Fecha puntual con preparación y notas del momento.",
        coreComponents = setOf(ComponentType.COUNTDOWN),
        optionalComponents = setOf(ComponentType.CHECKLIST, ComponentType.NOTES)
    ),
    GOAL(
        taskType = TaskType.GOAL,
        displayName = "Meta personal",
        shortDescription = "Avance y milestones para objetivos de mediano plazo.",
        coreComponents = setOf(ComponentType.PROGRESS_BAR, ComponentType.CHECKLIST),
        optionalComponents = setOf(ComponentType.COUNTDOWN, ComponentType.METRIC_TRACKER, ComponentType.NOTES)
    ),
    NOTE(
        taskType = TaskType.GENERAL,
        displayName = "Nota libre",
        shortDescription = "Espacio flexible cuando solo necesitás contexto o ideas.",
        coreComponents = setOf(ComponentType.NOTES),
        optionalComponents = emptySet()
    );

    companion object {
        val userFacingOrder: List<TaskShape> = listOf(
            TRAVEL,
            HABIT,
            HEALTH,
            PROJECT,
            FINANCE,
            EVENT,
            GOAL,
            NOTE
        )

        fun from(taskType: TaskType): TaskShape {
            return userFacingOrder.firstOrNull { it.taskType == taskType } ?: NOTE
        }
    }
}

fun TaskType.toTaskShape(): TaskShape = TaskShape.from(this)

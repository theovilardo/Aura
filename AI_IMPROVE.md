# AURA — Intelligence Layer
## Clarification Loop + Contextual Memory
> Referencia de implementación — Marzo 2026

---

## El problema que resuelve esto

El sistema actual clasifica la intención del usuario y genera un Task DSL con los componentes correctos, pero los entrega **vacíos**. El usuario tiene que poblar cada componente manualmente uno por uno, lo cual reproduce exactamente la fricción que queríamos eliminar.

Hay dos brechas concretas:

1. **Contenido inicial vacío** — la IA elige CHECKLIST para una groceries list pero no sabe que el usuario necesita tomate y leche porque nunca se lo dijiste.
2. **Sin memoria de contexto** — cada tarea se crea desde cero, sin conocimiento acumulado del usuario. El sistema no sabe que los viernes a la noche ignora todo, o que "el profe" significa su profesor de gym.

La solución son dos sistemas que trabajan juntos: el **Clarification Loop** para el momento de creación, y el **Contextual Memory Layer** para el aprendizaje acumulado.

---

## 1. Clarification Loop

### Concepto central

El input de creación no es solo para clasificar la tarea. Es también para extraer el **contenido** de cada componente generado. La IA tiene que poblar los componentes con lo que el usuario dijo, inferir lo que puede del contexto, y preguntar solo cuando realmente no hay información suficiente.

### Los tres casos

```
CASO 1 — Input suficiente
"lista de compras: leche, pan, tomate, detergente"
    → CHECKLIST detectado
    → ítems extraídos directamente del input
    → NO preguntar, crear directo

CASO 2 — Contexto inferible
"preparar el asado del domingo"
    → CHECKLIST detectado
    → no hay ítems explícitos pero el contexto permite inferir
    → poblar con sugerencias plausibles (carne, carbón, chimichurri...)
    → marcar ítems como "sugeridos" visualmente
    → NO preguntar, usuario ajusta en preview

CASO 3 — Sin información ni contexto
"lista de compras"
    → CHECKLIST detectado, ítems = vacío, contexto = nulo
    → no se puede inferir nada
    → PREGUNTAR: "¿Qué necesitás comprar?"
```

### Ítems sugeridos vs confirmados

Cuando la IA infiere contenido por contexto (Caso 2), los ítems se marcan visualmente como sugeridos. El usuario los ve pre-cargados y simplemente borra los que no corresponden o agrega los que faltan. Es mucho menos fricción que escribir desde cero.

En la DB y en el DSL esto se representa con un campo `isSuggested: Boolean` en cada ítem. Una vez que el usuario confirma o edita el ítem, pasa a `isSuggested = false`.

### CompletenessValidator

Etapa nueva en el classifier pipeline, que corre **después** de generar el Task DSL y **antes** de mostrar el preview:

```kotlin
data class CompletenessCheck(
    val isComplete: Boolean,
    val missingFields: List<MissingField>
)

data class MissingField(
    val componentType: ComponentType,
    val fieldName: String,
    val question: String,   // pregunta concreta para el usuario
    val isBlocker: Boolean  // false = se puede crear igual sin este campo
)
```

Reglas por componente:

| Componente | Campo | ¿Blocker? | Pregunta |
|---|---|---|---|
| CHECKLIST | items (vacío y no inferible) | Sí | "¿Qué ítems querés incluir?" |
| COUNTDOWN | targetDate (null) | Sí | "¿Para cuándo es?" |
| METRIC_TRACKER | goal | No | "¿Tenés un objetivo? (podés dejarlo sin definir)" |
| HABIT_RING | targetCount | No | "¿Cuántas veces por día/semana?" |
| DATA_FEED | params incompletos | Sí | Específica según fetcher |

### Flujo completo

```
Input usuario
    ↓
Classifier genera Task DSL
    ↓
CompletenessValidator evalúa cada componente
    ↓
    ├── Todo completo o inferible
    │       → poblar componentes
    │       → TaskPreview → Guardar
    │
    ├── Hay blockers (campos sin los que el componente no tiene sentido)
    │       → ClarificationCard con UNA pregunta
    │       → usuario responde inline
    │       → Classifier re-procesa con contexto enriquecido
    │       → TaskPreview → Guardar
    │
    └── Solo non-blockers
            → crear tarea con campos marcados "sin definir"
            → usuario completa después si quiere
            → Guardar directo
```

**Regla crítica: máximo una ronda de clarification.** Si después de la respuesta todavía hay campos incompletos, se crea la tarea con lo que hay. Dos rondas de preguntas antes de crear es demasiada fricción.

### UI del Clarification Loop

Vive en el mismo espacio visual que el TaskPreview, no es una pantalla separada. Se siente como una conversación, no como un formulario.

```
┌─────────────────────────────────────────┐
│  Entendido, voy a crear tu lista        │
│  de compras.                            │
│                                         │
│  ¿Qué necesitás comprar?                │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │  tomate, leche, pan...          │    │
│  └─────────────────────────────────┘    │
│                            [Listo →]    │
│                                         │
│  [Crear sin ítems por ahora]            │
└─────────────────────────────────────────┘
```

Siempre debe haber una opción de saltear y crear vacío. A veces el usuario quiere el contenedor primero y llenarlo después.

### Cambios al Task DSL

```kotlin
data class ChecklistItemDSL(
    val label: String,
    val isSuggested: Boolean = false  // nuevo campo
)

data class ComponentDSL(
    val type: String,
    val config: Map<String, Any>,
    val populatedFromInput: Boolean = false,  // nuevo: la IA lo pobló
    val needsClarification: Boolean = false   // nuevo: esperando input
)
```

### Cambios al System Prompt

Agregar sección explícita de extracción de contenido:

```
EXTRACCIÓN DE CONTENIDO:
Para cada componente que generes, intentá poblar su contenido
desde el input del usuario o inferirlo del contexto.

- CHECKLIST: extraer ítems mencionados explícitamente.
  Si hay contexto suficiente (ej: "asado"), inferir ítems plausibles
  y marcarlos como isSuggested: true.
  Si no hay información ni contexto, dejar items: [] y marcar
  needsClarification: true.

- COUNTDOWN: extraer fecha si se menciona. Si no hay fecha y el
  componente es COUNTDOWN, marcar needsClarification: true.

- METRIC_TRACKER: extraer valor inicial y objetivo si se mencionan.
  Si no están, crear sin ellos (non-blocker).

- NOTES: capturar contexto relevante del input como nota inicial.
  Siempre poblar si hay algo útil que preservar.
```

---

## 2. Contextual Memory Layer

### Concepto central

Sistema de memoria estructurada con categorías fijas. No es un historial de conversación que crece indefinidamente: es un conjunto de **slots de memoria** que se escriben, actualizan y compactan con el tiempo. El LLM local sabe qué categorías existen y decide cuándo y qué escribir en cada una.

### Arquitectura

```
Eventos del día
(tareas creadas, señales de hábito,
 clarification loops, sugerencias respondidas)
         ↓
    Memory Writer  (LLM local — corre de madrugada)
    "¿Esto revela algo nuevo sobre el usuario?"
         ↓
    Structured Memory Store  (Room DB — tabla memory_slots)
    ├── ROUTINE              rutina diaria inferida
    ├── WORK_CONTEXT         trabajo, proyectos, deadlines recurrentes
    ├── PERSONAL_CONTEXT     familia, compromisos, intereses
    ├── TASK_PREFERENCES     cómo prefiere organizar sus tareas
    ├── REMINDER_BEHAVIOR    cuándo responde mejor a recordatorios
    └── VOCABULARY           términos propios del usuario
         ↓
    Memory Compactor  (corre cuando un slot supera su límite)
         ↓
    Context inyectado en cada classifier call
```

### Categorías de memoria

```kotlin
enum class MemoryCategory {
    ROUTINE,             // rutina del día a día
    WORK_CONTEXT,        // trabajo, proyectos, deadlines recurrentes
    PERSONAL_CONTEXT,    // familia, compromisos, intereses
    TASK_PREFERENCES,    // cómo prefiere organizar sus tareas
    REMINDER_BEHAVIOR,   // cuándo responde mejor a recordatorios
    VOCABULARY           // términos propios ("el profe", "la reunión de los jueves")
}
```

### Memory Slot

```kotlin
data class MemorySlot(
    val id: String,
    val category: MemoryCategory,
    val content: String,          // texto en lenguaje natural, tercera persona
    val lastUpdatedAt: Long,
    val version: Int,             // cuántas veces fue compactada
    val tokenCount: Int,
    val maxTokens: Int = 300      // límite por categoría
)
```

### DB — tabla nueva

```sql
CREATE TABLE memory_slots (
    id              TEXT PRIMARY KEY,
    category        TEXT NOT NULL UNIQUE,   -- una fila por categoría
    content         TEXT NOT NULL,
    last_updated_at INTEGER NOT NULL,
    version         INTEGER DEFAULT 0,
    token_count     INTEGER NOT NULL
);
```

### Memory Writer

Corre dentro del `HabitAnalysisWorker` existente, después del procesamiento de patrones. No requiere un Worker nuevo.

**Input al LLM:**

```
Tenés acceso a las categorías de memoria del usuario.
Tu trabajo es decidir si los eventos de hoy revelan algo nuevo
que valga la pena recordar en alguna categoría.

CATEGORÍAS ACTUALES:
ROUTINE: [contenido actual]
WORK_CONTEXT: [contenido actual]
PERSONAL_CONTEXT: [contenido actual]
TASK_PREFERENCES: [contenido actual]
REMINDER_BEHAVIOR: [contenido actual]
VOCABULARY: [contenido actual]

EVENTOS DE HOY:
[lista de eventos relevantes]

REGLAS:
- Solo actualizar si hay información genuinamente nueva
- Nunca guardar datos sensibles (nombres completos, números,
  direcciones, datos de salud específicos)
- Escribir en tercera persona ("el usuario prefiere...",
  "suele hacer X los lunes...")
- Si no hay nada relevante para ninguna categoría, responder skip: true
- Máximo actualizar 2 categorías por noche

Responder SOLO con JSON:
{
  "skip": false,
  "updates": [
    { "category": "ROUTINE", "content": "texto completo actualizado" }
  ]
}
```

### Memory Compactor

Se ejecuta automáticamente cuando un slot supera `maxTokens`. Puede correr en el mismo batch nocturno o al momento de intentar escribir en un slot lleno.

```kotlin
suspend fun compact(slot: MemorySlot): MemorySlot {
    val prompt = """
        Resumí esta memoria en máximo 250 tokens.
        Conservá los patrones más consistentes y recientes.
        Descartá información que se contradiga con lo más nuevo.
        Escribí en tercera persona, estilo telegráfico.
        Nunca inventar información que no estaba en el original.
        
        MEMORIA ACTUAL:
        ${slot.content}
    """.trimIndent()

    val compacted = llmService.complete(prompt)
    return slot.copy(
        content = compacted,
        version = slot.version + 1,
        tokenCount = countTokens(compacted)
    )
}
```

### Inyección de contexto en el Classifier

El contexto de memoria se inyecta en cada llamada al classifier. No se inyectan todas las categorías siempre: se seleccionan las relevantes según el tipo de tarea detectado.

```kotlin
fun buildContextForClassifier(
    input: String,
    detectedType: TaskType?,
    memorySlots: List<MemorySlot>
): String {
    val relevant = when (detectedType) {
        TaskType.HABIT, TaskType.HEALTH ->
            listOf(ROUTINE, REMINDER_BEHAVIOR, PERSONAL_CONTEXT)
        TaskType.WORK, TaskType.PROJECT ->
            listOf(WORK_CONTEXT, ROUTINE, TASK_PREFERENCES)
        TaskType.TRAVEL, TaskType.EVENT ->
            listOf(PERSONAL_CONTEXT, ROUTINE)
        null ->  // tipo aún no detectado, inyectar todo
            MemoryCategory.values().toList()
        else ->
            listOf(TASK_PREFERENCES, VOCABULARY)
    }

    return memorySlots
        .filter { it.category in relevant && it.content.isNotBlank() }
        .joinToString("\n") { "[${it.category}]: ${it.content}" }
}
```

### Ejemplo de ROUTINE después de algunas semanas

```
El usuario trabaja de lunes a viernes.
Las mañanas son productivas, completa tareas complejas antes del mediodía.
Los lunes suele tener reuniones que retrasan sus tareas matutinas.
Come alrededor de las 13hs y suele crear tareas nuevas en ese horario.
Las tardes las usa para tareas cortas y administrativas.
Los viernes a la noche ignora casi todos los recordatorios.
Los sábados temprano tiene rutina de ejercicio.
```

Con este contexto, si el usuario escribe "recordatorio para el gym" a las 22hs del viernes, el sistema sabe que ese recordatorio debería ir al sábado temprano sin necesidad de preguntarle.

### Cómo resuelve el problema del clarification loop con el tiempo

Sin memoria: cada tarea se crea desde cero, el sistema siempre pregunta lo que no sabe.

Con memoria: después de algunas semanas, el sistema conoce los patrones del usuario y necesita preguntar cada vez menos.

```
Semana 1:
"lista de compras" → pregunta "¿Qué necesitás comprar?"
usuario responde: leche, pan, tomate, detergente

Semana 3:
VOCABULARY slot aprende: "lista de compras" del usuario
suele incluir productos básicos similares
→ pre-carga con sugerencias basadas en historial
→ no pregunta

Semana 6:
El sistema ya conoce sus productos habituales
→ crea la lista pre-poblada directamente
→ usuario solo agrega o quita lo que cambió
```

---

## 3. LLM recomendado para estas funciones

| Función | LLM recomendado | Razón |
|---|---|---|
| Classifier + content extraction | Gemma 2B via MediaPipe | Control sobre output JSON, funciona offline |
| Memory Writer | Gemma 2B via MediaPipe | Necesita razonamiento sobre categorías |
| Memory Compactor | Gemma 2B via MediaPipe | Output predecible, texto corto |
| Clarification question generation | Gemini Nano (si disponible) | Pregunta simple, baja latencia |
| Fallback API (todos) | Groq | Latencia baja, sin PII del usuario |

Gemini Nano es mejor para respuestas conversacionales rápidas (la pregunta de clarification). Gemma 2B es mejor cuando necesitás control sobre la estructura del output (JSON del Memory Writer, Task DSL poblado).

---

## 4. Impacto en la arquitectura existente

### Qué se agrega

- `memory_slots` tabla en Room
- `MemorySlot` domain model + DAO + Repository
- `CompletenessValidator` en `engine/classifier/`
- `MemoryWriter` en `engine/memory/`
- `MemoryCompactor` en `engine/memory/`
- `MemoryRepository` interfaz + implementación
- Campo `isSuggested` en `ChecklistItemEntity`
- Campo `needsClarification` en `TaskComponentEntity`
- `ClarificationCard` composable en `ui/components/`

### Qué se modifica

- `TaskClassifier` — agregar etapa de CompletenessValidator post-DSL
- `HabitAnalysisWorker` — agregar llamada a MemoryWriter al final del batch
- `CreateTaskViewModel` — manejar estado `NEEDS_CLARIFICATION`
- `System Prompt` — agregar sección de extracción de contenido
- `buildContextForClassifier()` — inyectar memoria relevante por tipo

### Qué NO cambia

- Room schema de tareas y componentes (solo campos nuevos, no breaking)
- CapabilityRegistry
- ReminderEngine y SM-2
- FetcherEngine
- HabitEngine (solo se le agrega un paso al Worker existente)

---

## 5. Orden de implementación sugerido

Estas features se insertan entre la Fase 2 (Classifier) y la Fase 3 (Habit Engine) del plan de implementación existente:

```
Fase 2 — Classifier (existente)
    ↓
Fase 2.5a — Clarification Loop
    - CompletenessValidator
    - ClarificationCard UI
    - Modificar CreateTaskViewModel
    - Actualizar System Prompt
    ↓
Fase 2.5b — Contextual Memory Layer
    - memory_slots DB + DAO + Repository
    - MemoryWriter (integrado en HabitAnalysisWorker)
    - MemoryCompactor
    - Inyección de contexto en Classifier
    ↓
Fase 3 — Habit Engine (existente, sin cambios)
```

La Fase 2.5b depende de que haya datos reales de uso para que la memoria tenga algo que aprender. Se puede implementar el esquema completo desde el inicio, pero la memoria no será útil hasta después de 1-2 semanas de uso real.

---

*Documento complementario al MVP Reference Document y al Plan de Implementación.*
*Actualizar cuando se tomen decisiones de implementación que afecten estos sistemas.*
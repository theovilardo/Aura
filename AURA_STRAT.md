# AURA — Informe Estratégico para Agente de Código
## Situación actual, filosofía del producto y objetivos de mejora
> Marzo 2026

---

## Contexto

AURA es una app Android de gestión de objetivos personales construida en Kotlin + Jetpack Compose. Tiene arquitectura local-first, un pipeline de clasificación de intención (ML Kit + TFLite + LLM), y un sistema de renderizado dinámico de componentes (TaskRenderer) que genera interfaces adaptadas al tipo de tarea.

La app tiene implementación parcial funcionando. El objetivo de este informe es orientar las mejoras inmediatas para acercarla a un estado lanzable.

---

## Filosofía central del producto

> **Aura es el lugar donde vas cuando tenés un objetivo que necesita estructura, no solo un recordatorio.**

Una reunión va en Google Calendar. Una compra va en Reminders. Pero "preparar el viaje a Europa", "bajar 5 kilos para diciembre", "terminar el curso de Kotlin", "ahorrar para el notebook" — esos no tienen un lugar natural en ninguna app, porque todas te dan la misma caja vacía.

**Aura da a cada tipo de objetivo la forma correcta de seguimiento.**

Esta frase es el filtro para cada decisión de producto y código. Si una feature no sirve a este propósito, no es prioritaria.

### Lo que hace diferente a AURA

El diferencial no es "task manager con IA". El diferencial es que **la interfaz muta según el tipo de objetivo**. Ninguna app de productividad mainstream hace esto:

- Notion genera texto
- Todoist genera listas
- **Aura genera interfaces**

Escribís "quiero ir a Europa en mayo" y el sistema no crea una línea de texto — crea un countdown al viaje, un checklist de preparación, y un campo de presupuesto. Esa es la apuesta. Todo el trabajo debe defender esa experiencia.

---

## Estado del producto: qué funciona y qué no

### Lo que ya está bien y no hay que tocar
- La arquitectura del pipeline de clasificación (EntityExtractor → IntentClassifier → DSL → ComponentCatalog) es sólida
- El patrón DSL como capa intermedia entre el LLM y la UI es correcto: el LLM no construye UI directamente, genera una representación estructurada que el sistema interpreta
- El TaskRenderer con su chrome consistente (título, icono, eyebrow stats) da coherencia visual entre componentes muy distintos
- El builder manual de módulos en CreateTaskScreen — es exactamente el modo correcto para usuarios avanzados

### Problemas críticos a resolver

#### 1. El usuario no entiende el diferencial en los primeros 60 segundos

Este es el problema más urgente. Si alguien descarga la app y no entiende en el primer uso por qué es diferente de Todoist, no hay arquitectura que salve el producto.

El onboarding actual (si existe) no demuestra la adaptabilidad. Necesita mostrar concretamente el momento "oh, entiendo": el usuario escribe algo, y la interfaz que aparece es inesperadamente específica y correcta para ese tipo de objetivo. Ese momento de sorpresa es el producto.

#### 2. Las "formas de tarea" no están nombradas ni comunicadas

El sistema internamente trabaja con `TaskType` y combinaciones de `ComponentType`. Pero el usuario no piensa en componentes — piensa en objetivos. "Quiero hacer un viaje", "quiero perder peso", "quiero ahorrar plata".

Actualmente el usuario no sabe qué "forma" le va a dar el sistema a su input, ni puede anticiparlo, ni puede pedirlo directamente. Eso hace que el diferencial sea invisible.

#### 3. El flujo CreateTask → preview → save necesita estar pulido

Es el flujo más usado de la app y cualquier fricción ahí destruye la experiencia. Incluye el Clarification Loop cuando el sistema necesita más información — ese momento tiene que sentirse como una conversación, no como un formulario.

#### 4. El enum TaskType tiene huecos conceptuales

Actualmente no cubre bien EVENT (cumpleaños, reunión, deadline) y GOAL (objetivo con milestones). Eso hace que muchos inputs caigan en GENERAL y el usuario recibe una estructura genérica en lugar de la forma correcta.

#### 5. Cuando el LLM no está disponible, la experiencia se degrada sin aviso

Si el clasificador cae en GENERAL por falta de confianza o ausencia de LLM, el usuario recibe `notes_brain_dump` sin ninguna explicación. Eso se siente roto, no degradado graciosamente.

---

## Objetivos de mejora — ordenados por impacto

### Objetivo 1 — Definir y nombrar las Task Shapes (alta prioridad)

**Qué es:** un layer por encima de `TaskType` que define 6-8 "formas" con nombre propio, contrato claro de componentes, y criterios de selección visibles para el usuario.

**Por qué importa:** hoy el usuario no sabe qué va a recibir cuando crea una tarea. Las Task Shapes dan predictibilidad y hacen el diferencial legible. "Esto es un Viaje" es mucho más claro que "esto tiene COUNTDOWN + CHECKLIST + METRIC_TRACKER".

**Shapes propuestas como punto de partida:**

| Shape | Nombre visible | Componentes core | Componentes opcionales |
|---|---|---|---|
| TRAVEL | Viaje | Countdown + Checklist | MetricTracker (presupuesto) |
| HABIT | Hábito | HabitRing + 30-day grid | Notes |
| HEALTH | Salud | MetricTracker + ProgressBar | HabitRing + Countdown |
| PROJECT | Proyecto | Checklist + ProgressBar | Countdown + Notes |
| FINANCE | Meta financiera | MetricTracker + ProgressBar | Countdown |
| EVENT | Evento | Countdown | Checklist + Notes |
| GOAL | Meta personal | ProgressBar + Countdown | Checklist + MetricTracker |
| NOTE | Nota libre | Notes | — |

Cada shape tiene: nombre visible al usuario, ícono propio, componentes que siempre aparecen, componentes opcionales, y criterios de cuándo el clasificador la elige.

El agente debe leer el `TaskType` actual, el `ComponentCatalog`, y la lógica de composición existente para implementar este layer de la forma más compatible posible con lo que ya existe.

**Resultado esperado:** cuando el sistema crea una tarea, el usuario ve claramente "esto es un Viaje" — no solo una colección de componentes sin nombre.

---

### Objetivo 2 — Onboarding que demuestra la adaptabilidad (alta prioridad)

**Qué es:** una secuencia de primer uso que muestra concretamente el diferencial de AURA en lugar de explicarlo con texto.

**Por qué importa:** el diferencial de AURA no se entiende con palabras, se entiende viéndolo. El onboarding es la única oportunidad de demostrar el "oh, entiendo" antes de que el usuario abandone.

**Concepto del flujo:**

```
Pantalla 1 — Bienvenida mínima
    No explicar qué es AURA con palabras largas
    Una sola frase: "Cada objetivo tiene su forma"
    CTA: "Ver cómo funciona"

Pantalla 2 — Demo interactiva (el momento clave)
    Input pre-cargado o escribible: "quiero ir a Europa en mayo"
    Animación que muestra cómo se construye la interfaz:
        aparece el countdown → aparece el checklist → aparece el campo de presupuesto
    El usuario ve que la app "entendió" algo específico

Pantalla 3 — Segundo ejemplo con tipo diferente
    "quiero correr 3 veces por semana"
    Aparece HabitRing + 30-day grid
    El contraste con el ejemplo anterior muestra la adaptabilidad

Pantalla 4 — Invitación a crear la primera tarea real
    "¿Qué objetivo querés seguir?"
    Input libre, sin templates forzados
```

El agente debe revisar si hay onboarding existente y decidir si extenderlo o rehacerlo. La prioridad es que sea corto (máximo 4 pantallas), visualmente demostrable, y que termine con el usuario creando su primera tarea real.

**Resultado esperado:** un usuario nuevo entiende el diferencial antes de llegar a la pantalla principal.

---

### Objetivo 3 — Pulir el flujo CreateTask → Clarification → Preview → Save (alta prioridad)

**Qué es:** revisar y mejorar la calidad de experiencia del flujo de creación de tareas de punta a punta.

**Por qué importa:** es el flujo más usado de la app. Cualquier fricción, estado inesperado o transición brusca destruye la confianza del usuario.

**Áreas específicas a revisar:**

- **Transiciones entre estados**: el paso de input → procesando → clarification (si aplica) → preview debe ser fluido y con feedback visual claro en cada momento
- **ClarificationCard**: si el sistema necesita más información, la pregunta debe sentirse como parte de una conversación, no como un campo de formulario. Una pregunta a la vez, con opción de saltear
- **Preview antes de guardar**: el usuario debe ver exactamente qué va a crearse antes de confirmar. Si la shape detectada no es la correcta, debe poder cambiarla fácilmente
- **Degradación cuando el LLM no está disponible**: si el clasificador cae en baja confianza o modo RULES_ONLY, el usuario debe recibir una explicación simple ("No pude detectar el tipo, elegí uno:") en lugar de una estructura genérica sin aviso
- **Undo en edición de componentes**: cualquier cambio en la configuración de un componente debe ser reversible. Sin undo, los usuarios avanzados que quieren editar son los más penalizados

**Resultado esperado:** el flujo completo desde escribir el input hasta tener la tarea guardada se siente pulido, predecible y sin estados rotos.

---

### Objetivo 4 — Completar el TaskType con EVENT y GOAL (prioridad media)

**Qué es:** agregar los tipos faltantes al enum TaskType y sus respectivas shapes, pipeline de clasificación y composición de componentes.

**Por qué importa:** actualmente muchos inputs válidos caen en GENERAL porque el clasificador no tiene un tipo apropiado. "Reunión con el cliente el jueves", "quiero aprender a tocar la guitarra" merecen formas propias, no una nota genérica.

**Lo que el agente debe hacer:**
- Leer el `TaskType` actual y el clasificador
- Agregar EVENT: optimizado para cosas con fecha fija (cumpleaños, reuniones, deadlines). Componentes core: Countdown. Opcionales: Checklist, Notes
- Agregar GOAL: optimizado para objetivos de mediano plazo sin fecha fija pero con milestones. Componentes core: ProgressBar + Checklist. Opcionales: Countdown, MetricTracker
- Actualizar el IntentClassifier con ejemplos de training apropiados para estos tipos
- Actualizar el ComponentCatalog con las reglas de composición para EVENT y GOAL

**Resultado esperado:** inputs que hoy caen en GENERAL ahora reciben la forma correcta.

---

### Objetivo 5 — Hacer el tier LLM invisible para el usuario (prioridad media)

**Qué es:** simplificar la configuración del LLM de forma que el usuario no tenga que tomar decisiones técnicas.

**Por qué importa:** actualmente el sistema expone decisiones de infraestructura al usuario (qué modelo usar, si descargar Gemma, si conectar Groq). Eso no es UX de producto — es UX de developer tool.

**El comportamiento correcto:**
- Si hay modelo local disponible y descargado: simplemente funciona, sin mención
- Si no hay modelo local: la app funciona en modo básico sin avisar, o pregunta una sola vez "¿Querés conectar Groq para mejor experiencia?" con explicación en lenguaje simple
- El usuario nunca elige un modelo. Nunca ve el nombre "Gemma" o "LiteRT" en la UI
- La descarga del modelo (si aplica) se presenta como "mejorar la IA de la app", no como "descargar Gemma 3 1B"

El agente debe revisar todas las pantallas de configuración relacionadas con LLM y simplificar el lenguaje y las decisiones expuestas al usuario.

**Resultado esperado:** un usuario no-técnico puede usar AURA sin entender nada de modelos de lenguaje.

---

## Qué NO tocar en esta etapa

Estas features existen en el codebase pero deben quedar congeladas. No eliminar — solo no invertir tiempo en ellas hasta después de un lanzamiento beta:

- **DataFeedComponent** (weather, currency, flights): mucha infraestructura, poco valor diferencial inmediato
- **Desktop sync / automatización remota**: es un producto separado, no una feature de AURA
- **Memory Layer (MemorySlot, MemoryWriter)**: correcto conceptualmente, pero necesita datos reales de uso para ser útil. Post-lanzamiento
- **Day Rescue**: feature potente pero que requiere datos de patrones reales para funcionar bien. Post-lanzamiento
- **SM-2 reminder system**: misma razón que Day Rescue. La complejidad es prematura sin usuarios reales

Si alguna de estas features está incompleta y genera errores o estados rotos en el flujo principal, corregir solo lo mínimo para que no interfiera — no desarrollar.

---

## Criterio de éxito para esta iteración

La iteración está completa cuando:

1. Un usuario nuevo puede abrir la app, entender el diferencial en los primeros 60 segundos, y crear su primera tarea sin confusión
2. Al crear una tarea, el usuario ve claramente qué "forma" le dio el sistema y puede cambiarla si no es la correcta
3. El flujo de creación de punta a punta no tiene estados rotos, transiciones bruscas, ni degradaciones silenciosas
4. Los inputs que antes caían en GENERAL (eventos con fecha, metas de mediano plazo) ahora reciben una forma apropiada
5. Ninguna pantalla de la app menciona nombres de modelos de lenguaje ni requiere decisiones técnicas del usuario

---

## Documentos de referencia del proyecto

Los siguientes documentos describen en detalle la arquitectura, el plan de implementación y los sistemas de inteligencia de AURA. El agente puede usarlos como referencia para entender decisiones de diseño:

- `aura_mvp.docx` — referencia completa del MVP, arquitectura y componentes
- `aura_impl_plan.docx` — plan de implementación por fases
- `aura_intelligence_layer.md` — Clarification Loop y Contextual Memory Layer
- `aura_llm_implementation.md` — tier system de LLM con LiteRT-LM

---

*Este informe define los objetivos de producto. El agente tiene autonomía para leer el codebase y decidir la implementación técnica más adecuada para cada objetivo.*
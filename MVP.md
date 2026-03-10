# AURA

## AI-Powered Personal Task Assistant

**MVP Reference Document**
**Arquitectura · Producto · Implementación**
**Versión 1.0 · Marzo 2026**

---

## 1. Visión y Problema

### 1.1 El problema

Las apps de gestión personal actuales son herramientas estáticas en un mundo dinámico. El usuario debe adaptarse a la herramienta, no al revés. Los problemas concretos son:

1. Los recordatorios son "tontos": se repiten en horarios fijos sin importar el contexto ni el comportamiento del usuario.
2. Las interfaces son genéricas: un checklist para todo, sin importar si la tarea es un viaje, un examen o un hábito de salud.
3. No hay aprendizaje: la app no sabe si el usuario completa sus tareas a la mañana o a la noche, si ignora recordatorios los lunes, o si siempre abandona tareas con más de 5 pasos.
4. App fragmentation: el usuario necesita un tracker de hábitos, una app de recordatorios, un gestor de tareas y una app de viajes por separado.
5. Friccción de setup: configurar manualmente cada tarea, componente y recordatorio es tedioso y provoca abandono temprano.

### 1.2 La visión

AURA es un asistente personal local-first para Android que combina gestión de tareas, habit tracking inteligente y automatizaciones contextuales en una sola app. La interfaz se adapta a cada tarea, el sistema aprende del comportamiento del usuario, y la IA actúa como motor de composición, no como chatbot.

> **CORE**
> La IA no dibuja interfaces: genera un schema estructurado (Task DSL) que el motor de la app convierte en Compose components optimizados para cada tipo de tarea.

### 1.3 Killer features del MVP

#### Context-aware Task Resurrection

Cuando una tarea muere (se ignora, pospone o abandona repetidamente), el sistema no la marca como fracaso. La analiza, entiende por qué falló usando los patrones del usuario, y propone una reestructuración con una explicación visible. El usuario aprueba o rechaza — la app nunca actúa sola.

#### Day Rescue

Con un tap, el usuario señala que su día se descarriló. El sistema reorganiza todas las tareas pendientes según el tiempo real disponible, el historial de energía del usuario en ese horario, y las prioridades declaradas. El resultado se presenta como una sugerencia completa para aprobar o editar, nunca se aplica automáticamente.

---

## 2. Principios de Diseño

| Principio            | Implicancia práctica                                                                                                 |
| -------------------- | -------------------------------------------------------------------------------------------------------------------- |
| Local-first          | Los datos viven en el dispositivo. La app funciona 100% offline. El servidor es un relay, nunca la fuente de verdad. |
| Suggest, never act   | La IA nunca modifica, mueve ni elimina datos sin aprobación explícita del usuario. Toda sugerencia es opt-in.        |
| Event-driven         | Sin polling continuo. Todo se dispara por eventos de usuario o WorkManager jobs. Consumo idle ≈ 0.                   |
| Privacy by design    | Datos sensibles nunca salen del dispositivo sin E2E encryption. Los fetchers externos nunca reciben PII.             |
| Graceful degradation | Sin modelo on-device → fallback a API. Sin internet → funciona igual. Sin BaaS → solo local.                         |
| Explainability       | Toda acción de la IA incluye un campo reasoning visible para el usuario. No hay cajas negras.                        |

---

## 3. Arquitectura del Sistema

### 3.1 Vista de capas

El sistema se organiza en cuatro capas bien separadas, donde cada capa solo conoce a la inmediatamente inferior:

```text
┌─────────────────────────────────────────────────────────┐
│                   UI LAYER  (Compose)                   │
│   TaskRenderer │ Dashboard │ Onboarding │ Widgets       │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                   DOMAIN LAYER                          │
│  Classifier  │  HabitEngine  │  CapabilityRegistry      │
│              TaskEngine                                  │
│  ReminderEngine  │  FetcherEngine  │  SuggestionEngine  │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                   DATA LAYER                            │
│  Room (SQLite)          WorkManager (background jobs)   │
│  tasks / components /   HabitAnalysisWorker             │
│  habit_signals /        FetcherWorker                   │
│  reminders /            ReminderWorker / SyncWorker     │
│  suggestions / sync_q   DataStore (preferences)         │
└────────────────────────┬────────────────────────────────┘
                         │  E2E encrypted (opcional)
┌────────────────────────▼────────────────────────────────┐
│                   EXTERNAL LAYER                        │
│  Supabase / self-hosted    APIs externas (sandboxed)    │
│  (sync relay, E2E)         device-side auth, sin PII    │
└─────────────────────────────────────────────────────────┘
```

### 3.2 Capability Registry

El componente más crítico del sistema. Actúa como sandbox entre la IA y el sistema operativo de la app. La IA nunca ejecuta nada directamente: emite objetos Capability que el Registry valida y despacha.

> **SEGURIDAD**
> Si la IA alucina una capability que no existe, el Registry la rechaza silenciosamente sin afectar el estado de la app.

#### Capabilities disponibles en el MVP

| Capability        | Descripción                                   | ¿Requiere aprobación? |
| ----------------- | --------------------------------------------- | --------------------- |
| CreateTask        | Crea una tarea nueva desde el Task DSL        | No                    |
| AddComponent      | Agrega un componente a una tarea existente    | No                    |
| ScheduleReminder  | Programa un recordatorio con estrategia SM-2  | No                    |
| ScheduleFetcher   | Configura un fetcher de datos externos        | No                    |
| LogSignal         | Registra una señal de hábito                  | No                    |
| ProposeSuggestion | Genera una sugerencia pendiente de aprobación | Siempre               |
| FetchExternalData | Request HTTP sandboxed (sin PII)              | No                    |

### 3.3 Habit Engine

Motor de aprendizaje del comportamiento del usuario. Corre en modo event-driven durante el día (registra señales silenciosamente) y en modo batch de madrugada vía WorkManager (procesa patrones y genera sugerencias).

#### Señales que registra

| Señal                          | Cuándo ocurre                           |
| ------------------------------ | --------------------------------------- |
| TASK_COMPLETED                 | El usuario marca la tarea como completa |
| TASK_PARTIALLY_COMPLETED       | Completa algunos ítems del checklist    |
| TASK_SKIPPED                   | El día pasa y la tarea queda sin tocar  |
| REMINDER_DISMISSED             | Cierra la notificación sin completar    |
| REMINDER_SNOOZED               | Pospone el recordatorio                 |
| SUGGESTION_APPROVED / REJECTED | Responde a una sugerencia del sistema   |

Algoritmo SM-2 para reminders: cada respuesta del usuario ajusta el `ease_factor` y el `interval` del recordatorio. `Dismissed` reduce el interval, `Completed` lo extiende. El sistema aprende cuándo el usuario es más receptivo para cada tipo de tarea.

### 3.4 Classifier Pipeline

Arquitectura en tres niveles para maximizar eficiencia y minimizar uso de recursos:

```text
Input del usuario (texto / voz)
         │
         ▼
  ML Kit Entity Extractor  ──────►  fechas, lugares, cantidades
         │                          (on-device, < 10ms)
         ▼
  IntentClassifier (TFLite)  ───►  tipo de tarea + confianza
         │                          (on-device, < 50ms)
         │
    confianza > 0.85?
    ┌─────┴──────┐
   SÍ            NO
    │             │
    ▼             ▼
  TaskDSL    Gemma 2B on-device  ──►  fallback a Groq API
  Builder    (MediaPipe LLM)          si HW insuficiente
    │             │
    └──────┬───────┘
           ▼
   Task DSL JSON validado
           ▼
   CapabilityRegistry.execute(CreateTask)
```

---

## 4. Task DSL y Componentes

### 4.1 ¿Qué es el Task DSL?

El Task DSL (Domain Specific Language) es el contrato entre la IA y el motor de UI. La IA genera un JSON estructurado que describe una tarea. El `TaskRenderer` lo convierte en Compose components. La IA nunca escribe código Compose.

```json
{
  "title": "Viaje a Madrid",
  "type": "TRAVEL",
  "theme": "travel",
  "targetDate": "2026-08-15",
  "components": [
    {
      "type": "COUNTDOWN",
      "config": {
        "targetDate": "2026-08-15",
        "label": "Días para el viaje"
      }
    },
    {
      "type": "CHECKLIST",
      "config": {
        "label": "Documentos y preparativos",
        "allowAddItems": true
      }
    },
    {
      "type": "DATA_FEED",
      "config": {
        "fetcherConfigId": "...",
        "displayLabel": "Precios de vuelos"
      }
    },
    {
      "type": "PROGRESS_BAR",
      "config": {
        "source": "SUBTASKS",
        "label": "Preparación general"
      }
    }
  ],
  "reminders": [
    {
      "strategy": "smart",
      "channels": ["push"]
    }
  ],
  "fetchers": [
    {
      "type": "FLIGHT_PRICES",
      "params": {
        "origin": "BUE",
        "destination": "MAD"
      },
      "schedule": "0 9 * * 1"
    }
  ]
}
```

**Ejemplo:** "Quiero viajar a Madrid en agosto"

### 4.2 Componentes del MVP

| Componente     | Casos de uso                                                   | Task types              |
| -------------- | -------------------------------------------------------------- | ----------------------- |
| PROGRESS_BAR   | Progreso de preparativos, porcentaje de completado de proyecto | PROJECT, TRAVEL, GOAL   |
| CHECKLIST      | Lista de documentos, compras, pasos de un proceso              | Todos                   |
| COUNTDOWN      | Cuenta regresiva a fecha de examen, vuelo, evento              | EVENT, LEARNING, TRAVEL |
| HABIT_RING     | Anillo visual de hábito diario/semanal (estilo Activity rings) | HABIT, HEALTH           |
| NOTES          | Texto libre con soporte markdown para contexto de la tarea     | Todos                   |
| METRIC_TRACKER | Tracking numérico con historial y gráfico (peso, km, gasto)    | HEALTH, FINANCE, GOAL   |
| DATA_FEED      | Datos externos actualizados: vuelos, clima, tipo de cambio     | TRAVEL, FINANCE         |

### 4.3 Reglas de composición

El System Prompt define qué componentes corresponden a cada tipo de tarea. Estas reglas son determinísticas para los casos de confianza alta:

| Task Type | Componentes obligatorios | Componentes opcionales                 |
| --------- | ------------------------ | -------------------------------------- |
| TRAVEL    | COUNTDOWN, CHECKLIST     | PROGRESS_BAR, DATA_FEED (vuelos/clima) |
| HABIT     | HABIT_RING               | METRIC_TRACKER, NOTES                  |
| HEALTH    | HABIT_RING               | METRIC_TRACKER, COUNTDOWN              |
| GOAL      | PROGRESS_BAR             | METRIC_TRACKER, COUNTDOWN, NOTES       |
| FINANCE   | METRIC_TRACKER           | DATA_FEED (tipo de cambio), NOTES      |
| EVENT     | COUNTDOWN                | CHECKLIST, NOTES                       |
| PROJECT   | PROGRESS_BAR             | CHECKLIST, NOTES                       |
| REMINDER  | NOTES                    | —                                      |

---

## 5. Database Schema

### 5.1 Entidades principales

| Tabla           | Descripción                                                         | Campos clave                                                |
| --------------- | ------------------------------------------------------------------- | ----------------------------------------------------------- |
| tasks           | Unidad central. Una tarea puede tener subtareas (`parent_task_id`). | `id`, `type`, `status`, `schema_json`, `target_date`        |
| task_components | Bloques visuales que componen cada tarea. Uno a muchos con `tasks`. | `task_id`, `component_type`, `config_json`, `sort_order`    |
| checklist_items | Ítems de componentes tipo `CHECKLIST`.                              | `component_id`, `label`, `is_completed`, `sort_order`       |
| habit_signals   | Log crudo de comportamiento. Nunca se modifica, solo se agrega.     | `task_id`, `signal_type`, `hour_of_day`, `day_of_week`      |
| user_patterns   | Patrones agregados. Se recalculan en batch nocturno.                | `task_type`, `hour_of_day`, `completion_rate`, `confidence` |
| reminders       | Config y estado de recordatorios. Incluye campos SM-2.              | `task_id`, `strategy`, `ease_factor`, `interval_days`       |
| fetcher_configs | Automatizaciones de datos externos por tarea.                       | `task_id`, `fetcher_type`, `params_json`, `cron_expression` |
| suggestions     | Sugerencias pendientes de aprobación del usuario.                   | `type`, `reasoning`, `payload_json`, `status`, `expires_at` |
| sync_queue      | Cola de cambios locales para sincronización eventual.               | `entity_type`, `entity_id`, `operation`, `payload_json`     |

> **DISEÑO**
> `habit_signals` es append-only por diseño. Nunca se actualiza ni elimina un registro. `user_patterns` es su proyección calculada. Esto garantiza que el historial de comportamiento siempre sea auditable.

### 5.2 Índices críticos

```sql
-- Consultas frecuentes del ReminderEngine
CREATE INDEX idx_reminders_scheduled
  ON reminders(scheduled_at, is_active);

-- Consultas frecuentes del HabitEngine
CREATE INDEX idx_habit_signals_time
  ON habit_signals(hour_of_day, day_of_week);

-- Lookup de patrones por tipo de tarea y horario
CREATE INDEX idx_patterns_lookup
  ON user_patterns(task_type, hour_of_day, day_of_week);

-- Sugerencias pendientes
CREATE INDEX idx_suggestions_status
  ON suggestions(status, expires_at);

-- Sync queue pendiente
CREATE INDEX idx_sync_queue_pending
  ON sync_queue(synced_at) WHERE synced_at IS NULL;
```

---

## 6. Reminder Engine y Fetcher Engine

### 6.1 Reminder Engine — SM-2 adaptado

El algoritmo SM-2 (originalmente diseñado para flashcards en Anki) se adapta aquí para calcular el intervalo óptimo entre recordatorios según la respuesta del usuario:

```kotlin
fun calculateNextInterval(reminder: Reminder, response: Response): Reminder {
  val quality = when (response) {
    COMPLETED  -> 5
    SNOOZED    -> 3
    DISMISSED  -> 1
  }

  val newEaseFactor = max(
    1.3f,
    reminder.easeFactor +
      (0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f))
  )

  val newInterval = when {
    quality < 3               -> 1
    reminder.repetitions == 0 -> 1
    reminder.repetitions == 1 -> 6
    else -> (reminder.intervalDays * newEaseFactor).toInt()
  }

  return reminder.copy(
    easeFactor = newEaseFactor,
    intervalDays = newInterval,
    repetitions = if (quality >= 3) reminder.repetitions + 1 else 0
  )
}
```

* `COMPLETED` → mejor respuesta
* `SNOOZED` → respuesta media
* `DISMISSED` → peor respuesta

El `ReminderEngine` también calcula el horario óptimo consultando `user_patterns`. Si el usuario tiene historial de completar hábitos de salud a las 19hs los martes, el sistema sugiere ese horario para tareas similares.

### 6.2 Fetcher Engine — Sandbox HTTP

| Fetcher           | API / fuente                          | En MVP  | Auth requerida            |
| ----------------- | ------------------------------------- | ------- | ------------------------- |
| FLIGHT_PRICES     | Aviasales / Skyscanner API pública    | Sí      | API key embebida (no PII) |
| WEATHER           | Open-Meteo (gratuito, sin key)        | Sí      | No                        |
| CURRENCY_EXCHANGE | exchangerate-api.com (tier free)      | Sí      | API key embebida          |
| STOCK_PRICE       | Yahoo Finance / Alpha Vantage         | No (v2) | API key                   |
| CUSTOM_URL        | Endpoint público definido por usuario | No (v2) | Configurable              |
| GOOGLE_DRIVE_FILE | Google Drive API                      | No (v2) | OAuth PKCE                |

> **PRIVACIDAD**
> El `Fetcher Engine` aplica sanitización de parámetros antes de toda request: elimina cualquier campo que pueda contener PII (`name`, `email`, `phone`, `address`). Los datos fetched se cachean localmente y nunca pasan por el backend propio.

---

## 7. Flujos Principales

### 7.1 Creación de tarea

```text
Usuario: "Quiero viajar a Madrid en agosto"
         │
         ▼
  ML Kit → lugar: Madrid, mes: agosto
         │
         ▼
  IntentClassifier → TRAVEL (confianza: 0.91) ✓
         │
         ▼  (path determinístico, sin LLM)
  TaskDSLBuilder.build(TRAVEL, entities)
         │
         ▼
  CapabilityRegistry.execute(CreateTask)
         │
         ├─► TaskEngine → Room DB
         ├─► ReminderEngine → calcula horario óptimo
         └─► WorkManager → schedula FetcherWorker (vuelos)
         │
         ▼
  TaskRenderer → UI con 4 componentes
```

### 7.2 Context-aware Task Resurrection

```text
[02:00 — WorkManager activa HabitAnalysisWorker]
         │
         ▼
  Detecta: "Aprender inglés"
           completion_rate: 8%
           última interacción: hace 14 días
           historial: completaba martes 20hs
         │
         ▼
  HabitEngine.generateSuggestion(RESURRECT_TASK) {
    reasoning: "No interactuaste con esta tarea en 14 días.
               Antes la completabas los martes a las 20hs.
               ¿Querés retomar con sesiones de 10 minutos?"
    payload: { simplify: true, newDuration: 10 }
  }
         │
         ▼
  [Mañana 9am — usuario abre la app]
         │
  SuggestionCard visible con reasoning completo
  [Sí, retomar] → TaskEngine aplica cambios
  [No por ahora] → registra rechazo, no repite por 7 días
  [Archivar]     → soft delete con señal
```

### 7.3 Day Rescue

```text
Usuario toca "El día se descarriló"
         │
         ▼
  DayRescueEngine recolecta:
  - Tareas pendientes del día (de Room)
  - Tiempo estimado disponible
  - user_patterns del horario actual
  - Prioridades declaradas (deadline, type)
         │
         ▼
  LLM genera reorganización:
  "Movemos ejercicio a mañana (tu mejor horario).
   Priorizamos el informe (deadline hoy).
   Posponemos compras al fin de semana."
         │
         ▼
  Sugerencia completa → aprobación del usuario
  [Aplicar todo]  → TaskEngine ejecuta batch
  [Editar]        → usuario ajusta antes de aplicar
  [Cancelar]      → sin cambios
```

---

## 8. Stack Técnico

| Capa              | Tecnología                | Justificación                                                     |
| ----------------- | ------------------------- | ----------------------------------------------------------------- |
| UI                | Jetpack Compose           | Interfaces dinámicas, animaciones, reactividad con StateFlow      |
| DI                | Hilt                      | Estándar Android, integración nativa con ViewModel y WorkManager  |
| DB local          | Room + SQLite             | Estándar Android, type-safe, soporte para Flow reactivo           |
| Async             | Coroutines + Flow         | Cancelación estructurada, integración total con Room y Compose    |
| Background        | WorkManager               | Respeta Doze mode, garantiza ejecución, retry automático          |
| HTTP              | OkHttp + Retrofit         | Madurez, interceptors para sanitización de PII                    |
| Serialización     | Kotlin Serialization      | Type-safe, soporte para sealed classes del Task DSL               |
| Clasificación NLP | ML Kit Entity Extraction  | On-device, sin latencia de red, detecta fechas/lugares            |
| Intent classifier | TFLite (modelo propio)    | Liviano, inferencia < 50ms, funciona offline                      |
| LLM on-device     | Gemma 2B via MediaPipe    | Google-soportado, optimizado para Android, Pixel 6+               |
| LLM API fallback  | Groq API                  | Latencia < 500ms, barato, compatible con Gemma/Llama              |
| Sync / BaaS       | Supabase (self-hosteable) | Open source, Row Level Security, E2E encryption posible           |
| Notificaciones    | NotificationManager local | Push local sin FCM para MVP, sin dependencia de servidor          |
| Preferencias      | DataStore                 | Reemplaza SharedPreferences, soporte para coroutines              |
| Testing           | JUnit5 + Turbine + MockK  | Turbine para testing de Flow, MockK para mocks Kotlin-idiomáticos |

### 8.1 Estrategia de IA por hardware

| Hardware                             | Estrategia                                    | Modelos                   |
| ------------------------------------ | --------------------------------------------- | ------------------------- |
| Pixel 6+ / Snapdragon 8xx con AICore | Gemini Nano via AICore (nativo del SO)        | Gemini Nano               |
| Android 8+ con GPU/NPU compatible    | Gemma 2B via MediaPipe LLM Inference API      | Gemma 2B 4-bit cuantizado |
| Hardware limitado / sin soporte      | Groq API (datos mínimos, sin PII del usuario) | Gemma 7B / Llama 3        |
| Clasificación simple (todos)         | ML Kit + TFLite intent classifier             | Modelo propio entrenado   |

---

## 9. Scope del MVP

### 9.1 Lo que entra en el MVP

#### Core obligatorio

1. Creación de tareas por texto natural con classifier + LLM fallback
2. 7 componentes Compose: `CHECKLIST`, `PROGRESS_BAR`, `COUNTDOWN`, `HABIT_RING`, `NOTES`, `METRIC_TRACKER`, `DATA_FEED`
3. 7 task types: `TRAVEL`, `HABIT`, `HEALTH`, `GOAL`, `FINANCE`, `EVENT`, `PROJECT`, `REMINDER`
4. Reminder Engine con algoritmo SM-2
5. Habit Engine: logging de señales + batch nocturno vía WorkManager
6. Suggestions con aprobación explícita: Task Resurrection + Day Rescue
7. DB local con Room, 100% funcional offline
8. Notificaciones push locales

#### Entra simplificado

1. 3 fetchers: `FLIGHT_PRICES`, `WEATHER`, `CURRENCY_EXCHANGE` (los tres sin auth de usuario)
2. Sync básico vía Supabase (solo `tasks` y `components`, no `habit_signals`)
3. 1 canal de notificación: push local (Telegram en v2)
4. Onboarding conversacional básico (captura contexto inicial del usuario)

### 9.2 Lo que NO entra en el MVP

| Feature                     | Versión objetivo | Razón de exclusión                                      |
| --------------------------- | ---------------- | ------------------------------------------------------- |
| LEARNING_DECK component     | v2               | Requiere motor de flashcards + spaced repetition propio |
| QUICK_LINKS component       | v2               | Bajo impacto en MVP, complejidad de deep linking        |
| MEDIA_REFERENCE component   | v2               | Requiere OAuth con Google Drive / Dropbox               |
| Telegram como canal         | v2               | Requiere bot setup del usuario, fuera del MVP           |
| Sync cross-device UI (web)  | v2               | Complejidad de auth + web app separada                  |
| Fetchers con OAuth          | v2               | Google Calendar, Drive: OAuth PKCE flow complejo        |
| STOCK_PRICE fetcher         | v2               | Menor demanda que vuelos/clima                          |
| Developer Mode / API propia | v3               | Segundo producto, post-product-market-fit               |
| Gamificación / social       | v3               | No diferencia del MVP, riesgo de scope creep            |

---

## 10. Roadmap

| Fase                   | Duración estimada | Entregables                                                    |
| ---------------------- | ----------------- | -------------------------------------------------------------- |
| Fase 0 — Fundamentos   | 2-3 semanas       | Room schema, entidades Kotlin, Hilt setup, CI/CD básico        |
| Fase 1 — Core UI       | 3-4 semanas       | TaskRenderer, 7 componentes Compose, TaskScreen, navegación    |
| Fase 2 — Classifier    | 2-3 semanas       | ML Kit integration, TFLite intent classifier, Task DSL builder |
| Fase 3 — Habit Engine  | 2-3 semanas       | SignalLogging, WorkManager batch, SM-2 para reminders          |
| Fase 4 — Fetchers      | 1-2 semanas       | FetcherEngine, 3 fetchers MVP, DATA_FEED component             |
| Fase 5 — Suggestions   | 2-3 semanas       | Resurrection flow, Day Rescue, SuggestionCard UI               |
| Fase 6 — Sync          | 2 semanas         | Supabase integration, sync_queue, E2E encryption               |
| Fase 7 — Polish + Beta | 2-3 semanas       | Onboarding, widgets, performance, testing, Play Store          |

> **NOTA**
> Las estimaciones asumen desarrollo individual a tiempo parcial. Los tiempos se comprimen significativamente con dedicación full-time o equipo.

---

## 11. Privacidad y Seguridad

### 11.1 Qué nunca sale del dispositivo sin cifrado

1. `habit_signals`: log de comportamiento del usuario (horarios, tasas de completado)
2. `user_patterns`: patrones agregados del comportamiento
3. `suggestions`: propuestas de la IA y respuestas del usuario
4. Contenido de notas y descripciones de tareas

### 11.2 Qué puede salir del dispositivo

1. `Tasks` y `components`: sincronizados E2E encrypted al BaaS (el servidor no ve plaintext)
2. `Fetcher requests`: solo parámetros públicos (origen/destino de vuelo, ciudad para clima). Sanitizados de PII antes de la request.
3. `LLM fallback (Groq)`: solo el texto de input de la tarea nueva. Sin historial, sin PII, sin datos de otras tareas.

### 11.3 Control del usuario

| Control                   | Comportamiento por defecto | Configurable                      |
| ------------------------- | -------------------------- | --------------------------------- |
| Aprobación de sugerencias | Siempre requerida          | No (no hay modo autónomo en MVP)  |
| Sync a BaaS               | Desactivado                | Sí, opt-in explícito              |
| LLM fallback a API        | Activado con aviso         | Sí, puede forzar on-device only   |
| Fetchers externos         | Activado por tarea         | Sí, desactivable por tarea        |
| Reasoning visible         | Siempre visible            | No (es garantía de transparencia) |

---

**AURA — MVP Reference Document · Confidencial · Marzo 2026**

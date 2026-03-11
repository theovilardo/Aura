
# AURA
## AI-Powered Personal Task Assistant

─────────────────────────────  
**Plan de Implementación**  
**Guía paso a paso para construir el MVP**

**Versión 1.0 · Marzo 2026 · Stack: Android / Kotlin / Jetpack Compose**

---

## Cómo usar este documento

Este plan está diseñado para desarrollo individual a tiempo parcial. Cada fase tiene tareas concretas con criterios de "done" explícitos, dependencias marcadas, y un checklist de verificación antes de avanzar a la siguiente fase.

> **REGLA**  
> No avanzar a la siguiente fase hasta completar el checklist de verificación de la actual. Las dependencias entre fases son reales: saltear pasos genera deuda técnica que frena el desarrollo más adelante.

### Referencia de columnas

| Columna | Significado |
|---|---|
| ID | Identificador único de la tarea para referenciar dependencias |
| Tarea + detalle | Qué construir y decisiones técnicas puntuales |
| Done | Checkbox para marcar al completar |
| Deps | IDs de tareas que deben estar completas antes de empezar esta |

---

## Estructura del proyecto (single module)

```text
aura/
├── app/src/main/java/com/aura/
│   ├── data/
│   │   ├── db/              # Room DAOs, Database, Entities
│   │   ├── repository/      # Repositorios (implementaciones)
│   │   └── worker/          # WorkManager Workers
│   ├── domain/
│   │   ├── model/           # Domain models (Task, Reminder, etc.)
│   │   ├── repository/      # Interfaces de repositorios
│   │   └── usecase/         # Use cases por feature
│   ├── engine/
│   │   ├── capability/      # CapabilityRegistry + Capabilities
│   │   ├── classifier/      # TaskClassifier pipeline
│   │   ├── habit/           # HabitEngine + SM-2
│   │   ├── reminder/        # ReminderEngine
│   │   ├── fetcher/         # FetcherEngine + fetchers
│   │   └── suggestion/      # SuggestionEngine
│   ├── ui/
│   │   ├── components/      # Compose components reutilizables
│   │   ├── renderer/        # TaskRenderer + ComponentRenderer
│   │   ├── screen/          # Screens (Home, TaskDetail, etc.)
│   │   └── theme/           # MaterialTheme, colores, tipografía
│   └── di/                  # Módulos Hilt
└── app/src/test/            # Unit tests por capa
````

---

# Fase 0 — Fundamentos del Proyecto

| Fase | Nombre                   | Duración estimada | Estado |
| ---- | ------------------------ | ----------------: | ------ |
| F0   | Fundamentos del Proyecto |       2–3 semanas | [x]    |

**Objetivo:** proyecto compilable, Room funcionando, Hilt configurado, y navegación básica operativa. Sin esta base todo lo demás se construye sobre arena.

## Tareas

| ID    | Tarea + detalle                                                                                                                                                                                                                  | Done   | Deps  |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ----- |
| F0-01 | **Crear proyecto Android en Android Studio**  Min SDK 30 (Android 11). Package: `com.theveloper.aura`. Language: Kotlin. Build: Gradle Kotlin DSL (`.kts`).                                                                              | ☑ Done | —     |
| F0-02 | **Configurar dependencias en `libs.versions.toml`**  Compose BOM, Hilt, Room, Coroutines, WorkManager, DataStore, Kotlin Serialization, OkHttp, Retrofit, MockK, Turbine.                                                        | ☑ Done | F0-01 |
| F0-03 | **Setup Hilt — Application class + módulos base**  `AuraApplication` con `@HiltAndroidApp`. Módulo `DatabaseModule` vacío. Módulo `RepositoryModule` vacío.                                                                      | ☑ Done | F0-02 |
| F0-04 | **Definir enums y sealed classes del dominio**  `TaskType`, `TaskStatus`, `ComponentType`, `SignalType`, `FetcherType`, `SuggestionType`, `NotificationChannel`. En `domain/model/`.                                             | ☑ Done | F0-03 |
| F0-05 | **Implementar Room Database — Entidades**  `TaskEntity`, `TaskComponentEntity`, `ChecklistItemEntity`, `HabitSignalEntity`, `UserPatternEntity`, `ReminderEntity`, `FetcherConfigEntity`, `SuggestionEntity`, `SyncQueueEntity`. | ☑ Done | F0-04 |
| F0-06 | **Implementar Room Database — DAOs**  Un DAO por entidad. `TaskDao` con `Flow<List<TaskEntity>>` para queries reactivos. `HabitSignalDao` append-only (solo insert, sin update/delete).                                          | ☑ Done | F0-05 |
| F0-07 | **Implementar Room Database — AuraDatabase**  `RoomDatabase` con todas las entidades. Migrations vacías pero preparadas. `TypeConverters` para JSON (`ComponentConfig`) y listas.                                                | ☑ Done | F0-06 |
| F0-08 | **Definir interfaces de repositorios**  `TaskRepository`, `ReminderRepository`, `HabitRepository`, `SuggestionRepository`. Solo interfaces en `domain/repository/`. Las implementaciones van en `data/repository/`.              | ☑ Done | F0-07 |
| F0-09 | **Implementar repositorios — TaskRepository**  `TaskRepositoryImpl`: CRUD completo, `getTasksFlow()` reactivo, `getTaskWithComponents()`. Mappers Entity ↔ Domain model.                                                         | ☑ Done | F0-08 |
| F0-10 | **Setup navegación con Compose Navigation**  `NavGraph` con rutas: `Home`, `TaskDetail(taskId)`, `CreateTask`, `Settings`. `BottomNavigation` básica. Screens vacías con texto placeholder.                                      | ☑ Done | F0-03 |
| F0-11 | **MaterialTheme de AURA**  Paleta de colores (light/dark). Tipografía con Inter o similar. Spacing constants. Todo en `ui/theme/`.                                                                                               | ☑ Done | F0-03 |
| F0-12 | **CI básico con GitHub Actions**  Workflow: build + lint + unit tests en cada push a `main`. No bloquea merge pero reporta estado.                                                                                               | ☑ Done | F0-01 |

## Checklist de verificación — no avanzar sin esto

1. El proyecto compila sin warnings en modo release
2. Room migrations corren sin errores en emulador y dispositivo físico
3. Hilt inyecta correctamente en un ViewModel de prueba
4. La navegación entre screens placeholder funciona
5. Al menos un unit test de `TaskRepository` pasa con base de datos in-memory
6. GitHub Actions reporta build exitoso

> **DECISIÓN**
> Los `TypeConverters` de Room para `ComponentConfig` deben serializar a JSON usando **Kotlin Serialization**, **NO Gson**. Esto es importante para la consistencia con el resto del stack.

---

# Fase 1 — Core UI y TaskRenderer

| Fase | Nombre                 | Duración estimada | Estado |
| ---- | ---------------------- | ----------------: | ------ |
| F1   | Core UI y TaskRenderer |       3–4 semanas | [x]    |

**Objetivo:** `TaskRenderer` funcional con los 7 componentes del MVP. El usuario puede ver tareas con sus componentes renderizados correctamente. No hay creación de tareas todavía: se usan datos hardcodeados (fakes) para desarrollo.

## Tareas

| ID    | Tarea + detalle                                                                                                                                                                                             | Done   | Deps         |
| ----- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ------------ |
| F1-01 | **Domain models completos — Task, TaskComponent, Reminder, etc.**  Data classes en `domain/model/`. Incluir `ComponentConfig` como sealed class con todos los subtipos del MVP. Mappers desde/hacia Entity. | ☑ Done | F0-09        |
| F1-02 | **FakeTaskRepository para desarrollo de UI**  Implementación in-memory de `TaskRepository` con tareas de muestra de cada `TaskType`. Permite desarrollar UI sin backend completo.                           | ☑ Done | F1-01        |
| F1-03 | **Componente: CHECKLIST**  `ChecklistComponent(config, items, onItemToggle)`. Animación de tachado al completar ítem. Estado local con `remember`.                                                          | ☑ Done | —            |
| F1-04 | **Componente: PROGRESS_BAR**  `ProgressBarComponent(config)`. Animación de llenado con `animateFloatAsState`. Soporta fuente `SUBTASKS` y `MANUAL`.                                                         | ☑ Done | —            |
| F1-05 | **Componente: COUNTDOWN**  `CountdownComponent(config)`. Cálculo de días/horas restantes. Color cambia a warning cuando quedan menos de 7 días.                                                             | ☑ Done | —            |
| F1-06 | **Componente: HABIT_RING**  `HabitRingComponent(config)`. Canvas con arco animado (estilo Activity rings). Tap para registrar completado del día.                                                           | ☑ Done | —            |
| F1-07 | **Componente: NOTES**  `NotesComponent(config)`. `TextField` expandible con soporte básico de markdown (bold, italic, listas). Autoguardado con debounce 500ms.                                             | ☑ Done | —            |
| F1-08 | **Componente: METRIC_TRACKER**  `MetricTrackerComponent(config)`. Input numérico + mini `LineChart` con Vico o Canvas. Historial de últimos 30 registros.                                                   | ☑ Done | —            |
| F1-09 | **Componente: DATA_FEED**  `DataFeedComponent(config)`. Estado: loading / data(value) / error / stale(lastValue, timestamp). Ícono de refresh manual.                                                       | ☑ Done | —            |
| F1-10 | **ComponentRenderer — dispatcher central**  `when(component.type)` que mapea a cada Composable. `UnknownComponentFallback` para tipos no soportados. En `ui/renderer/`.                                     | ☑ Done | F1-03 a F1-09|
| F1-11 | **TaskHeader**  Título, tipo (badge con ícono), prioridad, `target date` si existe, botón de opciones (⋮).                                                                                                  | ☑ Done | F1-01        |
| F1-12 | **TaskRenderer — composable raíz**  `TaskHeader` + componentes ordenados por `sort_order` via `LazyColumn`. Maneja estado vacío y loading. `onSignal` callback hacia ViewModel.                             | ☑ Done | F1-10, F1-11 |
| F1-13 | **TaskDetailScreen + TaskDetailViewModel**  Carga Task por ID con `StateFlow`. Pasa `onSignal` al `TaskRenderer`. FAB para completar tarea.                                                                 | ☑ Done | F1-02, F1-12 |
| F1-14 | **HomeScreen — lista de tareas**  `LazyColumn` de `TaskCards`. Card muestra título, tipo, progreso resumido, próximo reminder. Tap navega a `TaskDetail`.                                                   | ☑ Done | F1-02        |
| F1-15 | **TaskCard — versión compacta**  Versión reducida del `TaskRenderer` para la home. Muestra 1-2 datos clave según `TaskType` (countdown para `TRAVEL`, ring para `HABIT`, etc.).                             | ☑ Done | —            |
| F1-16 | **Empty states y error states**  Ilustración + texto para lista vacía, error de carga, tarea archivada. Reutilizables en toda la app.                                                                       | ☑ Done | F1-13, F1-14 |

## Checklist de verificación

1. Los 7 componentes renderizan correctamente con datos fake en emulador y dispositivo físico
2. `HABIT_RING` tiene animación fluida (60fps verificado con Layout Inspector)
3. `METRIC_TRACKER` muestra gráfico con al menos 5 puntos de datos
4. `DATA_FEED` muestra correctamente los 4 estados: loading, data, error, stale
5. `TaskRenderer` maneja correctamente 1, 2, 3 y 4 componentes
6. `HomeScreen` lista al menos 5 tareas fake de distintos tipos sin scroll issues
7. La navegación `Home → TaskDetail → Home` funciona con back stack correcto
8. Dark mode funciona en todos los componentes sin colores hardcodeados

> **PERFORMANCE**
> Todos los componentes deben evitar recomposiciones innecesarias. Usar `@Stable` en los data classes de config y `remember` donde corresponda. Verificar con el Recomposition Counter del Layout Inspector.

---

# Fase 2 — Classifier y Creación de Tareas

| Fase | Nombre                          | Duración estimada | Estado |
| ---- | ------------------------------- | ----------------: | ------ |
| F2   | Classifier y Creación de Tareas |       2–3 semanas | [ ]    |

**Objetivo:** el usuario puede crear tareas escribiendo texto natural. El classifier las convierte en Task DSL y el sistema las persiste. El `CapabilityRegistry` está operativo como sandbox.

## Tareas

| ID    | Tarea + detalle                                                                                                                                                                                                                              | Done   | Deps        |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ----------- |
| F2-01 | **Task DSL — modelos Kotlin + serialización**  `TaskDSLOutput`, `ComponentDSL`, `ReminderDSL`, `FetcherDSL` con `@Serializable`. Validador que verifica coherencia del DSL antes de pasarlo al Registry.                                     | ☐ Done | F1-01       |
| F2-02 | **TaskDSLBuilder — path determinístico**  Builder que construye `TaskDSLOutput` desde `IntentResult` + entidades ML Kit. Reglas hardcodeadas por `TaskType` (`TRAVEL→COUNTDOWN+CHECKLIST`, etc.).                                            | ☐ Done | F2-01       |
| F2-03 | **ML Kit Entity Extraction — integración**  Dependencia: `com.google.mlkit:entity-extraction`. Extractor de fechas, lugares y números. Wrapper `EntityExtractorService` en `engine/classifier/`.                                             | ☐ Done | F0-03       |
| F2-04 | **IntentClassifier — modelo TFLite**  Modelo TFLite liviano para clasificar texto en `TaskType`. Si no tenés modelo propio todavía: usar reglas de keywords como placeholder (regex por tipo). Reemplazable sin cambiar la interfaz.         | ☐ Done | F2-03       |
| F2-05 | **TaskClassifier — pipeline completo**  Orquesta ML Kit → IntentClassifier → decisión por confianza → `TaskDSLBuilder` o `LLMService`. Umbral: `0.85f`.                                                                                      | ☐ Done | F2-02,F2-04 |
| F2-06 | **LLMService — interfaz + implementación Groq**  Interfaz `LLMService` con `suspend fun classify(input, context): TaskDSLOutput`. `GroqLLMService` como implementación: POST a `api.groq.com` con el System Prompt. Parseo de JSON response. | ☐ Done | F2-01       |
| F2-07 | **System Prompt v1**  Prompt completo con todos los `TaskTypes`, `ComponentTypes`, reglas de composición y ejemplos. Almacenado en `assets/system_prompt.txt`. Inyectado en runtime por `LLMService`.                                        | ☐ Done | F2-06       |
| F2-08 | **CapabilityRegistry — implementación completa**  Todas las Capabilities del MVP. Validación de schema antes de ejecutar. `sanitizeParams()` para requests externas. Logging de cada capability ejecutada.                                   | ☐ Done | F2-05       |
| F2-09 | **TaskEngine — use cases de creación**  `CreateTaskUseCase`: recibe `TaskDSLOutput`, lo convierte a entidades Room, persiste via Repository. `UpdateTaskStatusUseCase`. `ArchiveTaskUseCase`.                                                | ☐ Done | F2-08       |
| F2-10 | **CreateTaskScreen — UI de creación**  `TextField` grande con placeholder `"Describí tu tarea..."`. Botón de envío. Estado: idle / classifying / preview / saving / error.                                                                   | ☐ Done | F1-14       |
| F2-11 | **TaskPreviewBottomSheet**  Antes de guardar: muestra el `TaskDSLOutput` interpretado en lenguaje natural. `"Vamos a crear una tarea de tipo Viaje con estos componentes: ..."` con opción de confirmar o cancelar.                          | ☐ Done | F2-10       |
| F2-12 | **CreateTaskViewModel**  Orquesta `TaskClassifier` → preview → `CapabilityRegistry.execute(CreateTask)`. Maneja estados de error del classifier y del LLM.                                                                                   | ☐ Done | F2-09,F2-11 |

## Checklist de verificación

1. Texto `"quiero viajar a Madrid en agosto"` genera correctamente una tarea `TRAVEL`
2. Texto `"recordatorio para tomar agua cada 2 horas"` genera `HABIT` con `HABIT_RING`
3. Texto ambiguo activa el fallback a LLM sin crash
4. El `TaskPreviewBottomSheet` muestra información comprensible (no JSON crudo)
5. La tarea creada aparece en `HomeScreen` con sus componentes correctos
6. ML Kit extrae fechas en español correctamente (`"20 de junio"`, `"la semana que viene"`)
7. El `CapabilityRegistry` rechaza una Capability con schema inválido sin crash
8. Unit tests del `TaskClassifier` cubren: confianza alta, confianza baja, input vacío, input en otro idioma

> **PRIVACIDAD**
> El texto que va a Groq API nunca debe incluir datos de otras tareas ni historial del usuario. Solo el input de la tarea nueva y el System Prompt. Verificar con un interceptor OkHttp en debug.

---

# Fase 3 — Habit Engine y Reminder Engine

| Fase | Nombre                         | Duración estimada | Estado |
| ---- | ------------------------------ | ----------------: | ------ |
| F3   | Habit Engine y Reminder Engine |       2–3 semanas | [x]    |

**Objetivo:** el sistema registra señales de comportamiento, calcula patrones, y programa recordatorios inteligentes con SM-2. WorkManager corre el análisis nocturno.

## Tareas

| ID    | Tarea + detalle                                                                                                                                                                                                                   | Done   | Deps  |
| ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ----- |
| F3-01 | **HabitSignalRepository — implementación**  `insert()` append-only. `getSignalsForTask()`. `getSignalsByTimeWindow(hour, day)`. **NUNCA** exponer `update()` ni `delete()`.                                                       | ☑ Done | F0-09 |
| F3-02 | **HabitEngine — logSignal()**  Registra señales desde el ViewModel al completar/ignorar/posponer tareas y reminders. Llamado desde los `onSignal` callbacks del `TaskRenderer`.                                                   | ☑ Done | F3-01 |
| F3-03 | **HabitEngine — calculatePattern()**  Dado un set de señales: calcula `completionRate`, `dismissRate`, `avgDelayMs`, `sampleSize`, `confidence`. `Confidence = min(1.0, sampleSize/20)`.                                          | ☑ Done | F3-02 |
| F3-04 | **HabitAnalysisWorker — WorkManager**  `PeriodicWorkRequest` cada 24hs, constraint: charging + idle (para no consumir batería). Llama `HabitEngine.processBatch()`. En `data/worker/`.                                            | ☑ Done | F3-03 |
| F3-05 | **UserPatternRepository — upsert por (taskType, hour, day)**  Si ya existe el patrón para esa combinación: actualizar. Si no: insertar. Query `getBestPatternForType()` devuelve el horario con mayor `completionRate`.           | ☑ Done | F3-03 |
| F3-06 | **ReminderEngine — calculateOptimalTime()**  Consulta `UserPatternRepository`. Si hay datos con `confidence > 0.5`: usa el mejor horario. Si no: `defaultTimeForType()` según `TaskType`.                                         | ☑ Done | F3-05 |
| F3-07 | **ReminderEngine — SM-2 calculateNextInterval()**  Implementación exacta del algoritmo SM-2 adaptado. `COMPLETED→quality 5`, `SNOOZED→3`, `DISMISSED→1`. `EaseFactor` mínimo: `1.3`.                                              | ☑ Done | F0-04 |
| F3-08 | **ReminderWorker — WorkManager**  `OneTimeWorkRequest` por cada reminder. Al dispararse: muestra notificación con acciones (**Completar / Posponer 30min / Ignorar**). Al responder: llama `ReminderEngine.onReminderResponse()`. | ☑ Done | F3-07 |
| F3-09 | **NotificationService — canales Android**  Canal `REMINDERS` (`importance HIGH`). Canal `SUGGESTIONS` (`importance DEFAULT`). Notificación de reminder con 3 action buttons. Manejo de respuestas via `BroadcastReceiver`.        | ☑ Done | F3-08 |
| F3-10 | **Conectar onSignal del TaskRenderer al HabitEngine**  `TaskDetailViewModel.onSignal()` → `HabitEngine.logSignal()`. Cada interacción del usuario con la tarea genera una señal automáticamente.                                  | ☑ Done | F3-02 |
| F3-11 | **ReminderRepository — implementación completa**  CRUD de reminders. `getActiveRemindersScheduled(before: Long)` para el `ReminderWorker`. `reschedule()` actualiza `scheduled_at`.                                               | ☑ Done | F0-09 |

## Checklist de verificación

1. Al completar una tarea se registra una señal `TASK_COMPLETED` en la DB (verificar con DB Inspector)
2. Al ignorar una notificación se registra `REMINDER_DISMISSED` y el SM-2 reduce el interval
3. Después de 5+ señales, `calculatePattern()` devuelve valores correctos
4. `HabitAnalysisWorker` se ejecuta correctamente en WorkManager (verificar en App Inspection → Background Task Inspector)
5. `calculateOptimalTime()` devuelve el horario con mayor `completionRate` cuando hay datos suficientes
6. La notificación de reminder tiene los 3 botones de acción funcionales
7. SM-2: 3 `DISMISSED` seguidos resetean el interval a 1 día
8. Unit tests de SM-2 cubren: primera repetición, quality 5, quality 1, `easeFactor` mínimo

---

# Fase 4 — Fetcher Engine

| Fase | Nombre         | Duración estimada | Estado |
| ---- | -------------- | ----------------: | ------ |
| F4   | Fetcher Engine |       1–2 semanas | [ ]    |

**Objetivo:** los 3 fetchers del MVP funcionan, los datos se cachean localmente, y el componente `DATA_FEED` los muestra correctamente. Ningún dato de usuario sale sin sanitización.

## Tareas

| ID    | Tarea + detalle                                                                                                                                                                                                                                  | Done   | Deps              |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------ | ----------------- |
| F4-01 | **FetcherEngine — estructura base**  `FetcherEngine` con `fun fetch(type, params): FetchResult`. `FetchResult` sealed: `Success(data)`, `MissingParams`, `NotSupported`, `Error(reason)`. `OkHttpClient` con interceptor de sanitización de PII. | ☐ Done | F2-08             |
| F4-02 | **PII Sanitizer — interceptor OkHttp**  Antes de toda request: eliminar de params cualquier key que sea: `name`, `email`, `phone`, `address`, `id`, `user`, `token`. Log en debug de qué params se enviaron.                                     | ☐ Done | F4-01             |
| F4-03 | **Fetcher: WEATHER**  API: `open-meteo.com` (gratuita, sin key). Params: `latitude`, `longitude`. Response: temperatura actual, descripción, ícono. Cachear resultado en `fetcher_configs.last_result_json`.                                     | ☐ Done | F4-02             |
| F4-04 | **Fetcher: CURRENCY_EXCHANGE**  API: `exchangerate-api.com` (tier free, API key embebida en `BuildConfig`). Params: `from`, `to`. Response: tasa actual, timestamp. Cache: 1 hora.                                                               | ☐ Done | F4-02             |
| F4-05 | **Fetcher: FLIGHT_PRICES**  API: Aviasales API pública (o alternativa sin auth de usuario). Params: `origin`, `destination`, `date_from`, `date_to`. Response: precio mínimo, aerolínea, link de compra.                                         | ☐ Done | F4-02             |
| F4-06 | **FetcherWorker — WorkManager**  `PeriodicWorkRequest` por fetcher activo. Respeta el `cron_expression` de cada `FetcherConfig`. Constraint: `NetworkType.CONNECTED`. Actualiza `last_result_json` en DB.                                        | ☐ Done | F4-03,F4-04,F4-05 |
| F4-07 | **DATA_FEED component — conectar a FetcherEngine**  `DataFeedViewModel` carga `last_result_json` de DB (cache). Refresh manual hace fetch on-demand. Muestra timestamp del último update.                                                        | ☐ Done | F4-06             |
| F4-08 | **Conectar creación de tareas TRAVEL con fetchers**  Cuando el Classifier crea una tarea `TRAVEL` con origen/destino: auto-crear `FetcherConfig` para `FLIGHT_PRICES` y `WEATHER`. El usuario puede desactivarlos.                               | ☐ Done | F4-07             |

## Checklist de verificación

1. `WEATHER` fetcher devuelve temperatura y descripción para Buenos Aires
2. `CURRENCY_EXCHANGE` devuelve tasa USD/ARS correcta
3. `FLIGHT_PRICES` devuelve al menos un resultado para BUE→MAD
4. El interceptor `PII Sanitizer` bloquea params con keys de la blocklist (test con log)
5. `DATA_FEED` muestra estado `stale` con timestamp cuando el fetch falla pero hay cache
6. `FetcherWorker` no corre cuando no hay conexión a internet
7. Crear tarea `"viaje a Madrid"` genera automáticamente los `FetcherConfigs` correctos

> **API KEYS**
> Las API keys de fetchers van en `local.properties` (no en el repo) y se inyectan via `BuildConfig` en el build. Nunca commitear keys al repositorio. Agregar `local.properties` a `.gitignore` desde el inicio.

---

# Fase 5 — Suggestion Engine (Killer Features)

| Fase | Nombre                              | Duración estimada | Estado |
| ---- | ----------------------------------- | ----------------: | ------ |
| F5   | Suggestion Engine — Killer Features |       2–3 semanas | [ ]    |

**Objetivo:** Task Resurrection y Day Rescue funcionan end-to-end. El usuario ve sugerencias con reasoning visible, las aprueba o rechaza, y el sistema aplica o descarta los cambios. Este es el corazón diferenciador del MVP.

## Tareas

| ID    | Tarea + detalle                                                                                                                                                                                                                                                                          | Done   | Deps        |
| ----- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ----------- |
| F5-01 | **SuggestionRepository — implementación completa**  `save()`, `getPendingSuggestions()` como `Flow`, `updateStatus(id, status)`, `deleteExpired()`. Las sugerencias expiradas (`expires_at` pasado) no se muestran aunque estén en DB.                                                   | ☐ Done | F0-09       |
| F5-02 | **SuggestionEngine — evaluación de patrones**  Corre dentro del `HabitAnalysisWorker`. Consulta `UserPatternRepository`. Genera sugerencias `RESCHEDULE_REMINDER` si `dismissRate > 0.6` con `confidence > 0.7`. `SIMPLIFY_TASK` si `completionRate < 0.3`.                              | ☐ Done | F3-04,F5-01 |
| F5-03 | **Task Resurrection — detección**  En `HabitAnalysisWorker`: tareas con status `ACTIVE`, sin señales en los últimos 10 días, y `completionRate` histórico > 0.4 (o sea: el usuario la hacía pero la abandonó). Genera sugerencia `RESURRECT_TASK`.                                       | ☐ Done | F5-02       |
| F5-04 | **Task Resurrection — payload y reasoning**  Payload incluye: `simplify` (bool), `suggestedDuration` (int min), `suggestedReminderTime`. Reasoning debe incluir días sin actividad, horario histórico en que la completaba, y qué cambio específico se propone.                          | ☐ Done | F5-03       |
| F5-05 | **Day Rescue — DayRescueEngine**  Recolecta tareas `ACTIVE` del día, tiempo estimado disponible (restante del día), `user_patterns` del horario actual. Llama `LLMService` con ese contexto para obtener nuevo orden con reasoning.                                                      | ☐ Done | F3-06,F2-06 |
| F5-06 | **Day Rescue — prompt específico**  Prompt separado del System Prompt de creación. Formato: lista de tareas + patrones del usuario + hora actual → JSON con nuevo orden + reasoning por cada movimiento.                                                                                 | ☐ Done | F5-05       |
| F5-07 | **SuggestionCard — componente UI**  Card con: título de la sugerencia, reasoning completo (**SIEMPRE visible, nunca colapsado por defecto**), botones de acción (**Sí / No por ahora / Archivar tarea**). Animación de entrada/salida.                                                   | ☐ Done | F0-11       |
| F5-08 | **SuggestionsScreen / overlay en Home**  Sugerencias pendientes aparecen como banner dismissible en la parte superior de `HomeScreen`. Tap expande a `SuggestionCard` completo. Badge en `BottomNav` cuando hay pendientes.                                                              | ☐ Done | F5-07       |
| F5-09 | **Aplicar sugerencia aprobada — use cases**  `ApplySuggestionUseCase`: según `SuggestionType` ejecuta las Capabilities correspondientes. `RESCHEDULE_REMINDER` → actualiza `Reminder`. `RESURRECT_TASK` → modifica `Task` + crea `Reminder` nuevo. `SIMPLIFY_TASK` → reduce componentes. | ☐ Done | F5-01,F2-09 |
| F5-10 | **Day Rescue — UI completo**  `DayRescueBottomSheet`: muestra lista reorganizada con reasoning por cada tarea movida. Checkbox por ítem para aprobar cambios selectivos. Botón `"Aplicar selección"`.                                                                                    | ☐ Done | F5-06,F5-09 |
| F5-11 | **Botón Day Rescue en HomeScreen**  FAB secundario o acción en el menú `"¿El día se descarriló?"` Disponible desde las 12hs del día (no tiene sentido a las 8am).                                                                                                                        | ☐ Done | F5-10       |
| F5-12 | **Expiración automática de sugerencias**  `SuggestionCleanupWorker`: `PeriodicWork` cada 24hs. Marca como `EXPIRED` las sugerencias con `expires_at < now`. Las sugerencias viven 72 horas por defecto.                                                                                  | ☐ Done | F5-01       |

## Checklist de verificación

1. Una tarea sin actividad por 10 días genera una sugerencia `RESURRECT_TASK` al día siguiente
2. El reasoning de la sugerencia menciona los días sin actividad y el horario histórico
3. Aprobar una sugerencia `RESCHEDULE_REMINDER` actualiza efectivamente el reminder en DB
4. Rechazar una sugerencia registra el rechazo y no vuelve a aparecer por 7 días
5. Day Rescue genera un nuevo orden coherente (deadline hoy = primera prioridad)
6. Day Rescue permite aprobar cambios selectivos (no todo o nada)
7. `SuggestionCard` nunca muestra el reasoning colapsado — siempre visible completo
8. Las sugerencias expiradas no aparecen en la UI aunque estén en DB
9. Unit tests de `SuggestionEngine` cubren: `dismissRate` alto, `completionRate` bajo, tarea abandonada

> **UX CRÍTICO**
> El reasoning de cada sugerencia debe estar escrito en lenguaje natural, primera persona del sistema (`"Notamos que..."`, `"Detectamos que..."`). Nunca mostrar valores numéricos crudos como `"dismissRate: 0.73"`. Siempre traducir a lenguaje humano: `"7 de cada 10 veces ignoraste este recordatorio"`.

---

# Fase 6 — Sincronización

| Fase | Nombre                           | Duración estimada | Estado |
| ---- | -------------------------------- | ----------------: | ------ |
| F6   | Sincronización (opcional en MVP) |         2 semanas | [ ]    |

**Objetivo:** el usuario puede activar sync entre dispositivos de forma opt-in. Los datos viajan E2E encrypted. El servidor (Supabase) nunca ve plaintext de datos sensibles.

> **NOTA**
> Esta fase es la de menor impacto en el MVP para usuario único con un dispositivo. Si el tiempo es limitado, puede postergarse a v1.1 sin afectar las funcionalidades core. La arquitectura ya la soporta via `sync_queue`.

## Tareas

| ID    | Tarea + detalle                                                                                                                                                                                               | Done   | Deps  |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ----- |
| F6-01 | **Supabase project setup**  Crear proyecto en `supabase.com` (o self-hosted). Tablas: `tasks_sync`, `components_sync`. Row Level Security activado. Claves en `local.properties`.                             | ☐ Done | F0-01 |
| F6-02 | **E2E encryption — AES-256**  Generar clave de cifrado por usuario, almacenarla en Android Keystore (nunca en DB ni `SharedPreferences`). Cifrar payload antes de escribir a `sync_queue`. Descifrar al leer. | ☐ Done | F6-01 |
| F6-03 | **SyncWorker — upload de sync_queue**  Lee `sync_queue` donde `synced_at IS NULL`. Cifra payload. POST a Supabase. Marca `synced_at`. Retry automático con exponential backoff.                               | ☐ Done | F6-02 |
| F6-04 | **SyncWorker — download de cambios remotos**  Consulta Supabase por cambios más recientes que `last_sync_at`. Descifra. Aplica CRDT (`last-write-wins` por campo). Persiste en Room.                          | ☐ Done | F6-03 |
| F6-05 | **Settings — toggle de sync**  En `SettingsScreen`: toggle `"Sincronizar entre dispositivos"` (off por defecto). Al activar: muestra aviso de que los datos se cifran antes de salir del dispositivo.         | ☐ Done | F6-04 |

## Checklist de verificación

1. Crear una tarea en dispositivo A aparece en dispositivo B después de sync
2. El payload en Supabase es ilegible (verificar en dashboard de Supabase)
3. Si Supabase no está disponible, la app funciona normalmente (offline first)
4. El toggle de sync está OFF por defecto y requiere acción explícita del usuario

---

# Fase 7 — Polish, Onboarding y Beta

| Fase | Nombre                    | Duración estimada | Estado |
| ---- | ------------------------- | ----------------: | ------ |
| F7   | Polish, Onboarding y Beta |       2–3 semanas | [ ]    |

**Objetivo:** la app está lista para que usuarios reales la prueben. Onboarding funcional, performance verificada, errores manejados, y la app publicada en Play Store internal testing.

## Tareas

| ID    | Tarea + detalle                                                                                                                                                                                                                                     | Done   | Deps  |
| ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ----- |
| F7-01 | **Onboarding — flujo de 3 pasos**  Paso 1: nombre y zona horaria. Paso 2: tipos de tareas que más usa (multi-select). Paso 3: crear la primera tarea guiada. Persiste en `DataStore`. Se muestra solo una vez.                                      | ☐ Done | F2-10 |
| F7-02 | **Onboarding — contextualización del classifier**  Los tipos de tareas seleccionados en onboarding ajustan los pesos del `IntentClassifier` y el contexto del System Prompt.                                                                        | ☐ Done | F7-01 |
| F7-03 | **Android Widgets — home screen widget**  Glance widget con las 3 tareas más urgentes del día. Tap en cada una abre `TaskDetail`. Refresh cada 30 minutos vía `GlanceAppWidgetManager`.                                                             | ☐ Done | F1-15 |
| F7-04 | **Performance audit**  Verificar con Android Profiler: ninguna operación de DB en el main thread. Startup time < 800ms (cold start). Memory leaks con LeakCanary. Recomposiciones con Layout Inspector.                                             | ☐ Done | F1-12 |
| F7-05 | **Error handling global**  Crashlytics o similar para captura de crashes. `UncaughtExceptionHandler`. Todos los `catch` muestran un mensaje al usuario, nunca pantalla en blanco.                                                                   | ☐ Done | F0-01 |
| F7-06 | **Manejo de permisos en runtime**  `POST_NOTIFICATIONS` (Android 13+): solicitado en onboarding con explicación clara. `SCHEDULE_EXACT_ALARM`: solicitado al crear el primer reminder. Sin permisos: la app funciona con recordatorios aproximados. | ☐ Done | F7-01 |
| F7-07 | **Testing de integración — flujos completos**  Test E2E: crear tarea `TRAVEL` → verificar componentes → completar checklist item → verificar señal en DB → verificar reminder programado.                                                           | ☐ Done | F5-12 |
| F7-08 | **Play Store — internal testing track**  Signed APK/AAB. Store listing mínimo (descripción, capturas, ícono). Privacy policy URL. Internal testing con 5-10 usuarios reales.                                                                        | ☐ Done | F7-07 |

## Checklist de verificación — MVP completo

1. El flujo completo crear → ver → completar → suggestion funciona sin crashes en 3 dispositivos distintos
2. Cold start < 800ms medido con Macrobenchmark
3. No hay operaciones de DB en el main thread (verificado con StrictMode en debug)
4. LeakCanary no reporta memory leaks en el flujo principal
5. Los 3 fetchers funcionan en conexión WiFi y datos móviles
6. La app funciona correctamente sin conexión a internet (offline first verificado)
7. Onboarding se muestra solo en el primer launch y nunca más
8. El widget de home screen se actualiza correctamente
9. 5 usuarios beta han creado al menos una tarea sin asistencia

---

## Mapa de Dependencias entre Fases

```text
F0 — Fundamentos
  └─► F1 — Core UI           (necesita: Room, Hilt, Theme)
       └─► F2 — Classifier   (necesita: Domain models, CapabilityRegistry)
            ├─► F3 — Habit   (necesita: Capability.LogSignal, TaskDetailViewModel)
            │    └─► F5 — Suggestions  (necesita: UserPatterns, WorkManager)
            └─► F4 — Fetcher (necesita: CapabilityRegistry, FetcherConfig en DB)
                 └─► F5 — Suggestions  (necesita: DATA_FEED component)

F6 — Sync     puede correr en paralelo con F5 si hay tiempo
F7 — Polish   depende de que F2, F3, F4 y F5 estén completos
```

> **PARALELISMO**
> Dentro de cada fase, algunas tareas son independientes y pueden hacerse en paralelo. Por ejemplo en F1, todos los componentes (`F1-03` a `F1-09`) son independientes entre sí y pueden desarrollarse en cualquier orden.

---

## Decisiones Técnicas Pendientes

Estas decisiones deben tomarse antes o durante las fases indicadas. No bloquean el inicio pero bloquean la tarea específica.

| Decisión                             | Antes de | Opciones                                                                          | Recomendación                                                                   |
| ------------------------------------ | -------- | --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| Modelo TFLite para IntentClassifier  | F2-04    | Entrenar propio / usar reglas regex como placeholder / ML Kit Text Classification | Empezar con reglas regex, reemplazar con TFLite cuando haya datos reales de uso |
| API de vuelos                        | F4-05    | Aviasales API / Skyscanner / Kiwi.com / mockear para MVP                          | Mockear con datos fake para MVP, integrar API real en v1.1                      |
| Nombre final de la app               | F7-08    | AURA (tentativo) / otro                                                           | Definir antes de Play Store listing                                             |
| Self-hosted Supabase vs cloud        | F6-01    | `supabase.com` (cloud) / self-hosted con Docker                                   | Cloud para MVP, migrar a self-hosted si la escala lo requiere                   |
| Gráfico para METRIC_TRACKER          | F1-08    | Vico library / Canvas custom / MPAndroidChart                                     | Vico: más mantenida, soporte Compose nativo                                     |
| LLM on-device para hardware limitado | F2-06    | Gemma 2B via MediaPipe / Groq siempre / `llama.cpp`                               | Groq como default, Gemma 2B como opt-in para usuarios con hardware compatible   |

---

## Quick Reference — Comandos Frecuentes

### Gradle

```bash
# Compilar en modo debug
./gradlew assembleDebug

# Correr todos los unit tests
./gradlew test

# Correr tests de un módulo específico
./gradlew :app:testDebugUnitTest --tests 'com.aura.engine.habit.*'

# Lint
./gradlew lint

# Generar AAB para Play Store
./gradlew bundleRelease
```

### Room — DB Inspector

```sql
-- Ver DB en tiempo real:
-- Android Studio → App Inspection → Database Inspector

-- Query útil para verificar señales de hábito:
SELECT task_id, signal_type, hour_of_day, day_of_week
FROM habit_signals
ORDER BY recorded_at DESC LIMIT 20;

-- Query para verificar patrones calculados:
SELECT task_type, hour_of_day, day_of_week,
       completion_rate, confidence, sample_size
FROM user_patterns
WHERE confidence > 0.5
ORDER BY completion_rate DESC;
```

### WorkManager — Background Task Inspector

```kotlin
// Ver estado de Workers en tiempo real:
// Android Studio → App Inspection → Background Task Inspector

// Forzar ejecución inmediata de un Worker en debug:
val request = OneTimeWorkRequestBuilder<HabitAnalysisWorker>().build()
WorkManager.getInstance(context).enqueue(request)
```

---

**AURA — Plan de Implementación MVP · Uso interno · Marzo 2026**

# AURA

Asistente personal local-first para Android que combina tareas, hábitos, recordatorios inteligentes y automatizaciones contextuales en una sola app.

La idea central de AURA es que la IA no renderiza interfaces ni actúa sola: interpreta la intención del usuario, selecciona `UI-Skills` reutilizables y genera un `Task DSL` estructurado que la app convierte en UI nativa. El sistema sugiere, explica y aprende, pero no modifica datos sin aprobación explícita.

## Qué problema intenta resolver

Las apps de productividad tradicionales suelen obligar al usuario a adaptarse a una estructura fija:

- recordatorios rígidos
- interfaces genéricas para cualquier tipo de tarea
- cero aprendizaje del comportamiento real del usuario
- fragmentación entre varias apps
- demasiado setup manual

AURA apunta a resolver eso con una experiencia más contextual, más visual y más automatizable, manteniendo los datos en el dispositivo.

## Principios del proyecto

- `Local-first`: los datos viven en el dispositivo y la app debe seguir funcionando offline.
- `Suggest, never act`: la IA propone, no ejecuta cambios sin confirmación.
- `Event-driven`: sin polling continuo; el sistema reacciona a acciones del usuario y trabajos programados.
- `Privacy by design`: nada sensible debería salir del dispositivo sin protección adecuada.
- `Explainability`: toda sugerencia debe poder explicarse.

## Cómo funciona

El flujo conceptual del MVP es este:

1. El usuario crea una tarea a partir de texto o voz.
2. Un pipeline clasifica la intención y extrae entidades.
3. El sistema genera un `Task DSL` estructurado.
4. Un `UI-Skill Registry` valida las skills elegidas y normaliza su configuración.
5. La app renderiza esa definición como UI nativa mediante un `SkillRendererRegistry`.
6. Los eventos del usuario alimentan un motor de hábitos y sugerencias.

El modelo conceptual de `UI-Skills` sigue el espíritu de Google AI Edge Gallery: cada skill tiene nombre, descripción e instrucciones declarativas (`SKILL.md`) para que distintos LLMs puedan descubrirla y configurarla sin depender de un prompt monolítico.

Ejemplos de UI-Skills soportadas en la visión actual del producto:

- `CHECKLIST`
- `COUNTDOWN`
- `PROGRESS_BAR`
- `HABIT_RING`
- `NOTES`
- `METRIC_TRACKER`
- `DATA_FEED`

## Estado actual

El repositorio ya incluye una base funcional del proyecto Android:

- app nativa con `Jetpack Compose`
- navegación principal entre Home, creación de tarea, detalle y ajustes
- persistencia local con `Room`
- inyección de dependencias con `Hilt`
- trabajos en segundo plano con `WorkManager`
- serialización con `kotlinx.serialization`
- piezas iniciales del motor de clasificación y construcción de `Task DSL`
- workflow de CI en [`.github/workflows/ci.yml`](/Users/theo/AndroidStudioProjects/Aura/.github/workflows/ci.yml)

Hoy el proyecto está más cerca de una base de arquitectura para el MVP que de un producto terminado. La visión detallada del alcance está documentada en [MVP.md](/Users/theo/AndroidStudioProjects/Aura/MVP.md).

## Stack técnico

- Kotlin
- Android SDK 36
- Jetpack Compose + Material 3
- Navigation Compose
- Hilt
- Room
- WorkManager
- DataStore
- Retrofit + OkHttp
- ML Kit Entity Extraction

Configuración actual de Android:

- `minSdk = 30`
- `targetSdk = 36`
- `applicationId = com.theveloper.aura`

## Estructura general

```text
app/src/main/java/com/theveloper/aura/
├── data/
├── di/
├── domain/
├── engine/
└── ui/
```

Capas principales:

- `ui`: pantallas, navegación y renderizado de UI-Skills
- `domain`: modelos y contratos del negocio
- `data`: base local, DAOs, entidades y repositorios
- `engine`: clasificación, DSL y lógica de composición

## Cómo abrir y ejecutar

### Requisitos

- Android Studio reciente
- JDK 11
- Android SDK 36

### Pasos

1. Abrir el proyecto en Android Studio.
2. Esperar la sincronización de Gradle.
3. Ejecutar la app sobre un emulador o dispositivo con Android 11+.

Si preferís línea de comandos:

```bash
./gradlew assembleDebug
```

## Próximos hitos naturales

- completar el flujo de creación de tareas desde lenguaje natural
- consolidar el `Task DSL` y las `UI-Skills` como contrato estable entre modelos y renderer
- conectar señales de uso con sugerencias y replanificación
- implementar recordatorios adaptativos y aprendizaje real de hábitos
- ampliar el catálogo de UI-Skills y su renderizado

## Documentación relacionada

- [MVP.md](/Users/theo/AndroidStudioProjects/Aura/MVP.md): visión, arquitectura y alcance del MVP
- [IMPL_PLAN.md](/Users/theo/AndroidStudioProjects/Aura/IMPL_PLAN.md): plan de implementación

## Licencia

Todavía no hay una licencia definida en el repositorio.

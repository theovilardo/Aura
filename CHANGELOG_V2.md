# Aura v2 — Multi-Creation-Type System

## Changelog for Desktop Team Integration

**Date:** 2026-03-29
**Protocol Version:** 3 (backward-compatible with v2)

---

## Overview

Aura now supports 5 distinct creation types from the mobile bottom bar:

| Type | Description | Persistence | Scheduling |
|------|-------------|-------------|------------|
| **TASK** | Existing flow (unchanged) — prompt/manual UI creation with components | `tasks` table | Existing ReminderWorker |
| **REMINDER** | Standalone reminders — one-time, repeating, cyclical | `aura_reminders` + `reminder_checklist_items` | AuraReminderWorker + CronDispatcher |
| **AUTOMATION** | LLM-driven scheduled processes with execution plans | `aura_automations` | AutomationWorker + CronDispatcher |
| **EVENT** | Time-span entities with sub-actions during the period | `aura_events` + `event_sub_actions` + `event_components` | EventLifecycleWorker + EventSubActionWorker |
| **SYSTEM** | Hub panel for managing all of the above | No new persistence | N/A |

---

## Protocol v3 Changes

### Backward Compatibility

v3 is **fully backward-compatible** with v2:
- `ignoreUnknownKeys = true` is already set in `ProtocolSerializer`
- Desktop clients running v2 will silently ignore unknown `MessageType` values
- Default `protocolVersion` bumped from `2` to `3` in `DeviceCapabilityReport`

### New MessageType Values

```kotlin
enum class MessageType {
    // ... existing v2 values ...
    SYNC_ITEM,            // Sync any creation type across devices
    AUTOMATION_EXEC,      // Phone requests desktop to execute an automation step
    AUTOMATION_EXEC_RESULT // Desktop returns automation step result
}
```

### New MessagePayload Subclasses

#### SyncItemPayload (bidirectional)
```json
{
  "payloadType": "sync_item",
  "itemType": "REMINDER",       // "TASK" | "REMINDER" | "AUTOMATION" | "EVENT"
  "itemId": "uuid-here",
  "operation": "CREATE",        // "CREATE" | "UPDATE" | "DELETE"
  "payload": { /* full entity JSON */ }
}
```

**Desktop action required:**
1. Register `sync_item` in your deserializer
2. On `CREATE`/`UPDATE`: persist the entity in local storage
3. On `DELETE`: remove the entity from local storage
4. When desktop creates/modifies items, send `SYNC_ITEM` back to phone

#### AutomationExecRequest (Phone -> Desktop)
```json
{
  "payloadType": "automation_exec",
  "automationId": "uuid",
  "step": {
    "type": "LLM_PROCESS",
    "description": "Summarize pending tasks",
    "params": { "instruction": "..." }
  },
  "context": {
    "tasks": "- [ACTIVE] Buy groceries (GENERAL)\n- ..."
  }
}
```

**Desktop action required:**
1. If step type is `LLM_PROCESS`, use Ollama to complete the prompt
2. Return `AutomationExecResult` with the LLM output

#### AutomationExecResult (Desktop -> Phone)
```json
{
  "payloadType": "automation_exec_result",
  "automationId": "uuid",
  "success": true,
  "result": { "text": "Here's your weekly summary..." },
  "error": null
}
```

---

## New Entity Schemas (JSON format for sync)

### AuraReminder
```json
{
  "id": "uuid",
  "title": "Go to the gym",
  "body": "Don't forget to stretch first",
  "reminder_type": "CYCLICAL",
  "scheduled_at": 1743260400000,
  "repeat_count": 0,
  "interval_ms": 604800000,
  "cron_expression": "0 8 * * 1",
  "linked_task_id": "task-uuid-or-null",
  "links": ["https://..."],
  "checklist_items": [
    { "id": "uuid", "text": "Pack gym bag", "is_completed": false, "sort_order": 0 }
  ],
  "status": "PENDING",
  "created_at": 1743260400000,
  "updated_at": 1743260400000
}
```

**Enum values:**
- `reminder_type`: `ONE_TIME`, `REPEATING`, `CYCLICAL`
- `status`: `PENDING`, `TRIGGERED`, `COMPLETED`, `CANCELLED`

### AuraAutomation
```json
{
  "id": "uuid",
  "title": "Weekly task summary",
  "prompt": "Every Monday summarize what I have pending this week",
  "cron_expression": "0 9 * * 1",
  "execution_plan": {
    "steps": [
      {
        "type": "GATHER_CONTEXT",
        "description": "Collect active tasks due this week",
        "params": { "query": "active_tasks_due_this_week" }
      },
      {
        "type": "LLM_PROCESS",
        "description": "Summarize into priorities",
        "params": { "instruction": "Summarize pending tasks by priority" }
      },
      {
        "type": "OUTPUT",
        "description": "Send as notification",
        "params": { "format": "notification" }
      }
    ]
  },
  "output_type": "NOTIFICATION",
  "last_execution_at": null,
  "last_result_json": null,
  "status": "ACTIVE",
  "failure_count": 0,
  "max_retries": 3,
  "created_at": 1743260400000,
  "updated_at": 1743260400000
}
```

**Enum values:**
- `status`: `ACTIVE`, `PAUSED`, `FAILED`, `COMPLETED`
- `output_type`: `NOTIFICATION`, `TASK_UPDATE`, `SUMMARY`, `CUSTOM`
- `step.type`: `GATHER_CONTEXT`, `LLM_PROCESS`, `OUTPUT`

### AuraEvent
```json
{
  "id": "uuid",
  "title": "Lollapalooza 2026",
  "description": "3-day music festival",
  "start_at": 1743260400000,
  "end_at": 1743519600000,
  "sub_actions": [
    {
      "id": "uuid",
      "event_id": "parent-uuid",
      "type": "METRIC_PROMPT",
      "title": "Spending tracker",
      "cron_expression": "",
      "interval_ms": 14400000,
      "prompt": "How much have you spent so far?",
      "config": {},
      "enabled": true
    }
  ],
  "components": [
    {
      "id": "uuid",
      "type": "METRIC_TRACKER",
      "sort_order": 0,
      "config": { "unit": "USD", "label": "Total spent", "goal": 500.0 }
    }
  ],
  "status": "UPCOMING",
  "created_at": 1743260400000,
  "updated_at": 1743260400000
}
```

**Enum values:**
- `status`: `UPCOMING`, `ACTIVE`, `COMPLETED`
- `sub_action.type`: `NOTIFICATION`, `METRIC_PROMPT`, `AUTOMATION`, `CHECKLIST_REMIND`

---

## Database Schema (v5 migration)

### New Tables

```sql
-- Standalone reminders
CREATE TABLE aura_reminders (
    id TEXT NOT NULL PRIMARY KEY,
    title TEXT NOT NULL,
    body TEXT NOT NULL DEFAULT '',
    reminder_type TEXT NOT NULL,         -- ONE_TIME | REPEATING | CYCLICAL
    scheduled_at INTEGER NOT NULL,
    repeat_count INTEGER NOT NULL DEFAULT 0,
    interval_ms INTEGER NOT NULL DEFAULT 0,
    cron_expression TEXT NOT NULL DEFAULT '',
    linked_task_id TEXT,                 -- FK to tasks.id (nullable)
    links TEXT NOT NULL DEFAULT '[]',    -- JSON array of URLs
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Checklist items within reminders
CREATE TABLE reminder_checklist_items (
    id TEXT NOT NULL PRIMARY KEY,
    reminder_id TEXT NOT NULL,
    text TEXT NOT NULL,
    is_completed INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL,
    FOREIGN KEY(reminder_id) REFERENCES aura_reminders(id) ON DELETE CASCADE
);

-- LLM-driven automations
CREATE TABLE aura_automations (
    id TEXT NOT NULL PRIMARY KEY,
    title TEXT NOT NULL,
    prompt TEXT NOT NULL,
    cron_expression TEXT NOT NULL,
    execution_plan TEXT NOT NULL,         -- JSON AutomationExecutionPlan
    output_type TEXT NOT NULL DEFAULT 'NOTIFICATION',
    last_execution_at INTEGER,
    last_result_json TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    failure_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Time-span events
CREATE TABLE aura_events (
    id TEXT NOT NULL PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    start_at INTEGER NOT NULL,
    end_at INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'UPCOMING',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Actions that run during active events
CREATE TABLE event_sub_actions (
    id TEXT NOT NULL PRIMARY KEY,
    event_id TEXT NOT NULL,
    type TEXT NOT NULL,
    title TEXT NOT NULL DEFAULT '',
    cron_expression TEXT NOT NULL DEFAULT '',
    interval_ms INTEGER NOT NULL DEFAULT 0,
    prompt TEXT NOT NULL DEFAULT '',
    config TEXT NOT NULL DEFAULT '{}',
    enabled INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY(event_id) REFERENCES aura_events(id) ON DELETE CASCADE
);

-- Links events to reusable task components
CREATE TABLE event_components (
    id TEXT NOT NULL PRIMARY KEY,
    event_id TEXT NOT NULL,
    component_id TEXT NOT NULL,          -- FK to task_components.id
    FOREIGN KEY(event_id) REFERENCES aura_events(id) ON DELETE CASCADE
);
```

### Indexes
```sql
CREATE INDEX index_aura_reminders_linked_task_id ON aura_reminders(linked_task_id);
CREATE INDEX index_reminder_checklist_items_reminder_id ON reminder_checklist_items(reminder_id);
CREATE INDEX index_event_sub_actions_event_id ON event_sub_actions(event_id);
CREATE INDEX index_event_components_event_id ON event_components(event_id);
```

---

## Architecture Overview

### Classification Pipeline

```
User selects creation type in bottom bar
       |
  CreationTypeClassifier
       |
       +-- TASK       --> TaskClassifier (existing, untouched)
       +-- REMINDER   --> ReminderClassifier
       +-- AUTOMATION --> AutomationClassifier
       +-- EVENT      --> EventClassifier
       +-- SYSTEM     --> SystemPanelScreen (no classification)
```

Each sub-classifier:
1. Extracts entities (dates, times, numbers) via `EntityExtractorService`
2. Attempts LLM classification (Groq, local LiteRT, or desktop Ollama)
3. Falls back to heuristic rules if LLM unavailable
4. Returns typed DSL output

### Scheduling & Execution

```
CronDispatcherWorker (every 15 min)
       |
       +-- evaluates cron expressions for active automations
       |   +-- enqueues AutomationWorker (GATHER -> LLM -> OUTPUT)
       |
       +-- checks due standalone reminders
           +-- enqueues AuraReminderWorker

EventLifecycleWorker (daily)
       |
       +-- UPCOMING -> ACTIVE transition
       |   +-- dispatches EventSubActionWorker
       |
       +-- ACTIVE -> COMPLETED transition
```

### Automation Execution Flow (with Desktop Ollama)

```
1. CronDispatcherWorker fires
2. AutomationWorker starts
3. GATHER_CONTEXT: queries local DB
4. LLM_PROCESS: if ExecutionRouter targets REMOTE_DESKTOP:
   a. Send AutomationExecRequest via WebSocket
   b. Desktop runs Ollama completion
   c. Desktop returns AutomationExecResult
5. OUTPUT: show notification with result
6. Store result in aura_automations.last_result_json
```

---

## New Enums Reference

```kotlin
enum class CreationType { SYSTEM, REMINDER, AUTOMATION, EVENT, TASK }
enum class ReminderType { ONE_TIME, REPEATING, CYCLICAL }
enum class ReminderStatus { PENDING, TRIGGERED, COMPLETED, CANCELLED }
enum class AutomationStatus { ACTIVE, PAUSED, FAILED, COMPLETED }
enum class EventStatus { UPCOMING, ACTIVE, COMPLETED }
enum class EventSubActionType { NOTIFICATION, METRIC_PROMPT, AUTOMATION, CHECKLIST_REMIND }
enum class AutomationOutputType { NOTIFICATION, TASK_UPDATE, SUMMARY, CUSTOM }
enum class AutomationStepType { GATHER_CONTEXT, LLM_PROCESS, OUTPUT }
```

---

## Files Changed / Created

### New Files (31)

**Domain Layer:**
- `domain/repository/AuraReminderRepository.kt`
- `domain/repository/AutomationRepository.kt`
- `domain/repository/AuraEventRepository.kt`
- `domain/usecase/CreateReminderUseCase.kt`
- `domain/usecase/CreateAutomationUseCase.kt`
- `domain/usecase/CreateEventUseCase.kt`

**Data Layer:**
- `data/repository/AuraReminderRepositoryImpl.kt`
- `data/repository/AutomationRepositoryImpl.kt`
- `data/repository/AuraEventRepositoryImpl.kt`
- `data/worker/AuraReminderWorker.kt`
- `data/worker/AutomationWorker.kt`
- `data/worker/CronDispatcherWorker.kt`
- `data/worker/EventLifecycleWorker.kt`
- `data/worker/EventSubActionWorker.kt`

**Engine Layer:**
- `engine/classifier/CreationTypeClassifier.kt`
- `engine/classifier/ReminderClassifier.kt`
- `engine/classifier/AutomationClassifier.kt`
- `engine/classifier/EventClassifier.kt`

**UI Layer:**
- `ui/screen/CreateReminderScreen.kt`
- `ui/screen/CreateReminderViewModel.kt`
- `ui/screen/CreateAutomationScreen.kt`
- `ui/screen/CreateAutomationViewModel.kt`
- `ui/screen/CreateEventScreen.kt`
- `ui/screen/CreateEventViewModel.kt`
- `ui/screen/SystemPanelScreen.kt`
- `ui/screen/SystemPanelViewModel.kt`

### Modified Files (12)

| File | Changes |
|------|---------|
| `domain/model/Enums.kt` | +8 new enums (CreationType, ReminderType, etc.) |
| `domain/model/Models.kt` | +6 new domain models (AuraReminder, AuraAutomation, AuraEvent, etc.) |
| `domain/repository/TaskRepository.kt` | +1 method: `getAllTasks()` |
| `engine/dsl/TaskDSL.kt` | +5 DSL output types + CreationDSLResult sealed interface |
| `engine/capability/CapabilityRegistry.kt` | +3 CapabilityRequest/Response variants |
| `data/db/Entities.kt` | +6 new entity classes |
| `data/db/Daos.kt` | +7 new DAOs + 2 relation classes |
| `data/db/AuraDatabase.kt` | Version 4->5, +6 entities, +6 DAO getters |
| `data/mapper/Mappers.kt` | +12 mapper functions for new types |
| `data/repository/TaskRepositoryImpl.kt` | +`getAllTasks()` implementation |
| `data/repository/FakeTaskRepository.kt` | +`getAllTasks()` implementation |
| `di/DatabaseModule.kt` | +MIGRATION_4_5, +6 DAO providers |
| `di/RepositoryModule.kt` | +3 repository bindings |
| `ui/AuraApp.kt` | +10 route constants, +4 composable entries, routing by creation type |
| `protocol/.../EcosystemMessages.kt` | +3 MessageType values, +3 payload classes, protocol v3 |
| `protocol/.../ProtocolSerializer.kt` | +3 subclass registrations |

---

## Desktop Integration Checklist

- [ ] Register new `MessagePayload` subclasses: `SyncItemPayload`, `AutomationExecRequest`, `AutomationExecResult`
- [ ] Handle `SYNC_ITEM` messages: persist/update/delete REMINDER, AUTOMATION, EVENT entities
- [ ] Handle `AUTOMATION_EXEC` messages: execute LLM step via Ollama, return `AUTOMATION_EXEC_RESULT`
- [ ] Implement local storage for new entity types (match JSON schemas above)
- [ ] Accept `protocolVersion = 3` in capability handshake
- [ ] Emit `SYNC_ITEM` when desktop creates/modifies items locally
- [ ] Display reminders, automations, events in desktop UI
- [ ] Implement cron evaluation for locally-managed automations
- [ ] Support `AutomationExecRequest` forwarding to Ollama API

---

## Migration Notes

- **Existing paired devices** will continue working — v3 is backward-compatible
- **Existing tasks** are untouched — all changes are additive (new tables only)
- **No breaking changes** to the WebSocket protocol or existing message types
- Desktop clients should upgrade to v3 when convenient; v2 will continue to work for all existing functionality

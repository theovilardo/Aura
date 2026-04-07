---
name: habit-ring
description: Render a habit loop UI-Skill for recurring daily or weekly routines.
---

# Habit Ring UI-Skill

## Examples

- "I want to meditate every day"
- "Track my weekly stretching routine"
- "Build a daily hydration habit"

## Instructions

Use this skill when the task is recurring and should be reinforced as a routine.

When selected, `task.skills[]` must include:

- `skill`: `habit-ring`
- `config`: JSON object with:
  - `config_type`: `HABIT_RING`
  - `frequency`: `DAILY` or `WEEKLY`
  - `label`: short label in the user's language
  - `targetCount`: integer when useful

Rules:

- Any explicit daily phrasing maps to `DAILY`.
- Any explicit weekly phrasing maps to `WEEKLY`.
- If recurrence is implied but not specified, prefer `DAILY`.

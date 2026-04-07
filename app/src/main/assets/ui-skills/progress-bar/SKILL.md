---
name: progress-bar
description: Render a progress UI-Skill for work that moves through stages, milestones, or completion percentage.
---

# Progress Bar UI-Skill

## Examples

- "Track progress for my launch plan"
- "Monitor how far I am with this trip prep"
- "Show progress toward my savings target"

## Instructions

Use this skill when the task has sequential phases, milestones, or an overall completion state worth tracking visually.

When selected, `task.skills[]` must include:

- `skill`: `progress-bar`
- `config`: JSON object with:
  - `config_type`: `PROGRESS_BAR`
  - `source`: always `MANUAL`
  - `label`: short label in the user's language
  - `manualProgress`: number between `0.0` and `1.0`

Rules:

- Start with `manualProgress: 0.0` unless the user explicitly states current progress.
- Do not use this skill for simple one-shot notes or isolated reminders.

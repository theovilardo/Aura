---
name: metric-tracker
description: Render a metric tracking UI-Skill for numeric goals such as weight, money, distance, reps, or time.
---

# Metric Tracker UI-Skill

## Examples

- "Save 500 USD for my trip"
- "Run 5 km this month"
- "Track my weight and target"

## Instructions

Use this skill when the user mentions a measurable target or a number that should be tracked over time.

When selected, `task.skills[]` must include:

- `skill`: `metric-tracker`
- `config`: JSON object with:
  - `config_type`: `METRIC_TRACKER`
  - `unit`: precise unit in the user's language or notation
  - `label`: short label in the user's language
  - `history`: always `[]` at creation time
  - `goal`: numeric goal when available

Rules:

- Match the unit exactly to the context: `kg`, `km`, `USD`, `ARS`, `reps`, `h`, and so on.
- Omit `goal` only when the user did not mention a numeric target.

---
name: countdown
description: Render a countdown UI-Skill for a due date, deadline, event date, or future target.
---

# Countdown UI-Skill

## Examples

- "Remind me that rent is due on Friday"
- "Count down to my flight in August"
- "Track the deadline for my application"

## Instructions

Use this skill when the task depends on a concrete future date or deadline.

When selected, `task.skills[]` must include:

- `skill`: `countdown`
- `config`: JSON object with:
  - `config_type`: `COUNTDOWN`
  - `label`: short label in the user's language
  - `targetDate`: epoch milliseconds

Rules:

- Use an extracted date when available.
- If the date is missing or ambiguous, set `targetDate: 0` and `needsClarification: true`.

---
name: checklist
description: Render a checklist UI-Skill with concrete items, steps, ingredients, or packing lists.
---

# Checklist UI-Skill

## Examples

- "Buy milk, bread and tomatoes"
- "Create a packing list for my trip"
- "Break this project into clear next steps"

## Instructions

Use this skill when the user needs a list of concrete things to do, bring, buy, prepare, or review.

When selected, `task.skills[]` must include:

- `skill`: `checklist`
- `config`: JSON object with:
  - `config_type`: `CHECKLIST`
  - `label`: short label in the user's language
  - `allowAddItems`: boolean
  - `items`: array of objects with:
    - `label`: string
    - `isSuggested`: boolean

Rules:

- `items` must contain the real items extracted from the prompt.
- Add inferred extras only when they clearly help, and mark them with `isSuggested: true`.
- If the checklist cannot be built yet, return `items: []` and set `needsClarification: true`.

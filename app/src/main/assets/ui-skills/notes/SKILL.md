---
name: notes
description: Render a rich notes UI-Skill with context, instructions, agenda, or supporting information.
---

# Notes UI-Skill

## Examples

- "Summarize the plan and key decisions"
- "Keep the recipe steps and cooking notes"
- "Prepare an agenda for the meeting"

## Instructions

Use this skill when the task benefits from structured context, explanation, preparation notes, or markdown guidance.

When selected, `task.skills[]` must include:

- `skill`: `notes`
- `config`: JSON object with:
  - `config_type`: `NOTES`
  - `text`: markdown in the user's language
  - `isMarkdown`: always `true`

Rules:

- `text` must contain useful content derived from the prompt, never a placeholder.
- Use markdown structure when it helps: `##` headings, `-` lists, and `**bold**` highlights.

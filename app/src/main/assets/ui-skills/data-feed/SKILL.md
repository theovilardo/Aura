---
name: data-feed
description: Render a live data UI-Skill backed by an external fetcher such as weather, exchange rates, or flight prices.
---

# Data Feed UI-Skill

## Examples

- "Show me the weather for Madrid during the trip"
- "Track the exchange rate from USD to EUR"
- "Watch flight prices for this route"

## Instructions

Use this skill only when the user explicitly needs live external data.

When selected, `task.skills[]` must include:

- `skill`: `data-feed`
- `config`: JSON object with:
  - `config_type`: `DATA_FEED`
  - `fetcherConfigId`: string
  - `displayLabel`: short label in the user's language
  - `status`: always `LOADING`

Rules:

- Do not use this skill for static notes or user-entered values.
- Only select it when the value depends on fresh external information.

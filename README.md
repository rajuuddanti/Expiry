# Mahalaxmi Expiry Manager

Android app for recording product expiry checks across Mahalaxmi Mahamart stores.

## New chat / developer handoff

**Start with `docs/CURRENT_HANDOFF.md`, then read `docs/NEXT_CHAT_HANDOFF.md`.** These are the quickest routes to understand the latest recorded project state and resume work without relying on an old chat.

## Documentation index

### Start and resume
- [Current project handoff](docs/CURRENT_HANDOFF.md) — current decisions, branch context, risks, and next steps.
- [Next-chat handoff](docs/NEXT_CHAT_HANDOFF.md) — copy-ready instructions for a fresh ChatGPT chat.
- [Project context](docs/PROJECT_CONTEXT.md) — purpose, requirements, and core app behavior.
- [Implementation status](docs/IMPLEMENTATION_STATUS.md) — implemented versus not yet verified.
- [Implementation plan](docs/IMPLEMENTATION_PLAN.md) — planned work and checklist.
- [Feature workflow handoff](docs/FEATURE_WORKFLOW_HANDOFF.md) — branch/workflow notes.
- [Requirements handoff](docs/REQUIREMENTS_HANDOFF_2026-10-26.md) — detailed requirements captured for handoff.

### Data and backend
- [Data mapping and import](docs/DATA_MAPPING_AND_IMPORT.md) — SKU/barcode mapping, normalization, and first-match rules.
- [Schema reference](docs/SCHEMA_REFERENCE.md) — table and field reference.
- [SQL reference](docs/SQL_REFERENCE.md) — SQL notes; verify against the current backend before running.
- [Supabase schema handoff](docs/SUPABASE_SCHEMA_HANDOFF.md) — recorded Supabase schema decisions.
- [Supabase Android setup](docs/SUPABASE_ANDROID_SETUP.md) — app configuration and setup notes.
- [Expiry upload security plan](docs/EXPIRY_UPLOAD_SECURITY_PLAN.md) — security considerations for upload workflows.
- [Product master daily sync](docs/PRODUCT_MASTER_DAILY_SYNC.md) — historical sync design; verify it against current code and decisions before relying on it.

### Business rules and history
- [Expiry return rules and schedule](docs/EXPIRY_RETURN_RULES_AND_SCHEDULE.md) — category calendar, return windows, and brand rules.
- [Decisions and changelog](docs/DECISIONS_AND_CHANGELOG.md) — confirmed decisions and dated changes.
- [Implementation log — 2026-10-26](docs/IMPLEMENTATION_LOG_2026-10-26.md)
- [Implementation log — 2026-10-29](docs/IMPLEMENTATION_LOG_2026-10-29.md)
- [Implementation log — 2026-10-31](docs/IMPLEMENTATION_LOG_2026-10-31.md)

## Important project guardrails

- Verify the actual branch, commit, and source before changing code; branches may diverge.
- Do not change Supabase URL/key configuration unless explicitly requested.
- Never place a Supabase service-role key in the Android app.
- A commit or a GitHub Actions run starting is not proof of a successful build or device test. Record the actual result.
- Some documentation captures historical plans or states. If files conflict, inspect current code and the latest explicit user decision, then update the handoff/changelog so the conflict is visible and resolved.
- Do not claim live Supabase data was inspected unless a connected tool actually verified it.

## Current branch note

Recent work is on `feature/new-expiry-upload-table`. Latest documented UI fix: the main-screen heading uses today's actual scheduled category names; it should not show an extra literal “Present Day Categories” label. See [the 2026-10-29 implementation log](docs/IMPLEMENTATION_LOG_2026-10-29.md).

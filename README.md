# Mahalaxmi Expiry Manager

Android expiry-date capture app for Mahalaxmi Mahamart stores.

## Start here

For any new ChatGPT session or developer handoff, read:

- [Current handoff](docs/CURRENT_HANDOFF.md) — latest decisions, reported backend state, branch context and next steps.
- [Project context](docs/PROJECT_CONTEXT.md) — app purpose and core requirements.
- [Implementation status](docs/IMPLEMENTATION_STATUS.md) — distinguish implemented code from unverified work.
- [Data mapping and import](docs/DATA_MAPPING_AND_IMPORT.md) — stacked SKU/barcode source, normalization and FCFS rules.
- [Return rules and schedule](docs/EXPIRY_RETURN_RULES_AND_SCHEDULE.md) — single return-rules table, brand overrides, popup and schedule continuation requirements.
- [Next-chat handoff](docs/NEXT_CHAT_HANDOFF.md) — copy-ready continuation instructions.
- [Decisions and changelog](docs/DECISIONS_AND_CHANGELOG.md) — user decisions and history.
- [Implementation plan](docs/IMPLEMENTATION_PLAN.md) — outstanding checklist.
- [SQL reference](docs/SQL_REFERENCE.md) — historical product-master staging SQL; verify it against the current project before running.
- [Product master daily sync](docs/PRODUCT_MASTER_DAILY_SYNC.md) — historical incremental-sync design; check against the latest local-only expiry-entry decision before using.

## Current data direction

Latest reported Supabase business tables:
1. `public.MASTER` — product master with `"SkuCode"`, `"SkuLongName"`, `"Catg"`.
2. `public.expiry_return_rules` — category and brand return rules in one table.

The user-reported migration removed obsolete cloud-upload/device/security tables. Expiry entries are intended to remain on the device in Room and be exported/shared as CSV. Do not reintroduce cloud expiry uploads unless the user explicitly changes this decision.

The older `product_master` / `expiry_entries` and daily delta-sync documents describe an earlier project direction and may not match the latest implementation/backend. Read the Current Handoff first and verify actual branch/source before relying on old docs or SQL.

## Key behavior

- Scan either SKU or barcode/EAN.
- Normalize digit-only codes shorter than 5 digits by left-padding to 5; preserve longer codes and all codes as text.
- For duplicate product names, use the first SKU in original source order (FCFS).
- Keep the raw scanned code separate from the canonical SKU.
- Cache product master locally for offline lookup.
- Store expiry records locally; export/share CSV.
- Store session rollover is 3 PM local time.
- Return Rules popup is a requested feature: show today's scheduled categories and their return windows, plus Himalaya (60 days) and Kellogg's (30 days).
- Existing category schedule currently covers 23 Oct 2026 through 31 Dec 2027; future continuation must preserve the exact rotation and never schedule more than two categories per day.

## Security and verification

- Do not change Supabase config or credentials unless explicitly requested.
- Never ship a service-role key in the APK.
- A commit does not prove a successful build or runtime test. State build/test status only when verified.

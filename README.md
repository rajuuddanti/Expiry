# Mahalaxmi Expiry Manager

Android expiry-date capture app for Mahalaxmi Mahamart stores.

## Start here

Read these documents before making changes, especially when continuing in a new chat:

- [Project context](docs/PROJECT_CONTEXT.md) — purpose, architecture, requirements and current status.
- [Next-chat handoff](docs/NEXT_CHAT_HANDOFF.md) — current task and exact next steps.
- [Data mapping and import](docs/DATA_MAPPING_AND_IMPORT.md) — stacked SKU/EAN CSV rules, padding, FCFS matching and import validation.
- [SQL reference](docs/SQL_REFERENCE.md) — diagnostic SQL and safe import workflow.
- [Decisions and changelog](docs/DECISIONS_AND_CHANGELOG.md) — user decisions and conversation history.
- [Implementation plan](docs/IMPLEMENTATION_PLAN.md) — feature checklist.

## Production data tables

Exactly two production business tables are intended:

1. `product_master` — every scannable SKU or EAN code maps to a canonical SKU code and product name.
2. `expiry_entries` — store expiry records.

`product_master_staging` is a temporary import helper, not a third production business table. No category table or category field is required.

## Master lookup behavior

The source file has two columns: `SkuCode/EAN` and `SkuLongName`. SKU and EAN rows are stacked vertically in one code column. The user wants the app to search one destination field, `scan_code`, for either type of code.

- Store codes as text to preserve leading zeros.
- If a code has fewer than 5 digits, left-pad it to 5 digits. Leave codes with 5 or more digits unchanged.
- SKU rows map their normalized code to both `scan_code` and `sku_code`.
- EAN rows map their normalized code to `scan_code`; find the canonical SKU by matching product name against the SKU section.
- If multiple SKU rows have the same name, use the first one in original source order (FCFS), per the user's explicit decision.
- Verify the boundary between the SKU section and EAN section using staging data; the user estimates the first 28–30K rows contain SKU codes.
- Do not import the original two-column CSV directly into the three-required-column destination table.

## Manager workflow

- Select a store name; save locally and allow changes in Settings.
- Scan barcode or enter code manually.
- Auto-fill SKU code, SKU name and EAN where known.
- Enter quantity and expiry date using a calendar picker.
- Save locally first, then sync to Supabase when online.
- Corrections/deletions should be soft deletes; no physical delete of expiry records.
- Visible history follows a 3 PM to next-day 3 PM session in Asia/Kolkata.
- Server exports/clearing must not reset local session logic or delete local unsynced records.

## Proposed stack

Kotlin Android, CameraX/ML Kit barcode scanning, Room local storage and sync queue, Supabase Postgres.

## Security

Do not ship a Supabase service-role key in the Android app. Configure a controlled read/write path and Row Level Security before production. Store selection alone is not authentication.

## Current status

Requirements and initial SQL exist. The repository README previously stated the Android app is not yet buildable; inspect the current source tree and run a real build before changing that status.

The user reports the master CSV import into `product_master_staging` is complete. The assistant is **not connected** to the user's Supabase project and must provide SQL for the user to run and review the returned results. See [Next-chat handoff](docs/NEXT_CHAT_HANDOFF.md).

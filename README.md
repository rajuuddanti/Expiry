# Mahalaxmi Expiry Manager

Android expiry-date capture app for Mahalaxmi Mahamart stores.

## Exactly two Supabase tables

1. `product_master` — imported from the provided two-column master (`SkuCode/EAN`, `SkuLongName`). No category field is needed.
2. `expiry_entries` — records entered by store managers.

## Master lookup behavior

Managers can scan either the internal SKU code or an EAN/barcode. The master import preserves every code as text (including leading zeros) and maps each scannable code to the canonical SKU and name. A SKU may have multiple EANs. Exact duplicate rows can be deduplicated, but distinct EANs must be retained. If the two-column source does not unambiguously establish which EAN belongs to which SKU, the importer must flag it instead of guessing.

## Manager workflow

- Select a store name; no device registration.
- Scan barcode or enter code.
- Auto-fill SKU code, SKU name and EAN where known.
- Enter quantity and expiry date using a calendar picker.
- Save locally first, then sync to Supabase when online.
- Managers can correct mistakes with a soft delete (`is_deleted = true`); no physical delete.
- Visible history follows a 3 PM to next-day 3 PM session in Asia/Kolkata.
- Server exports/clearing do not reset local session logic or delete local unsynced records.

## Proposed stack

Kotlin Android, CameraX + ML Kit barcode scanning, Room local storage/sync queue, Supabase Postgres.

## Security

Do not ship a Supabase service-role key in the Android app. Configure a controlled read/write path and Row Level Security before production use. Store selection alone is not authentication.

## Status

Requirements and initial SQL are present; this repository is not yet a buildable Android app.

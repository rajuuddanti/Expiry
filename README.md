# Mahalaxmi Expiry Manager

Android app for store-wise expiry-date capture across Mahalaxmi Mahamart stores.

## Core workflow

- Manager selects a store name; no device registration is required.
- Scan a barcode or manually enter a code.
- Resolve the input against both the internal SKU code and EAN/barcode aliases. A manager does not need to know which type they scanned.
- Populate SKU code, SKU name, EAN, and category where available.
- Enter quantity and expiry date using a calendar picker.
- Save locally first; sync to Supabase when a connection is available.
- Managers can review and soft-delete mistakes. Deleting sets `is_deleted = true`; it never physically deletes a row.
- The on-device visible history is scoped to a daily session from 3:00 PM IST through, but not including, the next day's 3:00 PM.
- Office-side records remain independent of local session history. Server clearing/export must not clear the device's local data or reset its session.

## Product master import rules

The provided master has two columns: `SkuCode/EAN` and `SkuLongName`. The combined code column can contain either an internal SKU code or an EAN/barcode. Internal SKU codes are 5 or 6 digits (examples: `00001`, `100002`); other barcode-shaped values are treated as EAN/barcode candidates.

The import process must:
1. Read all codes as text, never numeric, to preserve leading zeros.
2. Identify 5- or 6-digit internal SKU codes without stripping leading zeros.
3. Associate all other codes in the source column with their product name as barcode/EAN candidates.
4. Build a barcode alias mapping so scanning either the SKU code or any EAN associated with that SKU resolves to the same product.
5. Deduplicate only exact duplicate code-to-product mappings. Never discard a distinct EAN just because the product name or SKU repeats.
6. Flag ambiguous mappings (one scanned code resolving to multiple different SKUs) for review rather than choosing arbitrarily.
7. Leave category blank until a trusted category source is provided; do not invent it.

If the master only provides a barcode row and product name but no explicit SKU-to-EAN link on that row, the importer must infer links only when the source data supports that relationship. It must not guess based solely on similar names if that could map one SKU to the wrong product.

## Daily session rule

Use Asia/Kolkata local time. For a timestamp before 15:00, its session starts at 15:00 on the previous calendar day. At or after 15:00, its session starts at 15:00 on the current day. The session is calculated from the clock, not app-launch time.

## Initial data model

See `supabase/migrations/001_initial_schema.sql`.

## Security note

Do not ship a Supabase service-role key in the Android app. The initial schema is a data-model starting point; configure Row Level Security and a safe write path before production use. Store-name selection alone is not authentication and cannot prove a manager belongs to a particular store.

## Proposed app stack

- Kotlin / Android
- CameraX with ML Kit Barcode Scanning
- Room for offline-first local data and pending-sync queue
- Supabase Postgres for product master and central expiry records
- Material components with Mahalaxmi's red header / light background UI inspired by the supplied Stock Audit screenshots

## Build status

This repository currently contains requirements and initial schema only; it is not yet a buildable Android application.

# Mahalaxmi Expiry Manager — Project Context

Last updated: 2026-10-26 (Asia/Kolkata)

## Why this app exists

Mahalaxmi Mahamart needs a practical Android app for store staff to record product expiry information while scanning products. The master data is large and contains both internal SKU codes and EAN/barcode values. A scan of either kind of code should resolve to the product name and canonical SKU code. Staff should be able to enter quantity and expiry date, save records reliably offline, and sync when the network is available.

## Current repository

- GitHub repository: https://github.com/rajuuddanti/Expiry
- Default branch: `main`
- App name: Mahalaxmi Expiry Manager
- Platform: Android, Kotlin (intended)
- Backend: Supabase Postgres (the user's Supabase is NOT connected to the assistant's Supabase connector; do not assume tool access to their project).
- Repo was last inspected on 2026-10-26. README says requirements and initial SQL exist, but the repository is not yet a buildable Android app. Verify current source before claiming implementation/build status.

## Product requirements

- Select and save a store name locally; allow changing it in Settings.
- No device registration requirement.
- Scan a barcode or enter a code manually.
- Either an internal SKU code or an EAN must find the product name.
- Resolve the canonical SKU code from the product name using the agreed first-come-first-served (FCFS) rule when names are duplicated.
- Enter quantity and expiry date, preferably with a calendar picker.
- Save locally first, then sync when online.
- Maintain a retryable sync queue; never lose local unsynced records.
- History uses a daily session from 3:00 PM IST to the next day at 3:00 PM IST.
- Correcting/deleting a saved record should be a soft delete; never physically delete manager records.
- Store name, scanned code, SKU, product name, quantity and expiry date must be represented clearly.
- Keep the UI straightforward and usable by store staff.

## Intended tables

Exactly two production data tables are intended:
1. `public.product_master` — master lookup for scannable codes.
2. `public.expiry_entries` — store expiry submissions.

A temporary staging table `public.product_master_staging` is being used during the current CSV import workflow. It is an import helper, not a third production business table.

## Source master

Original uploaded file: `Multiple MRP Report20260223060241.CSV(1).xlsx`.

The source file was discussed as a stacked two-column master with headers:
- `SkuCode/EAN`
- `SkuLongName`

User says the first approximately 28–30K rows contain the SKU-code list and the EAN codes are stacked below, rather than separated into different columns. The exact boundary has not yet been verified against the imported rows. Do not invent a boundary or infer it from product names without validating the data/order.

## Confirmed code normalization rule

- Treat all codes as TEXT; never cast them to numbers.
- If a code has fewer than 5 digits, left-pad with zeros until its length is 5.
- If it has 5 or more digits, leave it unchanged.
- Examples: `1 → 00001`, `123 → 00123`, `1234 → 01234`, `12345 → 12345`, `100002 → 100002`, `890910452145 → 890910452145`.
- Apply normalization consistently to scan codes, canonical SKU codes and EAN values. Do not truncate long barcodes.

## Confirmed duplicate/name lookup rule

User explicitly chose FCFS (first come, first served):
- Keep source row order meaningful.
- When reverse-looking up SKU code by product name, choose the first matching SKU-master row from top to bottom.
- If duplicate product names occur, first matching SKU wins. Do not ask the user to change to a more complex matching scheme unless a concrete failure requires it.
- Exact repeated code mappings should not create conflicting duplicate primary keys. Import logic must resolve/deduplicate these predictably and report conflicts.
- A scanned code should resolve to a product name, then the product name is used to find the first SKU code according to the FCFS rule when the scanned row itself is an EAN row.

## UI/UX decisions

- Clean, light/white interface with Mahalaxmi red header.
- Big readable fields/buttons for store staff.
- Main areas/tabs intended: Scan, History, Settings.
- Camera scan action and manual code entry.
- Quantity field and expiry-date calendar picker.
- Avoid adding unrequested categories or extra master-data fields.

## Security and reliability

- Never embed Supabase service-role key in the Android APK.
- Store selection is not authentication.
- Before production, configure appropriate Row Level Security and a controlled read/write path.
- Local-first writes, idempotent sync, and preservation of unsynced records are required.
- Use a stable client-side record UUID/idempotency key for sync.
- Do not claim the app has been built, installed, or tested unless actually verified.

## Known status at handoff

- User reports the CSV import into `product_master_staging` is done.
- User's screenshot of `product_master` showed exactly three NOT NULL text columns: `scan_code`, `sku_code`, `sku_name`.
- Import into `product_master` failed because CSV rows were being mapped into the three required columns with `scan_code = NULL`; example error row: `(null, 00001, DISCONTINUED 4065)`.
- Staging import completion was stated by user, but its row count, actual columns/data and correctness have not yet been verified.
- Do not say the assistant has connected to or inspected the user's live Supabase project. The user explicitly corrected this: Supabase was never connected to the assistant.

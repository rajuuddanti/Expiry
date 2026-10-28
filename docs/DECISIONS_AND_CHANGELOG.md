# Decisions and Changelog

Last updated: 2026-10-28 (Asia/Kolkata)

This file separates user-confirmed decisions from requirements that still need implementation or verification. For the current full state, read `docs/CURRENT_HANDOFF.md`.

## Confirmed core decisions

### Product master and code mapping
- Latest reported source is `public.MASTER` with `"SkuCode"`, `"SkuLongName"`, `"Catg"`.
- Source has SKU and EAN/barcode codes stacked in one code column; no separate EAN column in the latest master.
- Preserve original source order.
- Digit-only codes shorter than 5 characters are left-padded to 5; codes of length 5 or more remain unchanged.
- Keep codes as TEXT; do not truncate long barcodes.
- If product names duplicate, first SKU in original source order wins (FCFS).
- When an EAN/barcode is scanned, keep raw scanned code separate from canonical SKU. When a SKU is scanned directly, both can be the same.

### Local expiry entries
- Expiry records stay on the device in Room.
- Do not upload expiry records to Supabase under the latest decision.
- Provide CSV export/share for email or WhatsApp.
- Preserve local records and avoid data loss when offline or sync fails.
- Daily device history/session anchor is 3 PM local time.

### Return rules
- One table `public.expiry_return_rules` contains category and brand rules.
- Recorded Supabase result: 32 rules total (30 category rules + 2 brand rules).
- Brand overrides: HIMALAYA = 60 days; KELLOGGS = 30 days.
- Brand rule should take precedence over category rule.
- User requested a tappable Return Rules icon/dialog showing categories scheduled on the current day with return days and a separate brand section.
- The app should calculate effective category return days as `max(configured_return_days, schedule_interval_days)` when the configured return window is shorter than the inspection interval. Keep source rule values unchanged in Supabase.
- It remains unconfirmed whether the schedule-day floor also modifies brand rules. Do not silently alter the two configured brand values.

### Schedule
- `SCHEDULER.csv` covers 2026-10-23 through 2027-12-31.
- Preserve existing category rotation and the maximum two categories/day rule.
- Schedule should continue after 2027 from the exact end-of-2027 state, not restart arbitrarily.
- Return popup/effective return logic/automatic schedule continuation are requested work, not confirmed implemented or tested.

### Supabase and security
- Latest reported tables: `public.MASTER`, `public.expiry_return_rules`.
- The user-reported migration removed `expiry_scan_uploads`, `expiry_device_registry`, `expiry_security_events`, and `expiry_store_ip_registry`.
- Do not change Supabase config/credentials unless explicitly requested.
- Do not put service-role keys in the Android app.
- The assistant must not imply live Supabase access or claim to have queried the user's project unless a connected tool actually did so.

## Recent technical work recorded in prior session

Changes were reported for:
- local-only expiry entries and CSV share/export;
- FCFS lookup and raw scanned-code tracking;
- Android FileProvider configuration for sharing;
- changing master refresh interval from 15 minutes to 24 hours.

Recorded commit IDs:
- `9002bde4b230e5475a15ecebeae374120c26bd43`
- `ca764f735349bc2d4eea7efc0b81dbaf96392668`
- `3dd706937bc072f77e5a15a0ac9bd6d61c911d01`
- `caa9ea584da921bcad72138380e8385680beab12`

Verify current branch ancestry/source before assuming these commits are present. No build or device-test result was recorded.

## Earlier history (kept for context, may be stale)
- The project originally considered `product_master`, `product_master_staging`, and `expiry_entries` with cloud upload/delta sync.
- The earlier two-column import file was `SkuCode/EAN` and `SkuLongName`, and direct import into a different three-column `product_master` failed due to NULL `scan_code`.
- These older workflow notes are retained in the SQL/import and daily-sync documents but must not override the latest local-only expiry-entry direction or the reported `MASTER`/return-rules table names.

## Working style
User prefers direct, informal “bro” communication and concrete step-by-step actions. “Push the handoff .md files” means commit Markdown files directly to the requested GitHub repository/branch.

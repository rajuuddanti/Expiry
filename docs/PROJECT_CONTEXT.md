# Mahalaxmi Expiry Manager — Project Context

Last updated: 2026-10-28 (Asia/Kolkata)

## Why this app exists

Mahalaxmi Mahamart needs a practical Android app for store staff to record product expiry information while scanning products. A large product master contains SKU and barcode/EAN codes stacked in one source code column. Scanning either code should find the product name and canonical SKU. Staff enter quantity and expiry date, keep records locally, and can export/share CSV.

## Repository and app

- Repository: https://github.com/rajuuddanti/Expiry
- Recent working/docs branch: `feature/new-expiry-upload-table` (verify current HEAD before making code changes; branches have diverged).
- App name: Mahalaxmi Expiry Manager.
- Android package reported in current context: `com.mahalaxmi.expiry`.
- Android/Kotlin with Room local storage and Supabase REST for product master/rules.
- Do not claim build/install/test success without actual evidence.

## Latest reported Supabase state

The user reported a fresh product master upload and migration. Treat these as reported results, not a live connector inspection:
- `public.MASTER`: 124,570 rows; columns `"SkuCode"`, `"SkuLongName"`, `"Catg"`.
- `public.expiry_return_rules`: 32 rows (30 category rules and 2 brand rules).
- Obsolete tables `expiry_scan_uploads`, `expiry_device_registry`, `expiry_security_events`, and `expiry_store_ip_registry` were removed.
- `expiry_return_rules` uses one table for both category and brand rules.
- The assistant must not assume it has access to the user's Supabase project or reconfigure Supabase.

Older repository docs describe `product_master`, `product_master_staging`, and `expiry_entries`. Those describe an earlier data design and may be stale versus the latest direction. Verify current code and the user's live dashboard results before running SQL or changing schemas.

## Confirmed product mapping

- Master source code column has SKU/EAN/barcode values stacked vertically; there is no separate EAN column in the latest master.
- Preserve original source order.
- If a digit-only code is shorter than 5 digits, left-pad with zeroes to 5 characters; 5+ digits remain unchanged.
- Keep codes as TEXT. Never convert codes to integers or truncate long barcodes.
- If duplicate product names exist, use the first SKU row in source order (FCFS) as canonical.
- Directly scanned SKU: canonical SKU and raw scanned code can be the same.
- Scanned barcode: keep raw scanned code separate and map canonical SKU using the FCFS product-name match.
- Do not alter the `MASTER` schema.

## App requirements and decisions

- Store name is entered manually and can be changed only through the intended PIN-protected setting flow (PIN recorded as 3331); inspect implementation before changing.
- Scan by camera or enter code manually.
- Lookup product details from the locally cached master; support offline use.
- Record quantity and expiry date.
- Keep expiry entries local in Room; user does not want cloud upload of these records. Export/share CSV through Android sharing options.
- Keep daily device history/session rollover at 3 PM local device time, according to the latest project notes.
- Preserve existing records unless the user explicitly requests deletion.
- Export requirements discussed: Store, SKU code, SKU name, QTY; a recent implementation was also described as including scanned code and expiry date. Inspect actual code to confirm current headers.
- Supabase is used for product master and return rules, not cloud expiry-entry upload in the latest direction.

## Return rules

One table, `public.expiry_return_rules`, stores:
- category rules (`rule_type='category'`)
- brand rules (`rule_type='brand'`)

Brand overrides:
- HIMALAYA — 60 days before expiry.
- KELLOGGS — 30 days before expiry.

Recorded category rule values and popup/schedule requirements are documented in `docs/EXPIRY_RETURN_RULES_AND_SCHEDULE.md`.

Brand rules should override category rules. User proposed a tappable Return Rules info icon/dialog that shows categories scheduled for the present day with their return days, plus the two brand rules.

## Schedule and effective return window

- Schedule file: `SCHEDULER.csv`, covering 2026-10-23 through 2027-12-31.
- Preserve the existing rotation and the rule that no more than two categories are scheduled per day.
- Continue the schedule beyond 2027 from the exact final state, not by arbitrarily restarting the rotation.
- User proposed the category formula: `effective_return_days = max(configured_return_days, schedule_interval_days)`.
- Keep original configured return values unchanged in Supabase.
- Whether the schedule-day floor applies to brand overrides is unconfirmed; safest behavior is to display Himalaya/Kellogg's as fixed brand rules until clarified.
- Popup, effective return window, and indefinite schedule continuation are requirements, not verified implementations.

## Reliability and security

- Preserve Room records and local master during failed/empty sync.
- Do not change Supabase URL/key configuration unless explicitly asked.
- Never embed a service-role key in the APK.
- Do not infer build or test success from GitHub commits.

# Current Project Handoff — Mahalaxmi Expiry Manager

Last updated: 2026-10-31 (Asia/Kolkata)

## Continue here first

Repository: `rajuuddanti/Expiry`  
Current working branch for the recent changes/docs: `feature/new-expiry-upload-table`  
App name: Mahalaxmi Expiry Manager  
Android package reported in project context: `com.mahalaxmi.expiry`

Read in this order:
1. `README.md`
2. `docs/CURRENT_HANDOFF.md`
3. `docs/PROJECT_CONTEXT.md`
4. `docs/IMPLEMENTATION_STATUS.md`
5. `docs/DATA_MAPPING_AND_IMPORT.md`
6. `docs/EXPIRY_RETURN_RULES_AND_SCHEDULE.md`
7. `docs/DECISIONS_AND_CHANGELOG.md`
8. `docs/IMPLEMENTATION_PLAN.md`

## Non-negotiable accuracy rules

- A GitHub commit is not proof of a successful Android build or runtime test.
- Do not claim any code has been built, installed, or tested unless there is actual build/test output.
- Do not change Supabase configuration or credentials unless the user explicitly asks.
- Do not assume Supabase connector access is connected to the user's Supabase project. Historical conversation statements about the user's dashboard should be treated as user-reported results, not fresh connector verification.
- Never add a service-role key to the APK.
- Do not re-create cloud expiry-upload/device-registration/security tables: the user decided expiry entries remain local and are exported/shared as CSV.
- Keep the project’s actual branch and source tree verified before implementation; branches have diverged and older docs may describe a different branch/state.

## Product behavior and decisions

### Product master
The latest recorded user-provided master table is `public.MASTER` with columns:
- `"SkuCode"`
- `"SkuLongName"`
- `"Catg"`

The user reported uploading a fresh master of 124,570 rows. A prior query was reported as 12,385 blank codes and 0 blank names. Treat those as user-reported figures from that point in time, not as a live check. Do not change the MASTER schema.

There is no separate EAN column in this master. SKU and EAN/barcode codes are stacked in the source code column, FCFS. Preserve source order when mapping duplicate product names. If a code has fewer than five digits, left-pad to five; keep codes as text and leave five-or-more-digit codes unchanged. Do not truncate barcodes.

### Expiry entries and offline storage
- Scan barcode/EAN/SKU or enter a code manually.
- Resolve product name from the locally cached master.
- For an EAN row, map to the first matching SKU by product-name source order (FCFS); keep raw scanned value separate from canonical SKU.
- Store name is entered manually and locked behind PIN `3331` when changed; store mapping/IP history is for later analysis.
- Expiry entries are stored locally in Room and remain available offline. User has now approved optional manual upload to a dedicated Supabase table; CSV export remains available.
- Updated decision (2026-10-28): keep CSV export/share and add an explicit manual Supabase upload to a new dedicated `public.expiry_entry_uploads` table. This must not modify `MASTER` or `expiry_return_rules`.
- CSV export columns requested most recently: Store | SKU code | SKU name | QTY. A later implemented export was described as including Scanned code and Expiry date too; inspect actual source before asserting exact headers.
- Device history rolls over at 3 PM local device time; old entries should not be deleted from Supabase because the current decision is that expiry entries are local-only. Keep prior session/history behavior consistent with the actual code and verify before modifying.

### Supabase tables and return rules
The latest recorded Supabase state after a user-reported migration:
- `public.MASTER`: 124,570 rows.
- `public.expiry_return_rules`: 32 rows (30 categories + 2 brands).
- Obsolete tables `expiry_scan_uploads`, `expiry_device_registry`, `expiry_security_events`, `expiry_store_ip_registry` were dropped in a migration.
- `expiry_return_rules` stores both category and brand rules in one table, with `rule_type` = category/brand, `match_value`, `return_days_before_expiry`, `priority`, `note`, `is_active`, `created_at`. RLS is enabled; active-rule SELECT was granted to anon/authenticated according to the recorded migration result.

Brand rules:
- HIMALAYA — 60 days.
- KELLOGGS — 30 days.
Brand rules should override category rules. The user proposed showing them in a tappable Return Rules popup along with return days for the categories scheduled that day.

Category rule seed values and popup/schedule requirements are in `docs/EXPIRY_RETURN_RULES_AND_SCHEDULE.md`. Android has not yet been confirmed to read/apply this table.

### Schedule and return-window calculation
- `SCHEDULER.csv` covered 23 October 2026 through 31 December 2027.
- Do not manually restart the category rotation in 2028. Continue from the final state in 2027 using the same rotation/rollover rules.
- Existing rule: never exceed two categories per day.
- User proposed: if category return days are less than the schedule interval, calculate effective category return days as `max(configured return days, schedule interval days)`.
- Preserve original values in Supabase; calculate effective values in the app.
- Brand rules exemption from the schedule-day floor remains unconfirmed; safest default is to display brand overrides as fixed 60/30 days unless user confirms otherwise.
- The Return Rules popup, effective-window logic, and schedule continuation beyond 2027 are design requirements, not verified implementations.

## Expiry upload implementation (2026-10-28)

- `SupabaseSyncRepository.uploadPendingEntries()` uploads pending Room entries in batches of 100 to `public.expiry_entry_uploads` using stable local UUID `id` as the upsert conflict key.
- Local rows are marked `SYNCED` only after the corresponding batch is accepted; failures remain pending for retry.
- History has a separate **UPLOAD TO SUPABASE** action; CSV export remains separate.
- SQL migration is in `supabase/migrations/20261028_create_expiry_entry_uploads.sql`. It has been committed to GitHub but has NOT been applied to Supabase; the Supabase connector could not establish an eligible account link. Run the migration in the intended Supabase project before testing uploads.
- This upload design uses the existing publishable/anon key and RLS policies; it is not a substitute for store authentication. Do not add service-role credentials to the APK.
- Build and runtime test results must be verified separately; a GitHub commit is not a successful build.

## Recent repository work reported in the prior handoff

The recent working branch has commits related to:
- local-only expiry entries and CSV share/export;
- FCFS scan mapping;
- Android FileProvider XML for sharing;
- 24-hour rather than 15-minute product master refresh.
Recorded commit IDs from prior session:
- `9002bde4b230e5475a15ecebeae374120c26bd43`
- `ca764f735349bc2d4eea7efc0b81dbaf96392668`
- `3dd706937bc072f77e5a15a0ac9bd6d61c911d01`
- `caa9ea584da921bcad72138380e8385680beab12`

These commits are recorded context; verify branch history/source before assuming they are ancestors of the current branch. No Android build/test success was reported.

## Next steps

1. Confirm branch HEAD and inspect the source files as they currently exist.
2. Build the app in Android Studio or CI and fix actual compiler errors using logs.
3. Verify FCFS scan lookup for both direct SKU scans and EAN scans.
4. Verify product master refresh runs once per 24 hours and works offline; do not change Supabase config.
5. Add return-rule repository/model reading `expiry_return_rules` without modifying `MASTER`.
6. Add Return Rules icon/dialog: today's scheduled categories + active category return days + Himalaya 60/Kellogg's 30.
7. Implement and test category effective window formula only after the schedule interval is accurately computed.
8. Continue the current schedule beyond 2027 without breaking existing dates or the maximum-two-categories/day rule.
9. Update these Markdown files with actual implementation/test evidence after each meaningful change.


## 2026-10-31 — Google Sheets export live test and filter fix

- The user installed the standalone Apps Script, configured its Script Properties, authorized it, and created the daily time-driven trigger.
- Manual function `exportPreviousBusinessDayNow` completed successfully and logged **5 rows exported** across two actual categories. This confirms a successful manual run against the configured Supabase/Drive setup at that point; it does not prove the automatic trigger succeeds.
- The first automatic time-driven run (around 3:19 PM IST on 2026-10-31) failed with: `You can't create a filter in a sheet that already has a filter.`
- The user fixed the Apps Script editor's `writeSheet_` logic by removing any existing filter before creating a new one. The repository copy now includes that fix and the empty-day behavior (commit `ce85db629f30610280f802e9e18a76b70698637d`). The script creates header-only sheets for scheduled categories on a zero-row business day and clears old category sheets to headers only when their category has no current rows. This repository update still needs to be pasted into the live Apps Script editor and tested.
- Next verification: inspect Apps Script **Executions** after the next time-driven run and confirm the trigger run is **Completed**. If testing the trigger in the 4–5 PM window, delete the old trigger and recreate it by running `setupDailyTrigger` once after changing the hour; Apps Script timing is approximate, not exact.
- Existing Sheets are snapshots, not live links to Supabase. A Supabase deletion does not immediately change a Sheet. A later successful export rewrites the existing category sheet for that business-date window from the rows currently returned by Supabase, so a deleted test row should disappear on the next successful export for that same window. Only use disposable test rows for this check.
- Script uses `session_start` for the 3 PM–to–3 PM business window. Rows created after 3 PM belong to the new session and will not be included in the export for the previous session.
- The schedule JSON currently has dates with one category and dates with two categories. The folder name uses the category entries actually present for that date; do not invent a second category when the schedule has only one. Verify the live folder label against the schedule for that exact date.
- Security: keep the Supabase service-role key only in Apps Script Script Properties. Never commit it or add it to Android.

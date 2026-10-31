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

## 2026-10-29 — documentation and main-screen heading

- Added `docs/IMPLEMENTATION_LOG_2026-10-29.md` to record the latest UI change and project reminders.
- Main-screen heading should show the actual category names scheduled for the current day, joined with ` • ` when there is more than one category. Do not display an extra “Present Day Categories” label.
- Keep the Return Rules control on a separate row below the category heading.
- Added a documentation index in the root README so a new chat/developer can find the handoff, implementation status, schemas, SQL notes, business rules, and changelog quickly.
- The current branch head at the time of this documentation update was `2a7ee9a270f3b7523d3d0dfbc09979d82cddcfae` before these documentation commits.
- Verification discipline: do not infer a successful build or runtime test from a commit alone; check the relevant GitHub Actions run and test actual app behavior.
- Important: if older documents conflict about whether expiry entries are local-only or uploaded to Supabase, treat that as a documentation inconsistency that must be reconciled against the latest explicit user decision and current source before further implementation.

## 2026-10-29 — stack today's categories vertically

- On the Scan screen, each category scheduled today is now displayed on its own line rather than separated by a bullet.
- Keep the category heading driven by the existing schedule, and retain the Return Rules control below it.
- This is a layout-only change; no schedule logic, business rules, or Supabase configuration changed.
- Commit: `9dee3136d61a3af31e9f74dd19be510d0d5d2136`.

## 2026-10-29 — modern Scan screen design

- Refreshed the Scan screen header to a white surface with red MAHALAXMI MAHAMART branding and a prominent dark Expiry Manager title.
- Styled the current-store card, field borders/corners, expiry-date control, and primary Save Entry button for a more modern retail-app appearance.
- Preserved the dynamic vertical category list and existing form/business logic.
- Code commit: `b5b8e3040410fce4431981c5c9d781e9a3895e1a`. Build and device verification must be checked separately.

## 2026-10-29 — end-of-day Scan screen design handoff

- The Scan screen now has a white header, red MAHAMART brand text, and a bold dark Expiry Manager title.
- Today's scheduled categories are stacked vertically.
- Current-store card, rounded form fields, expiry-date control, and Save Entry button received a visual refresh.
- Code commits: `9dee3136d61a3af31e9f74dd19be510d0d5d2136` and `b5b8e3040410fce4431981c5c9d781e9a3895e1a`.
- Verification at documentation time: GitHub Actions for the visual refresh had not yet been confirmed complete; no device visual test recorded. Recheck on next session.

## 2026-10-29 — quantity reset and automatic expiry upload

- After a successful save, the quantity field is cleared instead of being reset to `1`; this avoids accidentally carrying the previous quantity into the next entry.
- Expiry entries now attempt an immediate upload to `public.expiry_entry_uploads` after being saved locally.
- If automatic upload fails (for example, no connection), the entry remains stored locally and a connected-network one-time retry is scheduled. The background sync worker also attempts to upload pending entries when it runs.
- The History screen's manual upload action remains available as a fallback.
- Code commits: `6c6194f0e540b350ba4bb39a0740110d99f47a33`, `c862f3ed96d8a642764ae434093c0422e618bf85`, and `bee929f29bdbcc23250302f540728ba871648b16`.
- Build and live-device/Supabase verification are pending; inspect GitHub Actions before claiming success.

## CSV export filename — 2026-10-29

- CSV export filenames now follow `Store_Category-Date.csv`, for example `Dharmaram_NAMKEEN-CHIPS-2026-10-29.csv`.
- Filename parts are sanitized to use letters, numbers, and hyphens so store/category names do not create invalid file paths.
- If entries contain more than one distinct category, the category part becomes `Multiple-Categories`; if category is missing, it becomes `Uncategorised`.
- The CSV column format and exported entry data remain unchanged.
- Code commit: `8493393283200b5034f5b76007b778c5ad1b6b03`.
- Build/device verification pending.

## 2026-10-29 — CSV export naming

- Export filenames use `Store_Category-Date.csv`; multiple categories use `Multiple-Categories`, and missing categories use `Uncategorised`.
- Filename components are sanitized. Exported CSV columns and data are unchanged.
- Code commit: `8493393283200b5034f5b76007b778c5ad1b6b03`.
- Build/device verification remains pending.

## 2026-10-31 — category schedule follows 3 PM business day

- Today's scheduled categories now resolve against `currentSessionDate`, not the midnight-based calendar date.
- Before 3 PM, the app continues showing the previous business day's categories; at/after 3 PM, it switches to the current date's categories.
- Inspection schedule data itself was not changed.
- Code commit: `43ceba4271faa69fad0703f54b40907d43e77b4d`.
- Build/device verification pending.


## 2026-10-31 — Google Sheets export requirements confirmed (not implemented yet)

- Source: export only rows from `public.expiry_entry_uploads`.
- Destination: Google Drive, nested by year and month, with one folder per business day.
- Daily schedule: 3:15 PM Asia/Kolkata; export the previous completed 3 PM-anchored business day (15:00 to 14:59:59 the following day).
- Folder naming: business date plus the **two scheduled categories** for that business day, e.g. `11 Nov Beverages-Biscuits`. Use the existing schedule as the source of those two category names; do not derive the folder name from categories entered by store staff.
- Sheets: dynamically create one Google Sheet per actual category present in the day's source rows. All stores are combined in each category sheet; retain the location/store column.
- Unexpected categories entered by store staff must still be exported into their own sheet in the same daily folder, but must not be added to the folder name. Do not hardcode the two scheduled categories as the only exportable categories.
- Preserve all source-table columns/values (including deletion status) and do not mutate/delete Supabase data. Prevent duplicate folders/files on retries.
- Google Sheets is preferred; Excel download can be done from Google Sheets.
- Status: this is a confirmed requirement, not an implemented feature. Any automation must keep credentials in Apps Script properties and must not expose a Supabase service-role key in Android. Do not change existing Supabase config.


## 2026-10-31 — Google Sheets export script committed

- Added `scripts/google-apps-script/Code.gs` and `docs/GOOGLE_SHEETS_EXPORT_SETUP.md`.
- Script reads `public.expiry_entry_uploads` only; exports the prior completed 3 PM-anchored business day; uses the schedule JSON to name one daily folder with scheduled categories; and creates a sheet for every category present in the source rows, including unexpected categories.
- All store rows remain combined in each category sheet; table columns including deletion status are retained. Export retries reuse existing folders/sheets and update an export log.
- Commit IDs: script `18763802e0e34c68c7909b5411df9ca1492cb71c`; setup guide `dff51ad20cdb2316dba79d7f4c04e03e085232a0`.
- Verification status: not yet installed in Google Apps Script, authorized, or tested against live Supabase/Drive. Follow setup guide and verify one manual export before enabling the daily trigger. Store the service-role key only in the private Apps Script project's Script Properties; never commit it or put it in Android.


## 2026-10-31 — Google Sheets export live test and filter fix

- The user installed the standalone Apps Script, configured its Script Properties, authorized it, and created the daily time-driven trigger.
- Manual function `exportPreviousBusinessDayNow` completed successfully and logged **5 rows exported** across two actual categories. This confirms a successful manual run against the configured Supabase/Drive setup at that point; it does not prove the automatic trigger succeeds.
- The first automatic time-driven run (around 3:19 PM IST on 2026-10-31) failed with: `You can't create a filter in a sheet that already has a filter.`
- The user fixed the Apps Script editor's `writeSheet_` logic by removing any existing filter before creating a new one. After this edit, the manual export completed again with **5 rows exported**. The fix has not yet been synced back into the repository's `scripts/google-apps-script/Code.gs`; the checked-in copy still needs that change.
- Next verification: inspect Apps Script **Executions** after the next time-driven run and confirm the trigger run is **Completed**. If testing the trigger in the 4–5 PM window, delete the old trigger and recreate it by running `setupDailyTrigger` once after changing the hour; Apps Script timing is approximate, not exact.
- Existing Sheets are snapshots, not live links to Supabase. A Supabase deletion does not immediately change a Sheet. A later successful export rewrites the existing category sheet for that business-date window from the rows currently returned by Supabase, so a deleted test row should disappear on the next successful export for that same window. Only use disposable test rows for this check.
- Script uses `session_start` for the 3 PM–to–3 PM business window. Rows created after 3 PM belong to the new session and will not be included in the export for the previous session.
- The last manual output showed a folder named `30 Oct MASALA SPICES`. Confirm the folder name contains both categories scheduled for that date; if only one category is returned from the schedule JSON, investigate before calling the two-category naming requirement verified.
- Security: keep the Supabase service-role key only in Apps Script Script Properties. Never commit it or add it to Android.

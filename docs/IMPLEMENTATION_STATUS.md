# Implementation Status — Mahalaxmi Expiry Manager

Last updated: 2026-11-11 (Asia/Kolkata)

## Repository state

- Repository: `rajuuddanti/Expiry`
- Current feature branch: `feature/20-day-sync-lock` (based on `feature/main-screen-redesign`).
- Latest version bump commit: `8066915edb5505f817595e3ecd8d14cec9b5d900` (versionCode 2 / versionName 0.2.1).
- Do not use stale `main` as the working branch. Verify HEAD before every change.

## Verified current state

| Area | Status | Evidence / boundary |
|---|---|---|
| NO EXPIRY category selection | Source implemented | Current branch source and docs |
| Prevent duplicate active NO EXPIRY marks | Source implemented | Current branch source and docs |
| Prevent NO EXPIRY when active expiry entries exist in same category | Source implemented | Commit `b7c22c1`; soft-deleted rows excluded |
| Block new entries after NO EXPIRY | Source implemented | One-category/two-category rules documented below |
| Daily Google Sheets export | User-confirmed working | User confirmed on 2026-11-02 after the 3 PM run |
| CSV line-break correction | Live Apps Script editor corrected | User pasted full corrected file and confirmed output works |
| GitHub Apps Script copy | CSV fix synchronized; reliability enhancements committed | New enhancements still need to be pasted into standalone Apps Script and tested |
| Latest Android build / device test | Not verified here | No fresh build or device output available |
| Latest Android CI status | Not verified here | Check Actions before reporting |

## NO EXPIRY rules in the current source

- One scheduled category: auto-select the only category; staff confirm.
- Two scheduled categories: staff select which category to mark and confirm.
- An active mark cannot be duplicated for the same store/category/business day. A soft-deleted mark no longer counts.
- A category cannot be marked NO EXPIRY while that store/category has active ordinary expiry entries in the current 3 PM–3 PM session. Staff must delete those entries from History first.
- One-category day: once marked, block all new expiry entries for the rest of that business day, including unscheduled categories.
- Two-category day: marking one category blocks only that category. The other scheduled category and unscheduled categories remain allowed. If both scheduled categories are marked, block all new entries.
- Final save validation covers both scanned and manually entered code paths.
- No schedule data was changed. The schedule remains capped at two categories/day.

## Google Sheets export — user-confirmed working on 2026-11-02

- Source: `public.expiry_entry_uploads`.
- Trigger: daily near 3:15 PM Asia/Kolkata; timing is approximate.
- Window: previous completed business day, 3 PM inclusive to next 3 PM exclusive, using `session_start`.
- Drive hierarchy: `MahaMart Expiry Reports/YYYY/Month/D MMM <scheduled categories>`.
- One category spreadsheet per category actually present in the source rows; all stores combined and location retained.
- Unexpected categories get their own spreadsheet.
- Category sheets retain all table fields, including deletion status.
- Active NO EXPIRY marks are written to `no expiry.csv`, separate from category sheets; deleted marks are omitted.
- Reruns refresh the dated output and update `_export_log`.
- Existing sheet filters are removed before creating a new filter.
- The user confirmed the live export works as intended after correcting the complete Apps Script file in the standalone editor.

### Repository script warning

The repository copy at `scripts/google-apps-script/Code.gs` has now been corrected to match the working live editor. The source line is:
```javascript
folder.createFile('no expiry.csv',lines.join('\r\n'),MimeType.CSV);
```
That source literal must have a single backslash before `r` and `n` in the JavaScript file. The generated CSV then contains real CRLF record separators. The script correction is committed separately from the earlier documentation update. GitHub does not automatically deploy the standalone Apps Script project; the user has already corrected and verified the live editor.

## Google Sheets reliability enhancements — 2026-11-02

Committed to `feature/no-expiry-fix`: overlap lock with 30-second wait, per-category sheet row-count verification, source-to-export reconciliation (excluding deleted NO EXPIRY records by design), NO EXPIRY CSV creation check, expanded `_export_log` metrics, and optional failure email via `EXPORT_ALERT_EMAIL`. These are source changes only; the standalone Apps Script editor has not yet been updated or tested with them. See `docs/GOOGLE_SHEETS_EXPORT_SETUP.md` for setup.

## Supabase context

Live project **Expiry** was accessible for read-only inspection on 2026-11-02:
- `public.MASTER`: columns `SkuCode`, `SkuLongName`, `Catg`; 125,120 rows at time of inspection.
- `public.expiry_return_rules`: 32 rows at time of inspection.
- `public.expiry_entry_uploads`: expiry entry and NO EXPIRY upload data.

These are time-stamped observations, not permanent row counts. Do not change project settings, credentials, schema, or policies without explicit instruction.

## Product master and schedule rules

- SKU and EAN/barcode values are stacked in the source code column; preserve source order and text values.
- Pad digit-only codes shorter than five characters to five; do not alter longer codes or truncate barcodes.
- EAN-to-SKU canonical mapping uses first matching product name (FCFS); preserve the raw scanned code separately.
- Schedule supplied in `inspection_schedule.json` spans 2026-10-23 to 2027-12-31 and must never exceed two categories per day.
- Continue the existing rotation after 2027; do not restart it.
- Brand return rules: Himalaya 60 days; Kellogg's 30 days; brand rules override category rules.
- Proposed category effective return window is `max(configured_return_days, schedule_interval_days)`; do not alter saved source values or assume brand-floor behavior is approved.

## Not verified / future work

- Fresh Android Gradle build and installation of the latest branch.
- Real-device verification of all NO EXPIRY paths, including soft-delete/undo and category combinations.
- Latest CI result for the app-source commit.
- End-to-end product lookup tests for SKU and EAN against the current master.
- App-side fetch/cache of `expiry_return_rules`, Return Rules popup, and effective-window calculation.
- Schedule continuation beyond 2027.

## Safety and process

- Do not modify Supabase URL/key settings or `local.properties` unless asked.
- Never put the service-role key in the APK, GitHub, or chat.
- Inspect branch/source before patching; no merges or branch switches without permission.
- A GitHub commit is not proof of a build or runtime test.
- The live Apps Script project is separate from the repository copy; do not claim GitHub commits deploy it.


## 2026-11-11 branch update — 20-day manual sync lock

- Current feature branch: `feature/20-day-sync-lock` (based on `feature/main-screen-redesign`).
- Application ID unchanged: `com.mahalaxmi.expiry`.
- Branch version: versionCode 2, versionName 0.2.1.
- Manual SYNC NOW cooldown is source-implemented in `MainActivity.kt`, using existing `expiry_manager/master_last_sync`; successful manual sync starts 20 days, failure does not. Button locks and displays remaining days/hours. Background WorkManager sync is unchanged.
- No Room schema/version, Supabase schema/config, sync-history table, PIN/override, or unrelated behavior changes.
- GitHub Actions workflow includes this branch and uploads a debug APK artifact.
- Still unverified: latest CI build result, physical-device installation/update, same-signing-key compatibility, and real-device cooldown test.
- User's local branch switch/pull is blocked by local changes to `app/build.gradle.kts`; preserve them with a path-specific stash before switching. Never reset or force checkout.
- See `docs/IMPLEMENTATION_LOG_2026-11-11.md` for full task context, decisions, Git commands, and next steps.

## Planned next feature (not implemented): in-app updater

GitHub Releases + version metadata JSON, app-side update check, download progress, and Android installer handoff are the discussed direction. Admin-controlled rollout is a requirement to explore. Android normally prompts the user to confirm sideloaded APK installation. No updater code exists yet.

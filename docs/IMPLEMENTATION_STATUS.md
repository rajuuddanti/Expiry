# Implementation Status — Mahalaxmi Expiry Manager

Last updated: 2026-11-11 (Asia/Kolkata)

## Repository state

- Repository: `rajuuddanti/Expiry`
- Current feature branch: `feature/in-app-updates` (based on `feature/20-day-sync-lock`).
- Current test version: versionCode 3 / versionName 0.2.2. The previously installed baseline is 0.2.1.
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


## 2026-11-11 — initial in-app updater source

Branch: `feature/in-app-updates`, based on `feature/20-day-sync-lock`.

Implemented in source:
- Automatic latest-release check shortly after launch.
- Manual check action in Settings.
- Admin-controlled rollout by publishing a GitHub Release with `update.json` and APK assets.
- Version comparison, release notes, mandatory-update indicator, APK download progress, optional SHA-256 verification, FileProvider installer handoff, and unknown-source permission settings.
- CI push trigger includes the new feature branch.

No Supabase, Room, expiry, product-master sync, or business-rule changes.

Not yet verified: Gradle build/CI success, actual release asset download, installer handoff, same-signature in-place update, and device behavior. The CI debug APK signing key is runner-generated and is not guaranteed to match the user's installed app. Resolve stable signing before rollout. See `docs/IN_APP_UPDATE_SETUP.md` for the release manifest and admin procedure.


## In-app update test build — 2026-11-11

- Settings navigation glyph changed from `⚙` to `🔧`; no other app UI/business logic changed for this test.
- Version bumped to `3` / `0.2.2`.
- User must pull `feature/in-app-updates` and build locally in Android Studio using the same PC/debug signing key as the installed 0.2.1 app.
- Publish the resulting APK plus `update.json` as assets on GitHub Release `v0.2.2`. A source push alone does not publish an update.
- Not yet verified: Gradle build, APK signing match, release asset download, installer flow, or data preservation.


## Latest status — 2026-11-11: in-app update test

Branch: `feature/in-app-updates`.

Implemented and committed:
- GitHub latest published Release update check on app launch and manual Settings check.
- `update.json` metadata handling, versionCode comparison, release notes/mandatory display, APK download progress, optional SHA-256 validation, and Android installer handoff.
- Test version bumped to `versionCode 3` / `versionName 0.2.2`; baseline is `2` / `0.2.1`.
- Settings icon glyph changed from `⚙` to `🔧` as the minimal visible test change.
- Updater setup/release instructions are in `docs/IN_APP_UPDATE_SETUP.md`.

Test APK evidence:
- Local 0.2.2 APK SHA-256: `1a2dfbe9df87ea475a3fd8c393c61e9296e08f54ef6efc5c759fd25e4e681d89`.
- Local 0.2.2 signing certificate SHA-256: `855ba8e12336f7dcd6bd2df1ba991acc129bd82592f5b19895c5b7b0a4867df5`.
- Android Studio Analyze APK verified the original baseline APK as `com.mahalaxmi.expiry`, version `0.2.1`, code `2`; its signing fingerprint matches the new 0.2.2 APK.
- This confirms the two APK files can share an update signing identity; it does not prove installation/update flow works.

Pending:
- Confirm GitHub Release `v0.2.2` is published with APK asset `MahaMart-Expiry-Manager-v0.2.2.apk` and metadata asset named exactly `update.json`.
- Latest screenshot showed APK name corrected, but JSON name was still `Update .JSON`; correct it to lowercase `update.json`.
- Keep 0.2.1 installed; test update detection, download, SHA-256 check, installer handoff, in-place install, and data retention.
- No successful Gradle/CI build or end-to-end physical-device update is documented yet.


## 2026-11-11 follow-up — prepare version 0.2.3

- Restored the Settings navigation icon from the temporary test wrench `🔧` to the original gear `⚙`. Commit: `be4da1cc65169f9ede61161775a440c426d3da6f`.
- Bumped app version to `0.2.3` / versionCode `4`. Commit: `5907bbee7ad98f91a670e05e46afee9c00df203c`.
- This is a minimal visual revert plus version increment; updater logic and business behavior are unchanged.
- Next: pull branch, build APK locally with same signing identity, calculate its SHA-256, then publish GitHub Release `v0.2.3` with APK and lowercase `update.json`.
- Required metadata: versionCode 4, versionName 0.2.3, apkAssetName `MahaMart-Expiry-Manager-v0.2.3.apk`, mandatory false, releaseNotes `Restored the original Settings gear icon.`, and the actual SHA-256 of the built APK.
- User screenshot confirmed `v0.2.2` release is published/latest with APK and lowercase `update.json`. The 0.2.1 → 0.2.2 in-app update test has not yet been confirmed. Publishing 0.2.3 will replace 0.2.2 as Latest.


## 2026-11-14 — product-master sync must become manual-only (decision; implementation pending)

The user identified repeated complete product-master downloads as the cause of avoidable Supabase egress and requested that product-master sync be manual on every device.

### Agreed direction
- App opens using the locally cached Room product master.
- Do not automatically download `public.MASTER` on launch, resume, or in the background.
- Keep a clearly available manual **SYNC NOW** action.
- A manual action downloads the complete master using the existing pagination approach.
- Replace/update the local master only after the complete download succeeds; if sync fails, preserve the previous local master.
- Prevent overlapping manual master syncs.
- Keep expiry-entry uploads, local history, category/rule behavior, and the GitHub in-app updater unchanged.

### Important implementation boundary
This is a requirement/plan only. No Kotlin source, WorkManager configuration, Supabase function, database schema, or release asset was changed as part of this documentation update.

The existing source history includes a 20-day manual-sync cooldown and background WorkManager behavior. Both must be inspected in the current `feature/in-app-updates` source before implementation. The latest user request is manual-only across devices; confirm whether manual sync should be available at any time or retain the 20-day cooldown before removing/changing that cooldown.

### Supabase enforcement
- Phase 1 can be implemented in the Android app without changing Supabase schema or project configuration.
- App-only enforcement does not stop old installed app versions from making automatic requests.
- Optional Phase 2: after the updated app is deployed, add a server-side request guard so MASTER downloads require an explicit manual-sync request marker/header. Test the guard against the actual PostgREST request path before enabling it.
- Do not claim Supabase enforcement exists until implemented and HTTP-tested.

### Rollout
Use the existing GitHub Releases + `update.json` in-app updater to distribute a new app version after build, signing, and device tests. Publishing a GitHub Release is separate from pushing source code. Do not claim that all devices have updated until rollout is verified.

## 2026-11-14 — Supabase MASTER egress investigation

Read-only edge-log inspection for 2026-11-14 (00:00–11:10 IST) showed successful GET pagination against `/rest/v1/MASTER`.

- Request Range headers show pages of 1,000 rows (for example, `0-999`, `1000-1999`).
- The observed master contained 125,120 rows, so a full download requires 126 page requests (the last page covers rows 125,000–125,119).
- Confirmed complete page sequences by user-agent signature:
  - Xiaomi `24108PCE2I`, Android 16: 1,722 page requests; 12 complete sequences confirmed, with 5 additional first-page starts beyond the 12 matched final-page requests.
  - Xiaomi `24108PCE2I`, Android 15: 499 page requests; 4 complete sequences confirmed, with additional partial activity.
  - Vivo `V2240`, Android 15: 378 page requests; 3 complete sequences confirmed.
  - Xiaomi `2312BPC51H`, Android 16: 232 page requests; 1 complete sequence confirmed.
  - Xiaomi `24108PCE2I`, Android 14: 126 page requests; 1 complete sequence confirmed.
  - Moto G57 Power, Android 17: 26 page requests; no complete sequence confirmed in this window.
- At least 21 complete master downloads are therefore confirmed across these signatures in the checked window. Partial attempts are additional.
- IP addresses varied and must not be interpreted as physical-device counts. A user-agent signature is not proof of an exact handset count or store identity.
- Supabase log response content-length values were not populated in the inspected aggregate, so this investigation identifies request activity, not an exact byte/egress total.
- No additional device was blocked and no historical data was changed as part of this investigation.

## 2026-11-14 — updater release state carried forward

- Working branch: `feature/in-app-updates`.
- Version source was advanced to `versionCode 4` / `versionName 0.2.3` (commits recorded in prior handoff).
- The user previously confirmed release `v0.2.2` was published. The 0.2.1 → 0.2.2 in-app update device test was not confirmed.
- Version 0.2.3 build, release publication, and end-to-end device update remain unverified in this handoff. Do not infer these from the version bump.


## 2026-11-14 — manual-only product master and mandatory app update flow

Implemented on `feature/in-app-updates`:

- `ExpirySyncWorker` is upload-only. Existing initial/periodic background work and save-time retry can upload pending expiry entries, but do not download the product master.
- Product-master download remains manual through Settings → **SYNC NOW**, retaining the existing 20-day successful-sync cooldown.
- Automatic app-update check remains on app launch and uses GitHub Releases only; updater code does not access Supabase.
- Any detected newer version is mandatory regardless of the manifest flag. The popup is non-dismissible, with UPDATE → download progress in the same popup → INSTALL UPDATE. No LATER/CLOSE.
- Manual **CHECK FOR APP UPDATES** only opens the blocking popup when a newer version exists; otherwise it shows an up-to-date toast.
- App version advanced to 0.2.4 / versionCode 5.

Boundary: `fetchReturnRules()` still reads return rules from Supabase on app startup. That is separate from the GitHub updater and has not been removed. If GitHub is unreachable, the launch check cannot confirm a release and the app remains usable. Once a release is detected, the update popup blocks normal use.

Not yet verified: Gradle build, CI result, APK signing compatibility, GitHub v0.2.4 release publication, and end-to-end device installation. Android's package installer still requires user confirmation.


## 2026-11-14 — APK SHA-256 release procedure documented

- Added `docs/APK_HASH_AND_RELEASE_CHECKLIST.md` on `feature/in-app-updates`.
- Documents the PowerShell `Get-FileHash ... -Algorithm SHA256` command, exact-file requirement, double-extension download naming pitfall, manifest alignment, and post-upload checksum verification.
- Documentation only; no app source, APK asset, signing key, release asset, Supabase configuration, or database was changed by this documentation commit.

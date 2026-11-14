## Copy this into a new chat

Continue the Mahalaxmi Expiry Manager project in `rajuuddanti/Expiry`.

**Active branch for current updater work:** `feature/in-app-updates`  
**Last app-source commit:** `b7c22c15d9e8ff758c181f95d3851de5ac92f372` — Prevent NO EXPIRY when category has active expiry entries.

Read:
- `README.md`
- `docs/CURRENT_HANDOFF.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/PROJECT_CONTEXT.md`
- `docs/DECISIONS_AND_CHANGELOG.md`
- `docs/NO_EXPIRY_FEATURE.md`
- `docs/NO_EXPIRY_CATEGORY_SELECTION.md`
- `docs/GOOGLE_SHEETS_EXPORT_SETUP.md`

## Current state

### App
- Offline-first Android/Kotlin expiry capture app; Room stores local history.
- Store is selected manually and protected by PIN `3331` when changed.
- Business day is 3 PM to 3 PM Asia/Kolkata.
- Product lookup accepts SKU or EAN/barcode. Keep codes as text, preserve source order, use FCFS for duplicate-name canonical SKU mapping, and preserve the raw scanned code separately.
- Schedule JSON covers 2026-10-23 through 2027-12-31 and must never exceed two categories per day.
- Himalaya return rule is 60 days; Kellogg's is 30 days. Brand rules override category rules.
- NO EXPIRY behavior is described below and in the dedicated feature docs.

### NO EXPIRY
- One scheduled category: auto-select it; confirmation marks it NO EXPIRY.
- Two scheduled categories: staff select one and confirm.
- Active NO EXPIRY marks prevent duplicates for that store/category/business day; soft-deleted marks do not.
- Before marking NO EXPIRY, refuse if that store/category has an active ordinary expiry entry. Staff must delete the entry from History first. Soft-deleted entries do not count.
- One-category day: marking NO EXPIRY blocks all new expiry entries for that business day, including unscheduled categories.
- Two-category day: marking one category blocks only that category; the other scheduled category and unscheduled categories remain allowed. If both scheduled categories are marked, all new entries are blocked.
- Save-time validation covers both scanned and manual entry paths.
- These are source-level changes committed on the active branch. Do not claim Android build/device verification without actual evidence.

### Google Sheets export
- User confirmed on 2026-11-02 that the live export works as intended.
- The standalone Apps Script runs near 3:15 PM IST and exports the previous completed 3 PM–3 PM session from `public.expiry_entry_uploads`.
- Category sheets are generated from categories actually present in the data; all stores are combined. Active NO EXPIRY confirmations are exported separately to `no expiry.csv`; deleted NO EXPIRY marks are excluded.
- The CSV writer in both the GitHub repository and the working live Apps Script uses actual CRLF line breaks. GitHub now also contains row-count reconciliation, per-sheet verification, overlap locking, and optional failure email alerts.
- The GitHub script update does not deploy to Apps Script; the standalone editor remains a separate deployment. The user confirmed the live export works.

## Rules for future work

- Do not change Supabase URL/key configuration, `local.properties`, database schema, or policies unless the user explicitly asks.
- Never put a service-role key in Android, GitHub, or chat.
- Do not switch to `main`, merge branches, or rebase without permission.
- Inspect actual current source before patching.
- Distinguish source review from build, CI, backend, and device verification.
- User confirmed the export works; do not unnecessarily modify it.
- If asked to synchronize the GitHub Apps Script copy, use the exact corrected version from the user/live editor and make that a separate code change.

## Recommended next step

Wait for the user's next requested task. If they ask for Android changes, inspect the current branch and relevant Kotlin code first; if they ask for Apps Script changes, preserve the working live setup and change only what is requested.


## Google Sheets reliability follow-up — 2026-11-02

Latest source changes on `feature/no-expiry-fix` add a 30-second script lock, per-category row-count checks, overall Supabase-to-export reconciliation, NO EXPIRY CSV creation verification, expanded `_export_log` metrics, and optional email alerts via `EXPORT_ALERT_EMAIL`. These changes are only in GitHub until the updated `Code.gs` is pasted into the standalone Apps Script editor, saved, and authorized. After that, run `exportPreviousBusinessDayNow` once and inspect `_export_log`.
# Next Chat Handoff — Mahalaxmi Expiry Manager

Last updated: 2026-11-11 (Asia/Kolkata)

## Current handoff pointer — read this first

Continue in repository `rajuuddanti/Expiry`. Current feature branch is `feature/20-day-sync-lock`, based on `feature/main-screen-redesign`. Application ID: `com.mahalaxmi.expiry`. Branch version: versionCode 2 / versionName 0.2.1. Version bump commit: `8066915edb5505f817595e3ecd8d14cec9b5d900`.

Read `docs/IMPLEMENTATION_LOG_2026-11-11.md` for the latest task's purpose, implementation, explicit scope limits, Git situation, verification boundaries, and planned next feature. The existing project documents below retain the older business and architecture history.

**Immediate local Git issue:** the user's `git switch` and `git pull` were blocked because local changes to `app/build.gradle.kts` would be overwritten. Do not reset or force checkout. First stash only that path, switch to the feature branch, pull, and verify the version. Do not automatically apply the stash because it may restore older version settings.

**Latest implementation:** manual SYNC NOW is locked for 20 days after successful manual product-master sync, using existing `expiry_manager/master_last_sync`. Failed sync does not start/reset the lock. The remaining days/hours are displayed. Background WorkManager sync is unchanged. No sync-history table, Supabase schema/config changes, Room schema/version changes, PIN/override, or unrelated behavior changes.

**Verification:** source and version values were inspected on GitHub. Do not claim latest CI build success, APK install, same-signing-key compatibility, or device verification until independently checked.

**Next requested feature (not implemented):** in-app update check/download/install flow, likely GitHub Releases + version metadata, with admin-controlled rollout considered. Android normally requires user confirmation for sideloaded APK installation. Keep updater separate from product-master sync and expiry data sync.

---



## Current active task — in-app updater (2026-11-11)

Continue on `feature/in-app-updates`, based on `feature/20-day-sync-lock`. Read `docs/IN_APP_UPDATE_SETUP.md` and the updater section in `docs/CURRENT_HANDOFF.md` first.

Source implementation adds automatic launch checking, a Settings manual check, GitHub Releases as the admin-controlled update channel, `update.json` metadata, APK download progress, optional SHA-256 validation, and Android installer handoff. No Room/Supabase/business logic was changed. Current version remains 2 / 0.2.1.

**Do not claim build/device success yet.** Verify CI. Before distributing APK updates, confirm the signing certificate of the currently installed app and configure a stable signing identity. The GitHub Actions debug keystore is runner-specific and may not match the existing installed app. Android package installer confirmation remains required.


## Current updater test state — 2026-11-11

- Baseline 0.2.1 was installed by the user from Android Studio.
- Test source change: Settings navigation glyph `⚙` → `🔧` only.
- New build version: `0.2.2` (`versionCode 3`).
- Pull `feature/in-app-updates`, build locally on the same PC/signing identity, then publish APK + `update.json` as GitHub Release `v0.2.2`.
- The app will not detect a source-code push by itself; it checks published GitHub Releases.
- No build or end-to-end update has been verified yet.


## 2026-11-11 — latest in-app updater test status

Continue on `feature/in-app-updates` in `rajuuddanti/Expiry`; do not switch branches or merge unless asked.

- Updater implementation is committed: automatic launch check, Settings manual check, GitHub latest published Release lookup, `update.json` parsing, version comparison, APK download progress, optional SHA-256 verification, and FileProvider installer handoff.
- Test app version is `0.2.2` / versionCode `3`; baseline is `0.2.1` / versionCode `2`.
- Only visible test change in 0.2.2 is Settings glyph `⚙` → `🔧`.
- User's local 0.2.2 APK SHA-256: `1a2dfbe9df87ea475a3fd8c393c61e9296e08f54ef6efc5c759fd25e4e681d89`.
- Signing certificate SHA-256 for the local 0.2.2 APK: `855ba8e12336f7dcd6bd2df1ba991acc129bd82592f5b19895c5b7b0a4867df5`.
- Android Studio Analyze APK confirmed `original-0.2.1.apk` is package `com.mahalaxmi.expiry`, versionName `0.2.1`, versionCode `2`. Its signing fingerprint matches the local 0.2.2 APK.
- Do not uninstall the existing 0.2.1 app. The user is preparing GitHub Release `v0.2.2` targeted at `feature/in-app-updates`, with assets `MahaMart-Expiry-Manager-v0.2.2.apk` and exactly `update.json`.
- The metadata checksum must be `1a2dfbe9df87ea475a3fd8c393c61e9296e08f54ef6efc5c759fd25e4e681d89`; versionCode 3, versionName 0.2.2, mandatory false, releaseNotes "Settings icon update and in-app update test.".
- Latest screenshot showed APK filename correct but JSON filename was `Update .JSON`; it must be renamed exactly to lowercase `update.json`. Old APK assets marked "will be deleted" are okay if they are unwanted duplicates.
- Release publication is not yet confirmed. After user confirms publish, test detection/download/checksum/installer/in-place update and retained app data. Do not claim success before device evidence.
- Android still requires user confirmation in its package installer; mandatory only prevents dismissing the app's update dialog, not Android's confirmation.


## Latest follow-up — 0.2.3 test release preparation

The user asked to revert the temporary Settings icon change and publish another version for testing. On `feature/in-app-updates`:
- Restored Settings icon glyph `🔧` → original `⚙` in MainActivity. Commit `be4da1cc65169f9ede61161775a440c426d3da6f`.
- Bumped version to `0.2.3` / versionCode `4`. Commit `5907bbee7ad98f91a670e05e46afee9c00df203c`.
- No build or device test was run by the assistant.
Next: user runs `git pull origin feature/in-app-updates`, builds locally, calculates APK SHA-256 with PowerShell `Get-FileHash`, and prepares release `v0.2.3` with APK named `MahaMart-Expiry-Manager-v0.2.3.apk` plus exact lowercase `update.json`. JSON values: versionCode 4, versionName 0.2.3, matching apkAssetName, mandatory false, releaseNotes "Restored the original Settings gear icon.", and actual built APK SHA-256.
The user screenshot confirmed v0.2.2 is published/latest with correct APK and `update.json`. Do not claim 0.2.1 → 0.2.2 in-app update succeeded; user has not reported the result. If they want to test 0.2.2 separately, do that before publishing 0.2.3 because 0.2.3 becomes Latest.


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


## 2026-11-14 — latest source changes to verify

Branch: `feature/in-app-updates`; app version source: 0.2.4 / versionCode 5.

- Background `ExpirySyncWorker` is upload-only; it no longer refreshes product MASTER.
- Manual Settings → SYNC NOW is the only product-master download path and retains the 20-day cooldown.
- Automatic launch updater checks GitHub Releases only. Any newer release is mandatory and opens a non-dismissible popup: UPDATE → in-dialog download progress → INSTALL UPDATE. Manual check shows an up-to-date toast if no release is newer.
- Expiry-entry background uploads and save-time retry remain enabled.
- `fetchReturnRules()` still calls Supabase at startup; do not describe the entire app launch as Supabase-free. Only the updater check/download is GitHub-only.
- Build, CI, APK signature compatibility, v0.2.4 release publication, and device update remain unverified. Test before rollout.

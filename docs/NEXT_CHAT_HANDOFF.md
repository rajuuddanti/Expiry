## Copy this into a new chat

Continue the Mahalaxmi Expiry Manager project in `rajuuddanti/Expiry`.

**Start on branch:** `feature/no-expiry-fix`  
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


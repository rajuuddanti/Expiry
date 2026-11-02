# Next Chat Handoff — Mahalaxmi Expiry Manager

Last updated: 2026-11-02 (Asia/Kolkata)

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
- The CSV fix in the live Apps Script uses actual CRLF line breaks.
- **Important:** GitHub's `scripts/google-apps-script/Code.gs` still has the old escaped line separator. Do not paste that stale repository copy over the working live Apps Script editor. The current documentation-only update does not change the script.

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

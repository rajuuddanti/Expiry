# Implementation Status — Mahalaxmi Expiry Manager

Last updated: 2026-10-31 (Asia/Kolkata)

## Branch context

Current branch used for recent documentation/work: `feature/new-expiry-upload-table`. Branch histories have diverged; verify HEAD and source files before assuming all recorded commits are present.

## Recent changes recorded in the handoff

The prior session reported changes related to:
- local-only expiry records in Room (no cloud upload of expiry entries);
- CSV export/share through Android's share sheet;
- FileProvider configuration for sharing;
- SKU/EAN scan mapping with FCFS canonical SKU and separate raw scanned code;
- master refresh interval changed to 24 hours.

Recorded commit IDs are listed in `docs/CURRENT_HANDOFF.md`. These notes are not proof that every commit is on the current branch or that the code compiles. Inspect actual source before relying on these statements.

## Backend state reported by user

- `public.MASTER` contains product master data with `"SkuCode"`, `"SkuLongName"`, and `"Catg"`; 124,570 rows were reported after upload.
- `public.expiry_return_rules` stores 30 category rules and 2 brand rules in one table.
- Brand rules: HIMALAYA = 60 days; KELLOGGS = 30 days.
- Obsolete cloud expiry-upload/device/security tables were reportedly removed.

These are historical user-reported results, not a fresh inspection of the user's Supabase project. Do not change Supabase config or claim live access.

## Not yet verified / not yet confirmed implemented

- Android debug build and runtime behavior.
- End-to-end direct SKU and EAN lookup against the current `MASTER` schema.
- Offline persistence across app restart and local CSV sharing.
- Actual 24-hour refresh behavior and error handling.
- Android fetching/caching `expiry_return_rules`.
- Return Rules icon/popup for today's scheduled categories plus Himalaya/Kellogg's.
- Effective category return-window formula.
- Continuous schedule generation after 31 December 2027.

Do not describe these items as implemented until source changes are committed and build/tests provide evidence.

## Target behavior

Read `docs/CURRENT_HANDOFF.md` and `docs/EXPIRY_RETURN_RULES_AND_SCHEDULE.md` for current requirements. In short: keep expiry records local, use one return-rule table for categories and brands, prioritize brand overrides, show today's scheduled category return windows in a popup, preserve the supplied calendar through 2027, and continue the exact schedule rotation beyond 2027 without exceeding two categories per day.

## Safety constraints

- Never put a Supabase service-role key in the Android app.
- Do not alter `public.MASTER` schema or Supabase URL/key configuration unless explicitly asked.
- Do not reintroduce cloud expiry-entry uploads or old device/security tables without explicit user approval.
- A GitHub commit is not a successful build or test.

## Google Sheets export — status as of 2026-10-31

**Manual export: verified working in the user's Apps Script environment. Automatic trigger: not yet verified after the fix.**

- Manual `exportPreviousBusinessDayNow` completed and logged 5 rows across two actual categories; the user reran it successfully after fixing the filter error.
- First time-driven run failed with `You can't create a filter in a sheet that already has a filter.`
- User's Apps Script editor now removes an existing filter before creating a new one. The checked-in `scripts/google-apps-script/Code.gs` has not yet been updated with this fix and remains out of sync.
- Next acceptance checks: (1) next time-driven execution status is Completed; (2) scheduled folder name contains both scheduled categories; (3) rerun updates existing sheet contents without duplicate sheets; (4) deletion of a disposable test row is reflected only after a successful rerun for that same business-date window.
- Existing Sheets are static snapshots between exports; Supabase changes do not instantly propagate to Sheets.
- Keep credentials in private Apps Script Script Properties. Do not commit the service-role key or put it in Android.

# Daily Google Sheets Export Setup

This Apps Script exports only `public.expiry_entry_uploads` to Google Drive. It does not change or delete Supabase records.

## Output behavior
- Runs daily around 3:15 PM Asia/Kolkata (Google triggers are approximate).
- Exports the previous completed business-day session: previous date 3:00 PM IST inclusive to export date 3:00 PM IST exclusive.
- Drive path: `MahaMart Expiry Reports / YYYY / Month / D MMM <two scheduled categories>`.
- The folder-name categories come from `app/src/main/assets/inspection_schedule.json` for the exported business date, with explanatory text in parentheses removed.
- Creates one Google Sheet only for each category actually present in the Supabase rows for that business-day window; all stores are combined and the `location` column is retained. Scheduled categories do not get sheets unless Supabase contains rows for them. On reruns, generated category sheets absent from the current Supabase result are moved to Drive trash so stale/extra category files are not left behind.
- Unexpected categories are exported in their own sheet but do not change the folder name.
- Exports all table columns, including `is_deleted`. Empty business days create the dated folder and an export log row with `row_count = 0`, but no category sheets. Any old generated category sheets in that daily folder are moved to Drive trash on rerun. The script does not mutate Supabase.
- Pagination is handled in 1,000-row pages. Reruns reuse the same folder and category sheet names and replace their contents. An `_export_log` spreadsheet records the last export per business date, status, source row count, verified exported row count, and deleted NO EXPIRY rows excluded.
- A script-wide lock prevents overlapping exports; a second run waits up to 30 seconds before failing visibly.
- Each category sheet is checked against its expected row count after writing. The NO EXPIRY CSV is checked for successful non-empty file creation. The combined exported count is reconciled against Supabase rows, accounting for intentionally excluded deleted NO EXPIRY records.
- On failure, the script sends an email alert when `EXPORT_ALERT_EMAIL` is configured. The error is also written to Apps Script execution logs.

## One-time setup
1. Open Google Apps Script at `https://script.google.com` and create a **standalone** project.
2. Replace the starter code in `Code.gs` with the contents of `scripts/google-apps-script/Code.gs` from this repository.
3. In Drive, create a root folder named **MahaMart Expiry Reports**. Copy its folder ID from its URL.
4. In Apps Script: **Project Settings → Script properties**, add:
   - `SUPABASE_URL` = your existing project URL, e.g. `https://YOUR_PROJECT_REF.supabase.co`
   - `SUPABASE_SERVICE_ROLE_KEY` = the Supabase service-role key for that project
   - `ROOT_FOLDER_ID` = the Drive folder ID from step 3
   - Optional `SCHEDULE_JSON_URL` = public schedule JSON URL (a default is already configured).
   - Optional `EXPORT_ALERT_EMAIL` = email address that should receive export failure alerts.
5. Save. Select `exportPreviousBusinessDayNow` and click **Run**. Review permissions and authorize. This creates a first export for the previous business day; check the folder, `_export_log`, and Apps Script **Executions** logs. If you configure `EXPORT_ALERT_EMAIL`, approve the additional email permission when prompted.
6. If the output is correct, run `setupDailyTrigger` once to create the daily trigger.
7. Keep the Apps Script project private. Never paste the service-role key into chat, GitHub, the Android app, or this file. It is only stored in the private Apps Script project's Script Properties.

## Important checks before relying on it
- Confirm the key belongs to the correct Supabase project and has read access to `expiry_entry_uploads`.
- Supabase REST must permit service-role access to the table; if RLS/policies or project settings block it, the script will fail visibly rather than silently export partial data.
- The script uses `session_start` to identify the 3 PM business-day window.
- Test one day and compare its row count against a read-only query in Supabase before relying on the recurring trigger.
- The default schedule JSON URL points at this repository's current branch; if the schedule file changes or the branch becomes private/renamed, set `SCHEDULE_JSON_URL` to the correct public raw-file URL.


## Post-setup validation notes — 2026-10-31

- The user successfully ran `exportPreviousBusinessDayNow` and saw 5 rows exported across two categories.
- The first scheduled execution failed because a filter already existed on a sheet. In `writeSheet_`, remove an existing filter before creating a new one:
  ```javascript
  const existingFilter = sh.getFilter();
  if (existingFilter) existingFilter.remove();
  if (sh.getLastRow() > 1) sh.getRange(1, 1, sh.getLastRow(), headers.length).createFilter();
  ```
- The filter fix is synced into `scripts/google-apps-script/Code.gs`. The script exports only categories present in Supabase rows and moves generated category sheets to trash if they no longer occur in the current result.
- Confirm a later **Time-driven** execution is marked **Completed** before relying on automation. Trigger timing is approximate.
- Sheets are snapshots. Changes/deletions in Supabase are reflected only after a successful rerun for the same business-date window; rows with `session_start` after 3 PM are in the next business session.
- Folder labels use whatever category entries actually exist for that date in `inspection_schedule.json`; some dates in the current JSON contain one scheduled category and others contain two. The script does not invent a second category.


## Reliability improvements — 2026-11-02

- Added a script-wide lock to prevent overlapping scheduled/manual exports. A lock wait timeout is treated as a failure, not silently ignored.
- Added per-category sheet row-count verification and a final reconciliation of source rows against exported rows. Deleted NO EXPIRY records are counted separately because they are intentionally excluded from output.
- The NO EXPIRY CSV must be successfully created and non-empty (it contains a header even when there are no active confirmations).
- `_export_log` now records `status`, `exported_row_count`, `excluded_deleted_no_expiry`, and `no_expiry_csv_rows` alongside the existing fields.
- Failure email alerts are opt-in through the `EXPORT_ALERT_EMAIL` Script Property. Without it, failures still appear in Apps Script **Executions** logs.
- These changes are in GitHub source. They are not live until the updated `Code.gs` is pasted into the standalone Apps Script editor, saved, and authorized.

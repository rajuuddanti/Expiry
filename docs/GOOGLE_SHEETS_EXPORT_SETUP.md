# Daily Google Sheets Export Setup

This Apps Script exports only `public.expiry_entry_uploads` to Google Drive. It does not change or delete Supabase records.

## Output behavior
- Runs daily around 3:15 PM Asia/Kolkata (Google triggers are approximate).
- Exports the previous completed business-day session: previous date 3:00 PM IST inclusive to export date 3:00 PM IST exclusive.
- Drive path: `MahaMart Expiry Reports / YYYY / Month / D MMM <two scheduled categories>`.
- The folder-name categories come from `app/src/main/assets/inspection_schedule.json` for the exported business date, with explanatory text in parentheses removed.
- Creates one Google Sheet per category actually found in the exported rows; all stores are combined and the `location` column is retained.
- Unexpected categories are exported in their own sheet but do not change the folder name.
- Exports all table columns, including `is_deleted`. Empty business days create the dated folder and export log row but no category sheets.
- Pagination is handled in 1,000-row pages. Reruns reuse the same folder and category sheet names and replace their contents. An `_export_log` spreadsheet records the last export per business date.

## One-time setup
1. Open Google Apps Script at `https://script.google.com` and create a **standalone** project.
2. Replace the starter code in `Code.gs` with the contents of `scripts/google-apps-script/Code.gs` from this repository.
3. In Drive, create a root folder named **MahaMart Expiry Reports**. Copy its folder ID from its URL.
4. In Apps Script: **Project Settings → Script properties**, add:
   - `SUPABASE_URL` = your existing project URL, e.g. `https://YOUR_PROJECT_REF.supabase.co`
   - `SUPABASE_SERVICE_ROLE_KEY` = the Supabase service-role key for that project
   - `ROOT_FOLDER_ID` = the Drive folder ID from step 3
   - Optional `SCHEDULE_JSON_URL` = public schedule JSON URL (a default is already configured).
5. Save. Select `exportPreviousBusinessDayNow` and click **Run**. Review permissions and authorize. This creates a first export for the previous business day; check the folder and Apps Script **Executions** logs.
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
- After this edit in the Apps Script editor, the manual export succeeded again. Sync this fix into `scripts/google-apps-script/Code.gs` in the repository; the committed copy may still contain the old filter call.
- Confirm a later **Time-driven** execution is marked **Completed** before relying on automation. Trigger timing is approximate.
- Sheets are snapshots. Changes/deletions in Supabase are reflected only after a successful rerun for the same business-date window; rows with `session_start` after 3 PM are in the next business session.
- Verify the folder name includes both categories from the schedule. Do not treat a folder name showing only one category as proof of the two-category requirement.

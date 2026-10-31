# Implementation Log — 2026-10-31

Repository: `rajuuddanti/Expiry`  
Branch: `feature/new-expiry-upload-table`

## Google Drive / Google Sheets export

### Manual verification
- The standalone Google Apps Script was installed in the user's Google account with Script Properties and authorized.
- Manual `exportPreviousBusinessDayNow` completed and logged **5 rows exported** across two actual categories.
- The manual export was run again after the filter fix and completed successfully with the same 5-row result.

### Scheduled-run failure and fix
- The first time-driven run occurred around **3:19:40 PM IST on 2026-10-31** and failed with:
  `Exception: You can't create a filter in a sheet that already has a filter.`
- The failure was caused by attempting to create a new filter on a sheet that already had one.
- User changed the Apps Script `writeSheet_` logic to remove an existing filter before creating it again:
  ```javascript
  const existingFilter = sh.getFilter();
  if (existingFilter) existingFilter.remove();
  if (sh.getLastRow() > 1) sh.getRange(1, 1, sh.getLastRow(), headers.length).createFilter();
  ```
- The subsequent manual run succeeded. **The automatic trigger has not yet been confirmed successful after the fix.**

### Remaining checks
1. Inspect Apps Script **Executions** after the next time-driven run; confirm status **Completed**.
2. Confirm the trigger's actual time window. If changing it to around 4 PM, remove the old trigger and run `setupDailyTrigger` once to create the replacement. Google Apps Script timing is approximate.
3. Confirm the dated folder label includes both categories scheduled for that business date. The last observed folder label was `30 Oct MASALA SPICES`, which requires checking against the schedule JSON.
4. Verify idempotent behavior by rerunning and confirming the same category Sheets are updated rather than duplicated.
5. If testing deletion behavior, delete only a disposable test row and check the same business-date sheet after a successful export rerun.

### Data and security behavior
- Export reads only `public.expiry_entry_uploads` and filters by `session_start` for the previous completed 3 PM–to–3 PM Asia/Kolkata business window.
- Supabase rows are not deleted by the export. Google Sheets do not live-sync; a successful rerun rewrites the sheet using the rows returned at that time.
- Entries created after 3 PM belong to the next business session.
- Service-role key stays only in the private Apps Script Script Properties. It must never be committed or included in the Android app.

### Repository synchronization note
The fix was made in the user's Apps Script editor. At the time of this log, the committed `scripts/google-apps-script/Code.gs` still contains the old filter-creation line. Syncing the script fix into the repository is a separate code update and remains outstanding.

## Verification boundary
This log records observed Apps Script output and the reported trigger failure. It does not claim that the automatic trigger has passed, nor that the folder naming requirement has been fully verified.

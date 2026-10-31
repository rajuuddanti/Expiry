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
The repository script was updated after the original log was written. Commit `ce85db629f30610280f802e9e18a76b70698637d` syncs the existing-filter removal and adds empty-day handling. The setup guide and implementation status were updated in commits `eb08403dcfedb8113ef9e70b84ababb88b7bb048` and `140c4c2060607e8a2fe2dfd3a1e11a9dd7f83842`.

### Empty business-day behavior — repository update
- Scheduled categories for the exported business date are seeded as groups even when Supabase returns zero rows, so their sheets are created with headers only.
- Existing category sheets in that day's folder are also rewritten on rerun. If their category has no current rows, the sheet is cleared to headers only instead of retaining stale rows.
- The export log records row count `0` and the categories/sheets refreshed. The script remains read-only toward Supabase.
- The live Apps Script project must be updated by pasting the current repository `Code.gs`; a GitHub commit does not automatically update the deployed standalone Apps Script.
- This new empty-day path has not yet been tested in the live Apps Script environment.

## Verification boundary
This log records observed Apps Script output and the reported trigger failure. It does not claim that the automatic trigger has passed, nor that the folder naming requirement has been fully verified.

# NO EXPIRY FOUND — Implementation Notes

Branch: `feature/new-expiry-upload-table`

## Behavior added in source
- Settings includes a `NO EXPIRY FOUND` action.
- The action records a local Room history row for the current store and current 3 PM business session.
- The record uses `expiry = 'NO EXPIRY'`, product label `NO EXPIRY FOUND`, quantity 0, blank SKU/scanned-code/expiry date, and today's scheduled categories in the category field.
- Ordinary product records default to `expiry = 'YES'`.
- History renders the confirmation clearly and uses the existing soft-delete flow; deleted rows are queued for upload like other rows.
- CSV export includes an `Expiry Status` column.
- The upload payload includes `expiry`; `expiry_date` is sent as SQL NULL for no-expiry confirmations.
- Room schema version 4 migration adds `expiry TEXT NOT NULL DEFAULT 'YES'` without dropping existing history.
- The Apps Script export header includes the `expiry` column.

## Required backend step
Run `supabase/migrations/20261101_add_no_expiry_status.sql` in the same Supabase project used by the app before testing the button. It adds the column, allows NULL expiry dates and quantity 0 for the special confirmation, and adjusts the upload policies while preserving existing grants and the conflict-key SELECT policy.

## Verification status
GitHub source changes are committed, but Android build/runtime and live Supabase migration are **not verified** from this commit alone. Do not start multi-device testing until:
1. Apply the SQL migration in Supabase.
2. Build/install the updated APK on one device.
3. Tap `NO EXPIRY FOUND`, verify the history record, delete it, and verify deletion sync.
4. Verify normal expiry records still upload with `expiry = YES`.
5. Confirm Google Sheets shows the `expiry` column.
Then proceed to multi-device testing across different stores.

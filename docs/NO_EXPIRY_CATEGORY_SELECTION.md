# NO EXPIRY category selection and CSV export

- Uses categories scheduled for the existing 3 PM–3 PM business day.
- One scheduled category: selected automatically.
- Two scheduled categories: manager selects which category has no expiry.
- NO EXPIRY row keeps SKU, name, scanned code, category and expiry date blank; quantity remains 0.
- Selected category is stored separately in `no_expiry_category`.
- Daily export creates `no expiry.csv` in that day's existing Drive folder with columns `Store name, Date, No expiry`.
- Soft-deleted confirmations are excluded. Normal category sheets continue to contain normal expiry rows only.
- Run `supabase/migrations/20261101_add_no_expiry_category.sql` in Supabase before installing the updated app.
- Replace the Apps Script project code with `scripts/google-apps-script/Code.gs`.
- This repo update does not itself build/install an APK or deploy Apps Script.


## 2026-11-02 — Entry-blocking rule

The schedule has at most two categories per business day. After a NO EXPIRY confirmation is saved:

- If only one category is scheduled, block all new entries for the business day, including unscheduled categories.
- If two categories are scheduled and only one is marked, block that marked category only. The other scheduled category and all unscheduled categories remain available from the main screen.
- If both scheduled categories are marked, block all new entries for the business day.
- Apply this check when saving either a scanned or manually entered product. Do not disable the main screen while entries remain allowed.
- Ignore soft-deleted NO EXPIRY confirmations.


## 2026-11-02 — Prevent NO EXPIRY when active expiry entries exist

Before saving a NO EXPIRY confirmation for a category, check the current 3 PM–3 PM business-day history for the selected store. If at least one active, non-NO-EXPIRY entry exists for that category, refuse the confirmation and tell staff to delete those entries from History first. Soft-deleted entries do not count. Categories are checked independently; for a two-category day, existing entries in one category do not prevent NO EXPIRY for the other category. Keep the check inside the save coroutine immediately before inserting the NO EXPIRY row.

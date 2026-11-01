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

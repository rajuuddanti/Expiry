# Next Chat Handoff — Mahalaxmi Expiry Manager

Last updated: 2026-10-26 (Asia/Kolkata)

## Paste this into a new chat

Continue the Mahalaxmi Expiry Manager project from the GitHub repository `rajuuddanti/Expiry`. First read:
- `README.md`
- `docs/PROJECT_CONTEXT.md`
- `docs/DATA_MAPPING_AND_IMPORT.md`
- `docs/SQL_REFERENCE.md`
- `docs/DECISIONS_AND_CHANGELOG.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `supabase/migrations/001_initial_schema.sql`

### Current exact status
I imported my stacked two-column product master CSV into `public.product_master_staging` in my own Supabase dashboard. The assistant is NOT connected to my Supabase project, so do not claim to query or change it directly. Give me SQL to run and ask me to share results when needed.

The source has `SkuCode/EAN` and `SkuLongName`. SKU codes occupy approximately the first 28–30K rows, and EAN rows are stacked after them. Verify the boundary from staging data rather than guessing. The user wants one `scan_code` column in `product_master`, so either SKU or EAN can be scanned.

Code normalization: if any code has fewer than five digits, left-pad to five digits. If five or more digits, leave it unchanged. Keep codes as text.

Mapping: a SKU row maps `scan_code = sku_code = normalized source code`, with the source name. An EAN row maps its `scan_code` to the normalized EAN, then reverse-look up the first SKU-master row with the same product name (FCFS). Duplicate names are resolved by the first matching SKU row in original source order. Do not overcomplicate this or silently choose any later duplicate.

Live `product_master` screenshot showed only three required TEXT columns: `scan_code`, `sku_code`, `sku_name`. The direct import failed with `scan_code = NULL`, because the two-column CSV doesn't directly match the destination schema. Do not retry direct import.

Latest staging SQL provided:
```sql
CREATE TABLE IF NOT EXISTS public.product_master_staging (
    row_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_code TEXT,
    source_name TEXT
);
```
User says import is done. Next: verify actual staging schema, row count, sample rows and order. Use diagnostics in `docs/SQL_REFERENCE.md`. Then show mapping preview and counts before giving final insert/upsert SQL. Do not delete or overwrite production data without preview/approval.

### App requirements
Android/Kotlin expiry capture app for Mahalaxmi Mahamart:
- store selection persisted locally, editable in Settings;
- scan or manually enter a SKU/EAN;
- autofill canonical SKU and product name;
- quantity and expiry date with calendar picker;
- Room/offline-first storage and retryable sync;
- history session from 3 PM IST to next day 3 PM;
- preserve unsynced rows; soft-delete expiry records;
- only two production Supabase tables: `product_master` and `expiry_entries`;
- no category table/field;
- never put service-role key in APK.

### Be accurate
Do not claim SQL was executed by the assistant, Supabase is connected, or Android app is buildable/tested unless verified. Check repository files before making code changes. Commit Markdown docs to the repo when I ask to “push .md files”.

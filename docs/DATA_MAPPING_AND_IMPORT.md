# Product Master Data Mapping and Import

Last updated: 2026-10-26

## Source format

The original Excel file has two source columns:
- `SkuCode/EAN` — contains a code, either an internal SKU or an EAN/barcode.
- `SkuLongName` — product name.

The user intentionally stacked SKU rows and EAN rows vertically in the same column. Do not require separate SKU and EAN source columns: the Android scanner should search one `scan_code` field regardless of code type.

The user describes approximately the first 28–30K rows as SKU codes in sequence, with EAN rows below. Verify the actual row boundary before writing the final import. Preserve source order.

## Destination table

The live table structure shown in the user's Supabase screenshot:
```sql
public.product_master (
  scan_code TEXT NOT NULL,
  sku_code  TEXT NOT NULL,
  sku_name  TEXT NOT NULL
)
```

The screenshot does not show defaults. Earlier import errors prove `scan_code` is required. The initial migration committed in the repository has additional columns (`ean`, `is_active`, `updated_at`) but the user's live screenshot showed only three columns. Therefore the checked-in migration and the user's live table are currently inconsistent. Do not assume applying the migration again will alter an existing table; `CREATE TABLE IF NOT EXISTS` does not add missing columns.

## Intended mapping

For each source row, after normalizing the code:
- SKU-master row: `scan_code = normalized SKU code`, `sku_code = normalized SKU code`, `sku_name = source name`.
- EAN row: `scan_code = normalized EAN`, `sku_code = the first SKU-master row with the same product name (FCFS)`, `sku_name = source name`.

Illustrative example only:
| scan_code | sku_code | sku_name |
|---|---|---|
| `100002` | `100002` | Sugar |
| `890910452145` | `100002` | Sugar |

If `Sugar` appears against multiple SKU codes in the SKU section, the first SKU row from top to bottom is canonical, by user's explicit decision.

## Normalization

Use text operations only. For a code consisting of digits:
- length < 5: left-pad to length 5 using zeroes.
- length >= 5: preserve as-is.
- Do not strip leading zeros already present.
- Do not truncate 6-digit SKU codes or long EANs.
- Trim accidental surrounding whitespace from source values, but do not alter internal characters.
- Blank codes/names should be counted and reported rather than inserted into NOT NULL destination columns.

A PostgreSQL expression for a digits-only code is:
```sql
CASE
  WHEN btrim(code_text) ~ '^[0-9]+$'
       AND length(btrim(code_text)) < 5
    THEN lpad(btrim(code_text), 5, '0')
  ELSE btrim(code_text)
END
```
Use a safe alias instead of the placeholder `code_text` in real queries.

## Current staging workflow

The assistant previously instructed creating a staging table and importing the original two-column CSV there. The latest SQL shown to the user was:
```sql
CREATE TABLE IF NOT EXISTS public.product_master_staging (
    row_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_code TEXT,
    source_name TEXT
);
```

The user then said: “import done broo”. We have not verified whether the actual staging table was created with this exact definition, nor whether the CSV import tool mapped `SkuCode/EAN` to `source_code` and `SkuLongName` to `source_name`. Confirm with read-only SQL first.

Recommended diagnostic SQL (run in the user's Supabase SQL Editor):
```sql
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('product_master', 'product_master_staging')
ORDER BY table_name, ordinal_position;

SELECT
  COUNT(*) AS staging_rows,
  COUNT(*) FILTER (WHERE NULLIF(BTRIM(source_code), '') IS NULL) AS blank_codes,
  COUNT(*) FILTER (WHERE NULLIF(BTRIM(source_name), '') IS NULL) AS blank_names,
  MIN(row_id) AS first_row_id,
  MAX(row_id) AS last_row_id
FROM public.product_master_staging;

SELECT row_id, source_code, source_name
FROM public.product_master_staging
ORDER BY row_id
LIMIT 20;
```

If the staging table does not have `row_id`, adapt the diagnostic; do not drop/recreate the staging table without checking its contents.

## Important unresolved issue: row order

The original sheet's order is meaningful for the user's FCFS rule. The recommended staging table uses identity `row_id`, which reflects CSV insert order only if import preserves sequential order. Verify samples and the row boundary. Do not assume row_id corresponds exactly to original spreadsheet row order without confirmation.

## Import sequence (do not skip validation)

1. Inspect staging schema and counts.
2. Sample first 30 rows and rows around the expected SKU/EAN boundary.
3. Establish and verify the boundary between SKU master rows and EAN rows. Do not infer solely from code length: user says codes shorter than 5 are padded, and SKU codes can be 5 or 6 digits while EANs can be long.
4. Build a deterministic SKU-name lookup from SKU rows ordered by original row order. First matching product name wins.
5. Normalize every code as text.
6. Validate blanks, conflicting duplicate scan codes, and EAN rows whose product name has no SKU-master match.
7. Produce counts before inserting.
8. Insert/upsert into `product_master` only after the user confirms the mapping preview/counts, unless the operation is strictly additive and has been clearly previewed.
9. Validate post-import totals and example lookups for both SKU and EAN scans.

## Error already observed

Direct CSV import into `product_master` failed:
`ERROR: 23502: null value in column "scan_code" of relation "product_master" violates not-null constraint. DETAIL: Failing row contains (null, 00001, DISCONTINUED 4065).`

This happened because the two-column CSV was not correctly transformed to populate the required `scan_code` column. Do not retry direct import of the original two-column CSV into `product_master`.

## Security note

This document describes SQL to be run by the user in their own Supabase SQL Editor. The assistant's Supabase connector is not connected to the user's Expiry project. Never imply that the live project was queried or modified by the assistant.

# Supabase SQL Reference

Last updated: 2026-10-26

These statements are a reference for the user to run in their own Supabase SQL Editor. The assistant does not have a connected Supabase session for the user's Expiry project.

## 1. Inspect both table schemas

```sql
SELECT
    table_name,
    column_name,
    data_type,
    is_nullable,
    column_default,
    ordinal_position
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('product_master', 'product_master_staging')
ORDER BY table_name, ordinal_position;
```

The user's screenshot showed the live `product_master` columns as three required TEXT columns:
- `scan_code`
- `sku_code`
- `sku_name`

## 2. Inspect staging import

Expected staging schema from the latest instruction:
```sql
CREATE TABLE IF NOT EXISTS public.product_master_staging (
    row_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_code TEXT,
    source_name TEXT
);
```

Inspect counts:
```sql
SELECT
    COUNT(*) AS staging_rows,
    COUNT(*) FILTER (WHERE NULLIF(BTRIM(source_code), '') IS NULL) AS blank_codes,
    COUNT(*) FILTER (WHERE NULLIF(BTRIM(source_name), '') IS NULL) AS blank_names,
    MIN(row_id) AS first_row_id,
    MAX(row_id) AS last_row_id
FROM public.product_master_staging;
```

Inspect first rows:
```sql
SELECT row_id, source_code, source_name
FROM public.product_master_staging
ORDER BY row_id
LIMIT 30;
```

Inspect rows around a proposed boundary (replace 30000 with the boundary being checked):
```sql
SELECT row_id, source_code, source_name
FROM public.product_master_staging
WHERE row_id BETWEEN 29990 AND 30010
ORDER BY row_id;
```

Do not assume the boundary is exactly 30,000; the user said approximately 28–30K rows.

## 3. Normalize code strings

For numeric-looking codes, add leading zeroes only when length is under 5:
```sql
CASE
  WHEN BTRIM(source_code) ~ '^[0-9]+$'
       AND LENGTH(BTRIM(source_code)) < 5
    THEN LPAD(BTRIM(source_code), 5, '0')
  ELSE BTRIM(source_code)
END
```

This must remain TEXT. Never cast the result to an integer/numeric type, because that removes leading zeros. Do not truncate long codes.

## 4. Check duplicate names among the presumed SKU section

Only run after verifying the SKU/EAN boundary. Replace 30000 with the verified last SKU row:
```sql
WITH sku_rows AS (
  SELECT
    row_id,
    BTRIM(source_code) AS raw_code,
    BTRIM(source_name) AS sku_name,
    CASE
      WHEN BTRIM(source_code) ~ '^[0-9]+$'
           AND LENGTH(BTRIM(source_code)) < 5
        THEN LPAD(BTRIM(source_code), 5, '0')
      ELSE BTRIM(source_code)
    END AS normalized_sku
  FROM public.product_master_staging
  WHERE row_id <= 30000
    AND NULLIF(BTRIM(source_code), '') IS NOT NULL
    AND NULLIF(BTRIM(source_name), '') IS NOT NULL
)
SELECT sku_name, COUNT(*) AS sku_count,
       ARRAY_AGG(normalized_sku ORDER BY row_id) AS sku_codes_in_fcfs_order
FROM sku_rows
GROUP BY sku_name
HAVING COUNT(*) > 1
ORDER BY sku_name;
```

## 5. Preview mapping before writing

Do not run a final INSERT/UPSERT until the SKU/EAN boundary and the staging import order are verified. Use a CTE mapping:
- SKU rows: `scan_code = normalized code`, `sku_code = normalized code`, `sku_name = source name`.
- EAN rows: `scan_code = normalized EAN`, `sku_code = first SKU code with the same name ordered by original row order`, `sku_name = source name`.

FCFS means first SKU-master row by row order wins when multiple SKU codes have the same product name. Any EAN name with no SKU name match must be reported rather than assigned a guessed SKU.

## 6. Previous table creation SQL

This was suggested earlier but may not match the existing live table; do not blindly re-run as a schema update:
```sql
CREATE TABLE IF NOT EXISTS public.product_master (
    scan_code TEXT PRIMARY KEY,
    sku_code TEXT NOT NULL,
    sku_name TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_product_master_sku_code
    ON public.product_master (sku_code);

CREATE INDEX IF NOT EXISTS idx_product_master_sku_name
    ON public.product_master (sku_name);
```

The repository's initial migration `supabase/migrations/001_initial_schema.sql` includes extra columns on `product_master` and additional columns on `expiry_entries`; it does not match the three-column live screenshot exactly. `CREATE TABLE IF NOT EXISTS` does not alter an existing table.

## 7. Do not do these things

- Do not directly import the original two-column CSV into the three-required-column `product_master` table.
- Do not drop tables or truncate data to fix the import.
- Do not overwrite existing product mappings until a preview has been checked.
- Do not assume the assistant has connected to Supabase.
- Do not embed service-role credentials in the Android app.

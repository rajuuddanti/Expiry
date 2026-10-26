# SQL Schema Reference — Mahalaxmi Expiry Manager

Last updated: 2026-10-26

This document records the SQL schema committed in the repository and distinguishes it from the live Supabase table structure the user showed in screenshots. The assistant is not connected to the user's Supabase project and has not executed these SQL statements there.

## Existing repository migration

Canonical checked-in file: `supabase/migrations/001_initial_schema.sql`

It defines two production tables:

### `public.product_master` (as defined in repository migration)

```sql
create table if not exists public.product_master (
    scan_code text primary key,
    sku_code text not null,
    sku_name text not null,
    ean text,
    is_active boolean not null default true,
    updated_at timestamptz not null default now()
);

create index if not exists product_master_sku_code_idx
    on public.product_master (sku_code);
create index if not exists product_master_ean_idx
    on public.product_master (ean);
create index if not exists product_master_active_idx
    on public.product_master (is_active);
```

### `public.expiry_entries` (as defined in repository migration)

```sql
create table if not exists public.expiry_entries (
    id uuid primary key default gen_random_uuid(),
    client_record_id uuid not null unique,
    store_name text not null,
    sku_code text not null,
    sku_name text not null,
    ean text,
    qty integer not null check (qty > 0),
    expiry_date date not null,
    session_start timestamptz not null,
    created_at timestamptz not null default now(),
    device_created_at timestamptz,
    uploaded_at timestamptz not null default now(),
    is_deleted boolean not null default false,
    deleted_at timestamptz,
    exported_at timestamptz,
    constraint expiry_entries_session_start_3pm_ist check (
        (session_start at time zone 'Asia/Kolkata')::time = time '15:00:00'
    )
);

create index if not exists expiry_entries_store_session_idx
    on public.expiry_entries (store_name, session_start);
create index if not exists expiry_entries_expiry_date_idx
    on public.expiry_entries (expiry_date);
create index if not exists expiry_entries_deleted_idx
    on public.expiry_entries (is_deleted);
create index if not exists expiry_entries_created_at_idx
    on public.expiry_entries (created_at);
```

The migration also runs `create extension if not exists pgcrypto;` and enables Row Level Security on both tables. It deliberately does not create unrestricted anonymous policies. Before production, implement a controlled read/write path; never embed a service-role key in the Android app.

## Live schema observed from user's screenshot

The user's screenshot of their own Supabase Table Editor showed `public.product_master` with exactly these three columns, all TEXT and NOT NULL:

```sql
scan_code text not null,
sku_code  text not null,
sku_name  text not null
```

The screenshot did not establish whether `scan_code` is the primary key or show the full `expiry_entries` schema. Do not infer unseen live schema details.

**Important mismatch:** the repository migration defines extra columns `ean`, `is_active`, and `updated_at` on `product_master`, whereas the live screenshot showed only three columns. PostgreSQL `CREATE TABLE IF NOT EXISTS` does not modify a table that already exists. Do not rerun or edit migrations blindly as a way to alter the user's live database.

## Current temporary import table

The last SQL given in chat for the staging import was:

```sql
CREATE TABLE IF NOT EXISTS public.product_master_staging (
    row_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_code TEXT,
    source_name TEXT
);
```

The user said the CSV import into staging completed. The actual schema and row count still need to be verified by SQL the user runs in their own Supabase SQL Editor.

## Required master mapping logic

Source columns: `SkuCode/EAN` and `SkuLongName`. SKU rows and EAN rows are stacked vertically in the same source-code column.

- Keep all codes as text.
- If a code has fewer than five digits, left-pad with zeros to five digits. Leave codes of five or more digits unchanged.
- SKU row: `scan_code = normalized source_code`, `sku_code = normalized source_code`, `sku_name = source_name`.
- EAN row: `scan_code = normalized source_code`; `sku_code` is the first SKU-master code with an exact matching product name (FCFS, first row in original SKU section order); `sku_name = source_name`.
- The user estimates the first 28–30K rows are SKU rows. Confirm the exact boundary from staging samples before final import.
- Preview row counts, blanks, unmatched names and duplicate scan codes before inserting into production.

## Diagnostic SQL for the user's Supabase SQL Editor

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
  AND table_name IN (
      'product_master',
      'product_master_staging',
      'expiry_entries'
  )
ORDER BY table_name, ordinal_position;
```

If the staging schema matches the latest instructions, inspect the import:

```sql
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
LIMIT 30;

SELECT row_id, source_code, source_name
FROM public.product_master_staging
WHERE row_id BETWEEN 29990 AND 30010
ORDER BY row_id;
```

Do not run these staging queries until confirming the actual staging column names. If they differ, adjust the query to match the live schema.

## SQL history

- Original table-creation SQL is in `supabase/migrations/001_initial_schema.sql`.
- Staging-table SQL and safe import diagnostics are also in `docs/SQL_REFERENCE.md`.
- This file is the quick index of schema definitions and their known differences.

# Supabase SQL Reference — Current Direction

Last updated: 2026-10-28 (Asia/Kolkata)

## Important

This file is a read-only query reference for the latest reported backend state. The assistant is not assumed to be connected to the user's Supabase project. Run queries in the user's own Supabase SQL Editor and share results if further analysis is needed.

Do not run the older `product_master` / `product_master_staging` DDL below from prior versions of this file. The latest reported product table is `public.MASTER`, and the user explicitly does not want its schema changed. Do not change Supabase URL/key configuration.

## 1. Inspect the current reported tables

```sql
select table_schema, table_name
from information_schema.tables
where table_schema = 'public'
  and table_name in ('MASTER', 'expiry_return_rules')
order by table_name;
```

## 2. Inspect the MASTER columns without modifying them

```sql
select column_name, data_type, is_nullable, ordinal_position
from information_schema.columns
where table_schema = 'public'
  and table_name = 'MASTER'
order by ordinal_position;
```

Expected columns from user-reported schema: `SkuCode`, `SkuLongName`, `Catg`. Preserve exact case/quoted identifiers when querying.

## 3. Check master row and blank counts

```sql
select
  count(*) as total_rows,
  count(*) filter (where nullif(btrim("SkuCode"::text), '') is null) as blank_sku_codes,
  count(*) filter (where nullif(btrim("SkuLongName"::text), '') is null) as blank_names,
  count(*) filter (where nullif(btrim("Catg"::text), '') is null) as blank_categories
from public."MASTER";
```

This is read-only. It may be expensive on a large table, so run when needed rather than repeatedly.

## 4. Inspect active return rules

```sql
select rule_type, match_value, return_days_before_expiry, priority, note
from public.expiry_return_rules
where is_active = true
order by rule_type, priority desc, match_value;
```

Expected recorded state: 30 category rules and 2 brand rules. Brand overrides are HIMALAYA = 60 days and KELLOGGS = 30 days. These are historical reported values; query to confirm current values if necessary.

## 5. Check for duplicate active rules

```sql
select rule_type, upper(btrim(match_value)) as normalized_match, count(*) as rule_count
from public.expiry_return_rules
where is_active = true
group by rule_type, upper(btrim(match_value))
having count(*) > 1
order by rule_type, normalized_match;
```

## Safety constraints

- All statements above are read-only SELECT queries.
- Do not drop/truncate `MASTER` or alter its columns.
- Do not recreate the old `product_master`, `product_master_staging`, or cloud `expiry_entries` design without a fresh explicit decision.
- Do not reintroduce cloud expiry-upload/device/security tables; current requirement is local expiry records with CSV export/share.
- Do not claim a query was run or data verified unless the actual results are available.

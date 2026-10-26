# Supabase Schema & Sync Handoff
Updated: 2026-10-26
Project: `Expiry` (`tmdswprfiaiywzrwzucj`)
Region: `ap-southeast-1`
Repository branch: `feature/new-expiry-upload-table`

This document reflects the schema observed in the connected Supabase project on 2026-10-26. Re-check the live schema before applying future migrations.

## public.MASTER
Observed columns:
| Column | Type | Nullable | Notes |
|---|---|---:|---|
| `SkuCode` | text | yes | SKU identifier |
| `SkuLongName` | text | yes | Product display name |
| `Catg` | text | yes | Category field added for category matching |

Observed row count: 99,686. No primary key was reported by schema introspection. No EAN/barcode column was reported. Do not assume the EAN column name or fabricate a mapping. Confirm source and schema before implementing barcode lookup.

## public.expiry_scan_uploads
Observed columns:
| Column | Type | Nullable | Default / constraint |
|---|---|---:|---|
| `id` | uuid | no | `gen_random_uuid()`, primary key |
| `skuname` | text | no | |
| `skucode` | text | no | |
| `ean` | text | no | |
| `qty` | integer | no | default 1; CHECK qty > 0 |
| `expirydate` | date | no | |
| `is_deleted` | boolean | no | default false |
| `device_id` | text | yes | |
| `owner_id` | uuid | no | FK to `auth.users(id)`, ON DELETE CASCADE |
| `created_at` | timestamptz | no | default now() |
| `updated_at` | timestamptz | no | default now() |
| `store_name` | text | yes | |

RLS is enabled. The table was reported with zero rows at inspection time.

## Migration history in repository
- `supabase/migrations/001_initial_schema.sql`
- `supabase/migrations/002_secure_android_sync.sql`
- `supabase/migrations/003_create_expiry_scan_uploads.sql`

The `Catg` column was added directly to live `public.MASTER` with:
```sql
alter table public."MASTER"
add column if not exists "Catg" text;
```
This live change should also be represented in a checked-in migration so a fresh environment can reproduce it. Before adding a new migration, inspect the existing migration files and confirm whether an equivalent migration already exists.

## Data/sync contract
- Android sync writes expiry scan records to `public.expiry_scan_uploads`.
- Use `owner_id` for authenticated ownership and honor existing RLS policies.
- Explicit deletion is soft deletion (`is_deleted=true`), not physical deletion.
- The 3 PM daily local reset clears only device-local daily history. It must not issue DELETE/TRUNCATE against Supabase and must not mass-mark rows deleted.
- Preserve the same `id` when syncing updates to an existing row.
- Verify whether the current Android client uses `store_name`, `device_id`, and `updated_at` consistently before changing the schema.
- Never place Supabase service-role keys in the Android app. Use the intended publishable/anon key with RLS.
- Run Supabase security and performance advisors after schema/RLS changes.

## Important gaps to resolve
1. `public.MASTER` currently has no EAN/barcode field in the inspected schema; locate the real source/mapping before enabling EAN lookup.
2. The current schema introspection reported no primary key on `MASTER`; do not add one blindly to a large imported table without assessing duplicates and app usage.
3. The new daily schedule data model has not yet been implemented. Decide whether to bundle the Excel schedule as an app asset or import it into a dedicated table; either way preserve dates and category labels exactly through 2027-12-31.

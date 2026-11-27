# Store Name Normalization — Data Cleanup Handoff

Last updated: 2026-11-27 (Asia/Kolkata)

## Purpose

Normalize historical `location` values in the existing `public.expiry_entry_uploads` table so records for the same physical store use one canonical store name. This is a prerequisite to reliable store-scoped login and archive recovery.

## Source of truth for mappings

- The user supplied an Excel workbook named `Change to.xlsx` containing 21 existing-to-canonical store-name mappings.
- Reopen and inspect that exact workbook before preparing any SQL or updating rows.
- Do not infer mappings from spelling similarity or invent canonical names.
- Present a before/after mapping preview and affected row counts for approval before any live update.

## Known Gangadhara variants

A read-only inspection on 2026-11-27 reported the following values in `public.expiry_entry_uploads`:

| Existing location | Rows |
|---|---:|
| `Gangadhara` | 4 |
| `gangadhara-1` | 8 |
| `Gangadhara new` | 36 |
| `Gangadhara New` | 1 |

The user explicitly stated that `Gangadhara` and `Gangadhara-1` refer to the same store and should be consolidated to `Gangadhara`.

The capitalization-only difference between `Gangadhara new` and `Gangadhara New` was raised for confirmation; do not normalize those values without the user's decision and the workbook mapping.

## Safety requirements for the update

- Update only the location/store-name field.
- Preserve row IDs and every other column.
- Preserve `is_deleted` values and all timestamps.
- Do not delete, merge, or recreate records.
- Do not modify the product master, schedule, return rules, auth, or any other table.
- First run read-only counts for every old value and confirm the proposed target value.
- Use a transaction or an equivalently safe batch operation where supported.
- After approval and update, rerun counts and verify no old mapped values remain.
- Report exact rows affected and any values not found.

## Current status

- Read-only count snapshot above was observed on 2026-11-27.
- No live row updates have been executed.
- The workbook mapping list still needs to be reopened and checked before proceeding.
- The user has not yet confirmed whether `Gangadhara New` and `Gangadhara new` should be treated as the same canonical value.

## Next step

Reopen `Change to.xlsx`, extract all 21 mappings, produce a dry-run preview with row counts, and wait for explicit approval before writing to Supabase.

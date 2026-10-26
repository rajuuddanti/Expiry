# Implementation plan

## Data model
- [x] Exactly two production tables: `product_master` and `expiry_entries`
- [x] No category field/table; source master has `SkuCode/EAN` and `SkuLongName`
- [x] One searchable `scan_code` field handles both SKU codes and EANs
- [x] Preserve codes as text to retain leading zeros
- [x] Normalize codes shorter than 5 digits by left-padding to 5; leave longer codes unchanged
- [x] Duplicate product names use first matching SKU row in source order (FCFS), as requested by user
- [ ] Verify staging import schema, row count and original row order
- [ ] Verify the SKU/EAN section boundary (user estimates first 28–30K rows are SKU codes)
- [ ] Preview mappings and report blanks, unmatched names and conflicting duplicate scan codes
- [ ] Import validated mappings into `product_master`

## Master import rules
- Source columns: `SkuCode/EAN`, `SkuLongName`; SKU/EAN rows are stacked vertically in the same code column.
- SKU row: `scan_code = normalized code`, `sku_code = normalized code`, `sku_name = source name`.
- EAN row: `scan_code = normalized EAN`; reverse lookup SKU by exact product name against SKU rows ordered by source order; first match wins.
- Keep all codes as text. For digit-only codes under 5 characters, left-pad to 5. Do not truncate 5+ digit codes.
- Keep distinct EAN scan codes. Exact duplicate scan codes cannot conflict with the destination primary key; report conflicting mappings before writing.
- Do not guess or silently assign an EAN whose name has no SKU-name match; report it for review.
- Never directly import the original two-column CSV into the three-required-column `product_master` table.

## Android
- [ ] Confirm/complete Kotlin project and Gradle wrapper
- [ ] Store selection saved locally and editable in Settings
- [ ] Camera barcode scan + manual code entry
- [ ] Exact lookup against `product_master.scan_code`
- [ ] Auto-fill SKU code and product name
- [ ] Quantity + calendar expiry picker
- [ ] Current 3 PM IST session history
- [ ] Soft delete via `is_deleted = true`
- [ ] Room offline persistence and retryable sync
- [ ] Build and test on device before claiming readiness

## Supabase / office
- [x] Initial two-table schema checked into repository
- [ ] Controlled secure read/write path (no service-role key in APK)
- [ ] Validate and import full master with a report
- [ ] Export by store/session including optional deleted rows
- [ ] Safe export/archive before server-side clearing
- [ ] Verify RLS policies and permissions before production use

## Non-negotiable session/sync rules
1. At 14:59:59, entries belong to the prior day's 3 PM session; at 15:00:00, they belong to the new session.
2. App history is filtered by calculated session, not by app launch or server table contents.
3. Never remove unsynced local records.
4. Manager delete sets `is_deleted=true`; never issue SQL DELETE for an expiry entry.
5. Use `client_record_id` as an idempotency key.
6. Clearing/exporting server rows must not reset local app state.
7. The user's Supabase project is not connected to the assistant's Supabase connector. Provide SQL and have the user run it; never claim direct access.

# Implementation plan

## Data model
- [x] Exactly two Supabase tables: `product_master` and `expiry_entries`
- [x] No category field; source master has only `SkuCode/EAN` and `SkuLongName`
- [x] Scan lookup supports internal SKU codes and EAN/barcodes
- [x] Preserve codes as text to retain leading zeros
- [ ] Validate full source import and flag ambiguous code mappings

## Master import rules
- Read both source columns as text.
- Internal SKU codes are 5 or 6 digits (examples: `00001`, `100002`).
- Preserve all distinct EAN/barcode values; multiple EANs may map to one SKU.
- Deduplicate only exact repeated mappings.
- Do not guess SKU-to-EAN relationships if the two-column source does not establish them unambiguously.
- Do not create or infer category.

## Android
- [ ] Kotlin project and Gradle wrapper
- [ ] Store selection saved locally
- [ ] Camera barcode scan + manual code entry
- [ ] Exact lookup against `product_master.scan_code`
- [ ] Auto-fill SKU code, name, EAN
- [ ] Quantity + calendar expiry picker
- [ ] Current 3 PM IST session history
- [ ] Soft delete via `is_deleted = true`
- [ ] Room offline persistence and retryable sync

## Supabase / office
- [x] Initial two-table schema
- [ ] Controlled secure read/write path (no service-role key in APK)
- [ ] Master CSV/XLSX importer with validation report
- [ ] Export by store/session including optional deleted rows
- [ ] Safe export/archive before server-side clearing

## Non-negotiable session/sync rules
1. At 14:59:59 entries belong to the prior day's 3 PM session; at 15:00:00 they belong to the new session.
2. App history is filtered by calculated session, not by app launch or server table contents.
3. Never remove unsynced local records.
4. Manager delete sets `is_deleted=true`; never issue SQL DELETE.
5. Use `client_record_id` as an idempotency key.
6. Clearing/exporting server rows must not reset local app state.

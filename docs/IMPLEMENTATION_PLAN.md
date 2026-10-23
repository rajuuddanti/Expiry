# Implementation plan

## Phase 1 — Data contract and UX
- [x] Define 3 PM IST session rule
- [x] Define offline-first / local history requirements
- [x] Define soft-delete field and central record contract
- [x] Confirm master code rule: 5/6 digit values are internal SKU codes; other barcode values are EAN/barcode candidates
- [x] Confirm a SKU may map to multiple EANs and a scan can use either SKU or EAN
- [ ] Validate the full master and flag ambiguous mappings before import

## Master import and lookup rules
- Source columns: `SkuCode/EAN`, `SkuLongName`.
- Treat all codes as text to preserve leading zeros.
- Build one canonical `product_master` row per internal SKU and a separate `product_barcodes` mapping table.
- Every internal SKU code must also be searchable as a scan code.
- Map every known EAN/barcode alias to the canonical SKU.
- Exact duplicate mappings can be deduplicated; different EANs for the same SKU must be preserved.
- If a barcode maps to multiple SKUs, mark it as ambiguous and exclude it from automatic lookup until reviewed.
- Category remains blank unless provided by a trusted source.
- Because the source combines SKU/EAN in one column, validate that each EAN row can be linked to its SKU from the file's repeated product records. Do not infer a relationship from fuzzy name similarity.

## Phase 2 — Android baseline
- [ ] Create Kotlin Android project and Gradle wrapper
- [ ] Main expiry capture screen (red Mahalaxmi header)
- [ ] Store selection saved locally
- [ ] Camera barcode scanner
- [ ] Lookup exact scanned value against `product_barcodes.barcode`; return canonical SKU details
- [ ] Supabase product lookup with local cache
- [ ] Quantity and calendar expiry date inputs
- [ ] Recent entries / current-session history
- [ ] Soft-delete action
- [ ] Room persistence and retryable sync queue

## Phase 3 — Supabase and admin workflow
- [x] Initial product and barcode-alias schema
- [ ] Controlled Supabase write path (RPC or Edge Function)
- [ ] Master importer with validation report
- [ ] Secure product master upload workflow
- [ ] Export with session date range, including optional deleted rows
- [ ] Safe archive/export workflow before office-side table cleanup

## Non-negotiable behaviors
1. Session starts at 15:00 Asia/Kolkata, not at app launch.
2. At 14:59:59 entries belong to yesterday's session; at 15:00:00 they belong to the current day's session.
3. Main history is filtered to the current session. Old local records may be purged only after confirmed sync, or retained in a separate local archive as an implementation detail.
4. If an entry is unsynced, local cleanup must not remove it.
5. A manager's soft-delete must update `is_deleted = true` on the central row once synced, and must not issue SQL DELETE.
6. Server table exports/clears must not control local history/session calculations.
7. Use `client_record_id` as an idempotency key so offline retry cannot create duplicate central records.
8. No Supabase service-role key in Android.
9. Store selection without authentication is not a security boundary; do not give the public client unrestricted access to all stores' records.

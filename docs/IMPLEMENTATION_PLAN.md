# Implementation plan

## Phase 1 — Data contract and UX
- [x] Define 3 PM IST session rule
- [x] Define offline-first / local history requirements
- [x] Define soft-delete field and central record contract
- [ ] Confirm canonical store names and master CSV headers
- [ ] Confirm duplicate barcode / multiple EAN behavior

## Phase 2 — Android baseline
- [ ] Create Kotlin Android project and Gradle wrapper
- [ ] Main expiry capture screen (red Mahalaxmi header)
- [ ] Store selection saved locally
- [ ] Camera barcode scanner
- [ ] Supabase product lookup with local cache
- [ ] Quantity and calendar expiry date inputs
- [ ] Recent entries / current-session history
- [ ] Soft-delete action
- [ ] Room persistence and retryable sync queue

## Phase 3 — Supabase and admin workflow
- [x] Initial product and expiry table schema
- [ ] Controlled Supabase write path (RPC or Edge Function)
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

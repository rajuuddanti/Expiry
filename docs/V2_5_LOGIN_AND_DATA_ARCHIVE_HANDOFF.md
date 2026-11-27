# V2.5 Handoff — Store Login and Data Archive

Last updated: 2026-11-27 (Asia/Kolkata)

## Branch and baseline

- Repository: `rajuuddanti/Expiry`
- Implementation branch: `feature/v2.5-login-data-archive`
- Base branch: `feature/in-app-updates`
- Base commit: `eab8d2766d89312826e697f70d5d2aa4f1ce8d71`
- This branch is intended to preserve the last stable app baseline from the in-app-updates branch.
- Do not merge into another branch unless explicitly asked.
- No app source code, Supabase schema, or live data has been changed as part of this handoff documentation commit.

## Non-negotiable guardrails

- Inspect the current branch HEAD and relevant source before implementation.
- Keep the work scoped to the requested v2.5 feature.
- Do not change Supabase URL, publishable/anon key, `local.properties`, or existing Supabase configuration.
- Never put a Supabase service-role key in Android, GitHub, or chat.
- Do not create a second archive table.
- Do not alter existing Expiry Manager history behavior.
- Do not delete server-side expiry records.
- Do not claim build, install, release, or device verification without evidence.

## Store login requirements

- Login screen fields: Mobile Number and Password.
- The user will provide an Excel file containing Store Name, Mobile Number, and Password. Treat that workbook as the initial credential source; inspect and validate it before importing anything.
- Every installation upgrading from v2.4 must show the login screen on the first v2.5 launch, even when v2.4 already has a locally saved store name. A saved v2.4 store name is not proof of authentication.
- After successful login, the server-authenticated store name becomes the app's store identity and replaces the old local store value.
- Subsequent launches reuse the securely stored session; a fresh install or reinstall requires login again.
- No logout button is required.
- Archive access/recovery is scoped to the authenticated store, not the physical device. Multiple devices belonging to the same store must access the same store archive.
- Retain device identity on records for audit purposes only.
- Verify store identity server-side; never trust a locally supplied store-name string for authorization.
- Do not store or expose raw passwords in the app. Use secure authentication/verification and store-scoped backend authorization.
- Inspect existing Supabase Auth, tables, and RLS policies before choosing the implementation. Do not assume a schema or duplicate existing authentication infrastructure.

## Data Archive requirements

- Add a separate **Data Archive** button beside the existing **Expiry Manager** button in the History tab.
- Keep existing Expiry Manager history untouched.
- Use existing `public.expiry_entry_uploads` as the server source. Do not add another archive table.
- Existing new expiry-entry uploads continue automatically as they do today.
- Archive screen is local-first. Do not auto-download on app launch, archive open, or app update.
- Only an explicit user tap on **SYNC** may fetch missing archive records.
- Local archive retention: keep 90 days of synced entries. Do not remove pending/unsynced local records, even if older than 90 days.
- Server records are permanent; retention must not delete server rows.
- Include valid NO EXPIRY entries.
- Exclude rows where `is_deleted = true`.
- Sort newest to oldest.
- Date selector:
  - No date / All: latest 90 days.
  - Selected date: that date only.
- Category filter should preferably support multi-select.
- Product/SKU search should support partial token matching regardless of token order. Example: `DairyDay Choco Cone Crisp 75ml` should match `cone choco dairyday` and `dairy cho con`.
- Apply date/category/search filters server-side where possible and paginate at 50–100 rows per page to limit egress. Local filtering can be used for the local archive view.

## Archive recovery and sync

- A fresh install starts with an empty local archive. Its first manual SYNC recovers all eligible non-deleted records for the authenticated store from the latest 90 days.
- Example: if Gangadhara has 850 eligible records, first SYNC should retrieve all 850.
- Later syncs retrieve only records missing locally, keyed by unique entry `id`; do not overwrite existing local archive rows.
- Use pagination and compare local IDs with server IDs. Avoid downloading already-present rows; client-side deduplication after downloading does not reduce egress.
- Use idempotent insert / conflict-do-nothing behavior.
- Enforce store scoping in backend authorization/RLS.
- Daily sync limit: once per store.
- Consume the daily lock only after a complete successful sync. Failed or incomplete syncs must not consume the lock.
- The lock must survive app restart and reinstall; prefer backend-enforced per-store last-successful-sync state.
- Disable SYNC while running, prevent duplicate taps, request confirmation, and show progress plus a result summary.
- No automatic retry or background archive sync.

## Egress constraints

- Supabase egress allowance discussed: 5 GB/month. Avoid automatic archive downloads.
- Typical volume estimate: 5–10 records/day/store, or roughly 450–900 rows per store over 90 days.
- For 17 stores and 900 rows/store, an all-store sync would be about 15,300 rows.
- Illustrative payload scenarios for 15,300 rows:
  - 500 bytes/row: about 7.65 MB
  - 1 KB/row: about 15.3 MB
  - 1.5 KB/row: about 22.95 MB
- These are estimates only; actual egress depends on response payload and protocol overhead.
- Database logs and network egress are different metrics.

## Product master sync — separate concern

- Product-master sync is separate from archive sync.
- User wants master download to be manual-only, with a 30-day lock; no automatic initial or recurring master download.
- Show last successful sync time. A failed sync must not consume the lock.
- Current master size was approximately 130k rows in prior discussion; recheck before implementation.
- Do not change current master incremental/full behavior without inspecting source and obtaining permission.
- Under Check App Updates, show installed version, e.g. `Current Version: 2.5.0`.

## Implementation checklist

1. Inspect this branch HEAD, Android source, Room entities/migrations, upload flow, Supabase schema, Auth, and RLS.
2. Confirm the existing store-registration persistence and upgrade behavior.
3. Review the supplied credential workbook before importing or designing credential storage.
4. Design server-verified store authentication without exposing raw passwords or service-role credentials.
5. Implement the archive using existing upload records; no new archive table.
6. Add Room persistence and migration only if needed; preserve existing local records.
7. Implement explicit manual, store-scoped, paginated sync with backend-enforced daily success lock.
8. Verify first recovery, subsequent missing-only recovery, deleted-row exclusion, NO EXPIRY inclusion, retention, and multi-device store access.
9. Run Android build and device tests; record evidence and update handoff docs.

## Current status

- Requirements captured; implementation is not yet recorded as complete.
- Store credential Excel has not yet been supplied in this handoff.
- No v2.5 login/archive code changes are included in this documentation commit.
- No Supabase schema or live records were modified by this handoff.


## Implementation progress — 2026-11-27

### Backend foundation deployed to the linked Expiry project

- Added private schema tables: `private.store_sessions` and `private.store_archive_sync_state`.
- Revoked access to the private schema/tables from `PUBLIC`, `anon`, and `authenticated`; only `service_role` receives access.
- Deployed Edge Function `store-access` (version 1, ACTIVE, `verify_jwt=false`). The function implements:
  - `login`: verifies mobile/password against `store_accounts` server-side and returns a random 30-day opaque session token; only its SHA-256 hash is stored.
  - `me`: returns the authenticated store identity.
  - `archive-start`: starts a per-store sync and checks the 24-hour cooldown.
  - `archive-page`: returns up to 100 non-deleted records for the authenticated store, with date/category/token filters and pagination.
  - `archive-complete`: records the successful sync timestamp.
  - `archive-cancel`: clears an abandoned sync lease.
- The Android app must call this function using the existing project URL and publishable key; it must never query `store_accounts.trigger` or receive a service-role key.
- Source migration committed at `supabase/migrations/202611270001_v25_store_login_archive.sql`.

### Important implementation boundary

This is backend groundwork, not a completed v2.5 release. Android login UI/session persistence, local archive Room entity/migration, archive UI and client paging/deduplication/retention are still pending. The current archive completion endpoint trusts the authenticated client to call completion only after it has received every page; strengthen this with server-tracked page progress before production rollout. Add login rate limiting/attempt throttling before production rollout. Do not claim full security or end-to-end testing until these are implemented and tested.


## Android implementation progress — 2026-11-27

### Added on the v2.5 branch
- `data/StoreAccessClient.kt`: Android client for login, session-token persistence, archive sync start/page/complete/cancel calls. It uses the existing BuildConfig Supabase URL and publishable key; no configuration values were changed.
- `data/ArchiveEntryEntity.kt`: separate Room entity for server archive snapshots, distinct from active/pending `expiry_entries`.
- Room database version advanced from 5 to 6 with a non-destructive `MIGRATION_5_6` that creates only the new archive table and indexes.
- `ExpiryDao` now has conflict-ignore archive inserts, archive observation/count/category/ID queries, and archive-only retention pruning.
- `data/ArchiveSyncRepository.kt`: explicit foreground paginated retrieval, insert-ignore deduplication, cancel-on-error, and local archive pruning after successful page retrieval.

### Not yet complete
- Login screen has not yet been wired into MainActivity.
- History tab does not yet expose the Data Archive screen.
- Archive search/date/category UI and local filtering remain pending.
- The server endpoint currently pages all matching server rows; it does not yet accept a local missing-ID cursor. Therefore repeat sync may transfer already-cached rows even though Room ignores duplicates. Do not claim egress-efficient missing-only recovery yet.
- Server-side completion currently relies on the client to call complete after fetching all pages. Do not treat the daily lock as production-hardened until the server independently tracks page completion.
- No Android build or device test has been run in this environment.

### Permission boundary
- User explicitly instructed: **do not make any further Supabase changes without permission**.
- No Supabase schema, policy, table, function, secret, or live-data changes were made during this Android implementation step.
- Any additional Supabase work requires asking the user first and waiting for explicit approval.


## Android UI progress — 2026-11-27 (continued)

- Added a first-install store login dialog with mobile number and password. Existing installations with a locally saved store name are not blocked by the login dialog.
- If an existing installation has no session token, the Data Archive SYNC action asks the operator to sign in before requesting archive data.
- Store session bearer token is now encrypted using an AES-GCM key held in Android Keystore. The password is not persisted.
- Added the Data Archive screen from History, with manual sync confirmation, progress/result text, local record count, newest-first cards, all-dates/date selection, multi-select category filter, token-order-independent local product/SKU search, and clear filters.
- Added current app version text below Check for App Updates.
- Archive retention now uses the Asia/Kolkata business date and removes only old rows from the separate archive table.

### Important verification status and remaining gaps

- No Supabase changes were made after the user's explicit permission boundary.
- No build, emulator test, or physical-device test has been run. The branch must be built and tested before installation.
- Archive filters currently operate on the locally cached archive. The backend page request currently retrieves the eligible 90-day store archive without applying the UI's selected date/category/search filters.
- Subsequent sync still downloads server pages and relies on Room insert-ignore to avoid local duplicates. This is not missing-ID-only retrieval and does not meet the intended egress optimization yet.
- The current deployed backend's daily completion lock is not independently verified against all pages server-side. Do not represent the daily lock as hardened against a modified client.
- Login rate limiting and backend brute-force protection remain unimplemented.
- Existing v2.4 installations with a saved store name but no v2.5 session are blocked at app entry until the one-time login succeeds.
- The token is Keystore-encrypted, but session validation/refresh via the Edge Function `me` action has not yet been wired into app startup. The server still authenticates every archive request.

### Latest implementation commits

- `9cfa573` — archive Room entity
- `78000f5` — store access API client
- `75630bb` — Room v5→v6 archive migration
- `d28ca20` — archive DAO operations
- `d80e84f` — archive sync repository
- `891b92d` — login and archive UI initial integration
- `76ff3a9` — archive search/date picker corrections
- `7c8dfc2` — Android Keystore token encryption
- `3d883e6` — manual sync confirmation
- `eca163f` — India-timezone archive retention

- Local archive display is additionally filtered to the currently selected store so cached records from another store on the same device are not shown.
- Latest UI follow-up commit: `3c424ca`.


## Corrected login behavior — 2026-11-27

The earlier implementation incorrectly treated a locally saved v2.4 store name as sufficient to enter the app and prompted for credentials only when opening Data Archive. This has been corrected on the feature branch:

- A v2.4 installation has no v2.5 authenticated session, so the first v2.5 launch always requires mobile number + password login, regardless of the locally saved store name.
- The login dialog is mandatory and cannot be dismissed until authentication succeeds.
- Successful server login sets the store identity; the account's store name replaces the old local store value.
- The encrypted session is reused on subsequent launches, so staff are not asked to log in every time.
- Removed the PIN-protected Change Store Name dialog and its Settings button. Store identity is assigned by the registered account, not manually edited on the device.
- No Supabase changes were made for this correction.

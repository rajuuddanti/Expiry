# Conversation Record — V2.5 Device Identity, Store Switching, and Persistent Login

**Date:** 2026-11-28 (Asia/Kolkata)  
**Repository:** `rajuuddanti/Expiry`  
**Branch:** `feature/v2.5-login-data-archive`  
**Status:** Requirements and architecture recorded; implementation NOT started.

> This file records the discussion and decisions made in the 2026-11-28 conversation before implementation. It is a conversation/handoff record, not evidence that the described behavior exists in the APK or deployed backend.

## 1. Purpose of this conversation

The user wants a physical Android device to retain a stable store-specific Device ID even if the app is uninstalled or its data is cleared. The same device may later be used to log in to another store account, but switching stores must require a separate authorization PIN. The user also decided that the existing 30-day login session expiry is unnecessary and wants login sessions to persist until explicitly revoked.

Before implementing these changes, the user asked for the entire conversation, decisions, implementation status, and architecture to be documented in the repository's Markdown documentation.

## 2. Concrete device/store example supplied by the user

The user clarified that the additional PIN is specifically for **switching stores from the same Android device**, not for every login and not as a replacement for normal store credentials.

Example:

1. Physical device reports Android ID `AN1`.
2. User logs into Dharmaram. The device receives store-specific Device ID `DRM001`.
3. The app is uninstalled or its app data is cleared.
4. The same physical device (`AN1`) is used to log in to Mallial with Mallial's normal store credentials.
5. The backend recognizes that `AN1` was already associated with Dharmaram.
6. The app asks for the separate Store Change Authorization PIN.
7. Only after correct PIN verification does Mallial receive its own store-specific Device ID, for example `MLL001`.

The Android ID and Device ID are different concepts:
- **Android ID identity input:** identifies the app-scoped Android device identity used to look up prior assignments.
- **Device ID:** a sequential identifier belonging to a particular store assignment.

The same Android ID can therefore have more than one retained store assignment over time, but a new store assignment is gated by the additional PIN.

## 3. Confirmed decisions

### 3.1 When the additional PIN is required

- First-ever registration of an Android ID to a store: no additional PIN.
- Same Android ID logging into the same store it is already assigned to: no additional PIN.
- Same Android ID attempting to log into a different store: require the Store Change Authorization PIN.
- Wrong PIN: reject the switch. Do not create a destination assignment, allocate a Device ID, or issue a successful destination-store session.
- Correct PIN: authorize the switch and complete the destination assignment.
- A different Android ID is treated as a new device identity and follows first-registration behavior.

The PIN is an additional store-switch authorization gate. Normal store mobile-number/password authentication remains required.

### 3.2 Returning to a previously used store — Option A confirmed

The user explicitly selected **Option A**:

If the same Android ID previously used Dharmaram, switched to Mallial, and later returns to Dharmaram, the backend must reactivate and reuse its original Dharmaram Device ID. It must not allocate another Dharmaram ID.

For example:
- First Dharmaram assignment: `AN1 → DRM001`.
- Authorized Mallial switch: `AN1 → MLL001`.
- Later authorized return to Dharmaram: reuse `DRM001`, not `DRM002`.

Implementation constraints:
- Enforce uniqueness for the Android-ID-hash + store pair.
- Look up the destination store's prior assignment before allocating a new ID.
- Reactivate/reuse the prior assignment if found.
- Allocate a new sequential ID only if this Android-ID/store pair has never existed.
- Preserve historical store assignment records; do not delete the old store's assignment.

### 3.3 Android ID handling

- Use Android's app-scoped `ANDROID_ID` (SSAID) as the identity input.
- Prefer sending/storing a one-way SHA-256 hash (`android_id_hash`) rather than exposing the raw value in ordinary database views or logs.
- Do not request privileged hardware/phone identifiers or add unnecessary permissions.
- Android ID is not a guaranteed permanent hardware serial. It may change after factory reset, signing identity changes, Android user/profile changes, or other platform circumstances.
- Treat the identifier as a lookup key, not as proof that the person is authorized.

### 3.4 PIN secret

The user created a Supabase Edge Function secret named:

`STORE_CHANGE_AUTH_PIN`

The user confirmed that the secret was created. The PIN value was not shared in chat and must never be requested or copied into this file, Android source, APK, GitHub, SQL migration, logs, or client responses.

- One common PIN is intended for all store switches.
- The backend must verify the PIN server-side.
- Add server-side attempt throttling/rate limiting and safe audit logging.
- Never log the PIN or return it to the Android client.
- Creating the secret alone does **not** activate PIN verification; the deployed function does not yet use it.

### 3.5 Persistent login session — static, no 30-day expiry

The user explicitly decided that the existing 30-day session is not required and wants the session to remain static/non-expiring.

Expected behavior:
- App close/reopen does not log the user out.
- App update does not log the user out.
- No automatic time-based logout after 30 days.
- Session remains valid until an explicit logout, store account deactivation, successful store switch/revocation, or administrator revocation.
- The backend must still support revocation; “static” does not mean irrevocable.
- Keep the session token protected on Android (for example, encrypted storage backed by Android Keystore).
- Uninstall/data clear may remove locally stored session credentials. The Android ID mapping should still allow the device's original Device ID to be recovered after the user authenticates again.

The assistant proposed revoking the prior store session for the same Android ID after a successful switch, without affecting sessions belonging to other devices. The user replied “yeah” to the proposed implementation plan. This should be implemented carefully so it revokes only the session associated with the switching device, not every session for the old store.

**Current schema implication:** `private.store_sessions.expires_at` is currently NOT NULL and the Edge Function currently checks it. A true non-expiring session should be represented explicitly (likely nullable `expires_at` with null meaning no time expiry), with backend validation updated accordingly. Do not simulate a permanent session with an arbitrary far-future timestamp.

## 4. Proposed end-to-end architecture

This is the agreed functional architecture to implement after review.

### 4.1 Normal authentication and device registration

1. Android app reads the app-scoped Android ID.
2. App computes SHA-256 locally or sends the identifier through HTTPS for trusted backend hashing; choose one consistent approach and never log the raw value.
3. User submits the existing store mobile number and password.
4. Backend validates the normal store credentials using the existing `store_accounts` authentication source.
5. Backend looks up the Android-ID hash and authenticated store.
6. If no prior assignment exists for this Android ID, allocate the next Device ID for the authenticated store atomically and create the assignment.
7. If the same Android-ID/store assignment already exists, reactivate/reuse its existing Device ID.
8. Create or return a persistent store session only after the required authorization path has succeeded.
9. Return authenticated store identity and Device ID to the app. The app must not authorize a store based on a locally typed store name.

### 4.2 Store-switch authorization

1. Normal store credentials are validated first.
2. Backend detects that this Android ID has an assignment to another store.
3. Backend responds with a specific “store switch authorization required” state; do not create the destination session yet.
4. App displays a clear PIN prompt, ideally showing the previous and requested store names.
5. App submits the PIN to the trusted backend endpoint over HTTPS, bound to the authenticated login attempt/device identity.
6. Backend verifies `STORE_CHANGE_AUTH_PIN`, applies throttling, and records a safe audit event.
7. If PIN is incorrect, reject without changing assignments, creating a session, or consuming a Device ID.
8. If PIN is correct, perform the switch atomically:
   - Reuse the destination store's existing assignment for this Android-ID hash if one exists.
   - Otherwise allocate the next sequential Device ID for the destination store.
   - Retain the old store assignment.
   - Create/activate the destination assignment.
   - Revoke only the previous session associated with this device, if present.
   - Create the destination store's persistent session.
9. Return the destination store identity and Device ID.

### 4.3 Device ID allocation

- Sequential and store-specific, e.g. `DRM001`, `MLL001` (final formatting should follow the existing store-code convention after source inspection).
- Allocate on the server, never on the Android client.
- Allocation and assignment creation must be atomic/transactional.
- Protect against concurrent registrations so two devices cannot receive the same ID.
- Never allocate a new number for an already-existing Android-ID/store pair.
- Retain old assignments as history.

### 4.4 Conceptual data responsibilities

Do not assume these are necessarily separate physical tables; choose the final schema only after reviewing the live database and current migration history.

- Android device identity / Android-ID hash.
- Store-specific device assignment and retained assignment history.
- Sequential per-store allocation/counter.
- Current app installation/version/activity details.
- Session records and revocation state.
- PIN secret/verifier and attempt/audit controls.

Device identity and app-version monitoring are separate concerns and should not be conflated.

## 5. App-version monitoring tables discussed separately

The user asked whether the two tables for store app-version verification were remembered. The previously discussed two-table design was:

### Table A — `app_releases`

One current approved release record per app, with fields:
- `app_id`
- `app_name`
- `latest_version`
- `latest_version_code`
- `minimum_version_code`
- `updated_at`

Purpose: centrally record the current approved/latest version and minimum supported version for an app.

### Table B — `app_installations`

One current installation/version record per app + store + Device ID, with fields:
- `id`
- `store_id`
- `store_name`
- `app_id`
- `device_id`
- `device_name`
- `version_name`
- `version_code`
- `last_seen`
- `updated_at`

Uniqueness: `store_id + device_id + app_id`.

Behavior:
- APK update updates the existing installation record; do not create a new row for each version.
- One current release record per app in `app_releases`.
- Keep version verification separate from Android-ID/store-switch authorization.

**Documentation reconciliation note:** the existing `docs/EXPIRY_APP_VERSION_MONITORING_HANDOFF.md` describes a later/minimal installation-monitoring design using a single installation table and no release-history/latest-version table. This conversation's recalled two-table design is recorded here so it is not lost. Before implementation, reconcile whether both `app_releases` and `app_installations` are still required, or whether the user wants the later minimal installation-only approach. Do not silently create either schema.

## 6. Live backend inspection performed before this documentation

The current deployed Supabase backend was inspected read-only. No live schema, function, or data changes were made.

### 6.1 Project identity

- Supabase project: `Expiry`
- Project ref: `tmdswprfiaiywzrwzucj`
- Region: `ap-southeast-1`
- Project status at inspection: `ACTIVE_HEALTHY`
- Project URL: `https://tmdswprfiaiywzrwzucj.supabase.co`

Preserve the repository's existing Supabase URL and publishable key. Do not change `local.properties` or project configuration.

### 6.2 Deployed `store-access` Edge Function

At inspection:
- Status: ACTIVE
- Version: 4
- `verify_jwt=false`
- Existing actions:
  - `login`
  - `me`
  - `archive-start`
  - `archive-page`
  - `archive-complete`
  - `archive-cancel`

Existing login:
- Validates mobile/password against `public.store_accounts`.
- Creates a random session token.
- Stores only its hash in `private.store_sessions`.
- Current session duration is 30 days.
- `identity()` validates the session through `store_private_access` action `get_session`, checks `expires_at`, and verifies that the store account remains active.

Not currently implemented in the deployed function:
- Android ID / Android-ID hash registration.
- Device registry or store assignment.
- Sequential Device ID allocation.
- Store-switch PIN verification.
- Store-switch attempt throttling.
- Device-scoped prior-session revocation.
- Static/non-expiring session behavior.

The existing archive actions must be preserved during any future function update.

### 6.3 Existing `store_private_access` RPC

Current signature:

```sql
store_private_access(
  p_action text,
  p_mobile_number text,
  p_token_hash text,
  p_expires_at timestamptz,
  p_sync_id uuid,
  p_started_at timestamptz,
  p_last_successful_sync_at timestamptz
) RETURNS jsonb
```

It is a `SECURITY DEFINER` function and currently supports:
- `create_session`
- `get_session`
- `archive_get`
- `archive_begin`
- `archive_complete`
- `archive_cancel`

It does not currently support device registry or store-switch operations. Any future change must preserve all current actions and their semantics.

### 6.4 Existing tables observed

`public.store_accounts`:
- `auth_user_id` nullable unique
- `store_name`
- `mobile_number` primary key
- `is_active`
- `created_at`
- `updated_at`
- `trigger` (existing password-related field)
- 17 rows at inspection

`private.store_sessions`:
- `token_hash` primary key
- `mobile_number`
- `expires_at` NOT NULL
- `created_at`
- 4 rows at inspection

`private.store_archive_sync_state`:
- `mobile_number` primary key
- `last_successful_sync_at`
- `active_sync_id`
- `active_sync_started_at`
- 2 rows at inspection

Other relevant public tables include `MASTER`, `expiry_entry_uploads`, and `expiry_return_rules`.

No `expiry_device_registry` or `expiry_app_installations` tables were present at inspection.

### 6.5 Security observation

A Supabase advisory reported RLS disabled on:
- `private.store_sessions`
- `private.store_archive_sync_state`

The inspected schema ACLs showed:
- `private` schema: `anon_usage=false`, `authenticated_usage=false`, `service_role_usage=true`.
- Direct table privileges were limited to postgres/service_role.

This suggests anon/authenticated API roles cannot directly access the private schema through normal API access, but it is still worth reviewing defense-in-depth. Do not enable RLS blindly without suitable policies or change the private schema as part of unrelated work.

## 7. Implementation status at the time this file was written

| Component | Status |
|---|---|
| Normal store login | Already deployed; current 30-day session behavior |
| Store archive actions | Already deployed; preserve them |
| `STORE_CHANGE_AUTH_PIN` secret | User created it in Supabase |
| PIN verification in Edge Function | Not implemented |
| Android ID read/hash in Android app | Not implemented |
| Device registry/assignment schema | Not implemented |
| Atomic sequential Device ID allocation | Not implemented |
| Same-device store-switch flow | Not implemented |
| Reuse prior Device ID when returning to a store | Decision confirmed; not implemented |
| Persistent/non-expiring session behavior | Decision confirmed; not implemented |
| Device-scoped old-session revocation on switch | Proposed and accepted in implementation plan; not implemented |
| Two-table version monitoring design | Discussed; not implemented; reconcile with existing handoff |
| Live Supabase schema/function changes | None made in this conversation |
| Android source changes | None made in this conversation |
| APK build/install/device testing | None performed in this conversation |

## 8. Critical implementation guardrails

- Work only on `feature/v2.5-login-data-archive`.
- Do not merge into `main`, switch branches, rebase, or rewrite branch history unless the user explicitly asks.
- Inspect current branch HEAD and relevant Android/backend source before editing.
- Preserve existing archive functionality and all current Edge Function actions.
- Do not change Supabase URL, publishable key, `local.properties`, package name, signing identity, Room schema, expiry upload behavior, or archive behavior without separate review and permission.
- Do not make live Supabase schema/function changes without explicit user authorization.
- Never put the service-role key or PIN value in Android, APK, GitHub, SQL source, or logs.
- The secret name may be referenced; the secret value must not be documented.
- A GitHub source commit is not proof of a successful build, deployment, or physical-device test.
- Keep device registration, app-version reporting, and archive behavior logically separate.

## 9. Suggested implementation sequence

1. Re-read this file and the existing v2.5/device/version handoffs.
2. Inspect the current branch HEAD, `StoreAccessClient.kt`, Android session persistence, and the deployed `store-access` function.
3. Design the minimum database additions and atomic RPC operations. Preserve existing archive RPC actions.
4. Agree the exact Device ID format and reconcile the version-monitoring one-table/two-table question.
5. Implement persistent session semantics with explicit revocation and device-scoped store-switch handling.
6. Implement backend-first store-switch authorization using `STORE_CHANGE_AUTH_PIN`, with throttling and audit logging.
7. Implement Android ID hashing/registration and the PIN prompt.
8. Add version/installation reporting only as a separate feature.
9. Test first login, same-store login, reinstall/data-clear recovery, wrong PIN, correct PIN, return-to-old-store reuse, concurrent registration, deactivated store, explicit logout, app update, and session revocation.
10. Update documentation with actual migration/function names, commits, build results, deployment state, and test evidence.

## 10. Related repository documentation

- `docs/EXPIRY_DEVICE_ID_AND_STORE_SWITCH_AUTH_HANDOFF.md` — device identity and store-switch design.
- `docs/EXPIRY_APP_VERSION_MONITORING_HANDOFF.md` — version and installation monitoring.
- `docs/V2_5_LOGIN_AND_DATA_ARCHIVE_HANDOFF.md` — existing v2.5 login and archive requirements.
- `docs/CURRENT_HANDOFF.md` — broad project handoff.
- `docs/DECISIONS_AND_CHANGELOG.md` — project decision history.
- `docs/SCHEMA_REFERENCE.md` and `docs/SUPABASE_SCHEMA_HANDOFF.md` — schema references.

---

**Handoff checkpoint:** The user asked to document the complete device identity/store-switch/persistent-session conversation before implementation. This file is the dedicated conversation record. At this checkpoint, requirements and live backend state have been documented; no implementation has been performed.

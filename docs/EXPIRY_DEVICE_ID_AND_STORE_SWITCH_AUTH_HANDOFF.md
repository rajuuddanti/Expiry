# Expiry Manager — Device Identity and Store-Switch Authorization Handoff

Last updated: 2026-11-28 (Asia/Kolkata)

## Scope and status

This document records the agreed design for **MahaMart Expiry Manager only**.

It is documentation only. It does not implement Android ID collection, database tables, SQL functions, Edge Function changes, PIN setup, or UI changes. No live Supabase changes are authorized by this document.

## Device identity

Use Android's app-scoped `ANDROID_ID` (SSAID) as the device identity input. It is generally stable across uninstall/reinstall for the same app signing identity, Android user/profile, and device. It can change after a factory reset, signing identity change, user/profile change, or certain device/OS circumstances. It is not a guaranteed permanent hardware serial number.

Recommended handling:
- Read the Android ID in the app.
- Prefer sending/storing a one-way SHA-256 digest (`android_id_hash`) rather than exposing the raw identifier in ordinary table views or logs.
- Treat the value as an identifier, not as proof that the person using the device is authorized.
- Do not request privileged phone/device identifiers or add unnecessary permissions.
- A reinstall should look up the existing device mapping and recover the existing store-specific Device ID when the Android ID remains the same.

## Store-specific Device IDs

A single Android device may be used by more than one store over time. Therefore, Android ID and Device ID are not interchangeable:

- Android ID identifies the app-scoped Android installation/device identity input.
- Device ID is a sequential identifier scoped to a store assignment.

Example:

| Android ID (illustrative) | Device ID | Store |
|---|---|---|
| hash_A | DHARMARAM-01 | Dharmaram |
| hash_A | MALLIAL-01 | Mallial |
| hash_B | DHARMARAM-02 | Dharmaram |

The same Android ID may map to multiple store-specific Device IDs over time, but only after an authorized store switch. The old assignment is retained; it is not overwritten or deleted.

## Allocation rules

- Format: `STORENAME-01`, `STORENAME-02`, etc.
- Numbering is independent for each store.
- Allocate the next number on the server, not on the phone.
- Allocation and creation of the assignment must be atomic/transactional.
- Never reuse a previously allocated number.
- Reinstalling with the same Android ID and same authenticated store recovers the existing Device ID; it does not consume a new number.
- A new Android ID at a store receives the next number for that store.
- A device moving to another store receives the next Device ID for the destination store, while retaining its previous store assignment.

## Store-switch authorization flow

The existing normal store login remains the first authentication step. A separate Store Change Authorization PIN is required only when the Android ID is already associated with a different store and the user is attempting to use another store account.

1. User completes the existing store login using the current server-authenticated login flow.
2. Backend compares the authenticated store with the device's current/known store assignment.
3. If this is a first registration or the Android ID is already assigned to the authenticated store, continue without the additional PIN.
4. If the Android ID is associated with another store, block the switch and show a clear authorization popup identifying the previous and requested stores.
5. User enters the separate Store Change Authorization PIN.
6. App sends the PIN and authenticated session to a trusted backend endpoint over HTTPS.
7. Backend verifies the PIN securely. Do not hardcode the PIN in the APK, return it to the client, or store it in plaintext.
8. Only after successful verification, backend atomically allocates the next Device ID for the destination store and creates the new store assignment.
9. Keep the previous store assignment and its installation row unchanged.
10. On an incorrect PIN or backend failure, do not change the current assignment, consume a Device ID number, or partially create rows.

The PIN is an additional authorization gate, not a replacement for the normal store login. The implementation must decide who can set/change the PIN and how it is rotated before production. PIN verification should include server-side attempt throttling/rate limiting and safe audit logging; never log the PIN itself.

## Suggested conceptual data separation

The final physical schema must be chosen only after inspecting the live Supabase schema and existing backend. Conceptually, the design needs:

1. A device identity mapping keyed by the Android ID hash.
2. Store-specific device assignments, with unique Device IDs and retained assignment history.
3. Current Expiry Manager installation details for each store-specific Device ID: version, last-opened, and last-closed timestamps.
4. A per-store counter used for atomic sequential Device ID allocation.
5. Secure server-side configuration for the store-switch authorization PIN verifier.

These are conceptual responsibilities, not a mandate to create five separate tables. They may be combined where constraints and RLS/backend security remain correct. Keep the user-facing Supabase table view simple, with Device ID, Store, Version, Last Opened, and Last Closed.

## Edge cases to test

- Same store, app update: same Android ID and Device ID; update the version in the existing installation row.
- Same store, uninstall/reinstall: recover the same Device ID when Android ID is unchanged; update the existing row.
- Different store, no PIN: reject the switch and leave all records unchanged.
- Different store, wrong PIN: reject and leave all records unchanged.
- Different store, correct PIN: create a new destination-store Device ID; retain the previous assignment.
- Return to a previously used store: follow the agreed policy for reusing that same Android-ID/store assignment versus allocating a fresh assignment. **This policy must be explicitly confirmed before implementation**; do not silently reuse or allocate a second ID.
- Two devices registering simultaneously at the same store: server-side atomic counter prevents duplicate IDs.
- Android ID changes after factory reset/signing identity/user-profile change: treat as a new identity unless a separate authorized recovery process is designed.
- Android process killed or force-stopped: last-closed callback may not run. Record last-closed only when Android gives the app a reliable lifecycle callback; do not claim it proves a clean app exit.

## Existing project guardrails

- Repository: `rajuuddanti/Expiry`
- Branch: `feature/v2.5-login-data-archive`
- Do not merge into `main` or another branch unless explicitly requested.
- Do not change Supabase URL, publishable key, `local.properties`, package/signing identity, Room schema, expiry-entry upload behavior, or existing archive behavior as part of this feature without reviewing and obtaining permission.
- Inspect existing `StoreAccessClient.kt`, `store-access` Edge Function, login/session persistence, and live `store_accounts` configuration before implementation.
- No service-role key or PIN secret in Android.
- Do not modify live Supabase schema/functions without explicit user approval.

## Confirmed return-to-store policy — 2026-11-28

The user confirmed **Option A**: if a device switches from Dharmaram to Mallial and later returns to Dharmaram with the same Android ID, reactivate and reuse its original `DHARMARAM-01` assignment. Do not allocate `DHARMARAM-02` for that same Android-ID/store pair.

Rationale: only a small number of authorized people (approximately 2–3, including the user) are expected to switch stores for testing. Reusing the original assignment keeps store-wise device records stable and avoids unnecessary ID consumption.

Implementation requirements:
- Enforce uniqueness for the Android-ID-hash + store pair.
- On an authorized switch, look up an existing assignment for the destination store first.
- If one exists, reactivate/reuse that Device ID and update the current assignment state.
- If none exists, allocate the next sequential Device ID for that store.
- Preserve the prior store's assignment and installation record; do not delete or overwrite it.
- The switch still requires the separate server-verified Store Change Authorization PIN whenever the Android ID is currently associated with a different store.

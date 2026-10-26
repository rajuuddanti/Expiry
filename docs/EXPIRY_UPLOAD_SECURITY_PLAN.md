# Expiry Upload Security Plan
Updated: 2026-10-26
Branch: `feature/new-expiry-upload-table`

## Goal
Record upload network information and prevent a device assigned to one store from submitting expiry records under another store. `public.MASTER` is unrelated and must remain unchanged.

## Current state
- The user reports adding `ip_address inet` to live `public.expiry_scan_uploads`.
- This connector could not independently inspect or alter the production Supabase project; Supabase write calls returned a permissions error.
- Android still uploads directly to `/rest/v1/expiry_scan_uploads`.
- The Edge Function is committed source code, not deployed or wired into Android.
- Migration 005 is committed but not applied.
- No device registration rows have been verified.
- No build or runtime tests have been run in this environment.

## Intended secure flow
1. Apply `supabase/migrations/005_expiry_upload_security_foundation.sql` only after confirming the user's `ip_address` column already exists. Its `ADD COLUMN IF NOT EXISTS` is intended to be idempotent.
2. Confirm Edge Function runtime request headers using trusted deployment documentation/observed logs. The current draft reads `cf-connecting-ip`; if the platform does not provide this header, IP will be null. Do not blindly trust client-provided `X-Forwarded-For`.
3. Set `SUPABASE_SERVICE_ROLE_KEY` only as an Edge Function secret, never in Android.
4. Verify device ID and store with an administrator, then register using `supabase/operations/register_expiry_device.sql.example`. Replace placeholders before running; never run the template unchanged.
5. Deploy `expiry-upload` only after migration and secrets are ready.
6. Change Android to call `/functions/v1/expiry-upload` and handle HTTP 403 without marking local entries synced.
7. Test: registered device + correct store succeeds and records IP; unregistered/inactive device and store mismatch fail and create audit events; transient failures remain queued locally.

## Device registration notes
- The registry must not be writable by ordinary Android clients.
- A device should be registered only after a trusted person verifies its identity and store.
- The current template uses Android ID as the initial lookup key. Android ID is not strong authentication; a revocable per-install credential is preferable for a hardened rollout.
- The PIN `3331` must not be the only server-side authorization control. Do not trust a PIN check that exists only in Android UI.

## Draft migration
`supabase/migrations/005_expiry_upload_security_foundation.sql` proposes:
- `ip_address inet`, `security_flag`, `security_reason` on `expiry_scan_uploads`.
- `expiry_device_registry` for server-managed device/store bindings.
- `expiry_security_events` for suspicious attempts.
- RLS enabled without direct client policies on the security tables.

## Important caveats
- IP is an audit signal, not a device identity. Mobile networks and store routers can change public IPs, and many devices may share one public IP.
- Never ship a service-role key in Android.
- `public.MASTER` stays untouched.
- Do not describe the feature as active until the migration, function deployment, Android routing change, and tests are complete.

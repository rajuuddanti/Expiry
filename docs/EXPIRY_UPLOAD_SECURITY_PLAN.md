# Expiry Upload Security Plan
Updated: 2026-10-26
Branch: `feature/new-expiry-upload-table`

## Goal
Record upload network information and prevent a device assigned to one store from submitting expiry records under another store. The existing `public.MASTER` table is unrelated and must remain unchanged.

## Current upload path
The Android app currently writes directly to `public.expiry_scan_uploads` through Supabase REST. A client-supplied IP field is not trustworthy and will not reliably contain the public IP. Do not rely on a new database column alone.

## Intended flow
1. Android sends the authenticated user, device identifier, selected store, and expiry record to a Supabase Edge Function.
2. The function derives the observed client IP from request metadata available in the deployed Supabase runtime/proxy chain. Confirm the correct trusted header before implementation; never blindly trust arbitrary client-supplied `X-Forwarded-For`.
3. The function checks the device registration in `public.expiry_device_registry`.
4. If the device is inactive or the claimed store differs from its registered store, reject the upload and write an event to `public.expiry_security_events`.
5. For allowed uploads, the function writes the expiry row and observed IP to `public.expiry_scan_uploads`.
6. Only an authorized registration/admin workflow may change a device's assigned store. The PIN is not sufficient by itself if checked only in Android; verification must be server-side and rate-limited.

## Draft migration
`supabase/migrations/005_expiry_upload_security_foundation.sql` adds:
- `ip_address inet`, `security_flag`, and `security_reason` to `expiry_scan_uploads`.
- `expiry_device_registry` for server-managed device/store bindings.
- `expiry_security_events` for attempted mismatches and other security events.
- RLS enabled with no direct client policies on the two security tables.

**This is a repository migration draft only. It has not been applied to production.** Do not apply until the Edge Function, authorization flow, RLS behavior, and rollout plan are reviewed together.

## Important caveats
- IP is an audit signal, not a device identity: mobile networks and store routers can change public IPs, and multiple devices may share one public IP.
- Android ID alone is not strong authentication and may be reset or spoofed on compromised devices. Consider a per-install registration token stored securely and revocable by an administrator.
- Do not place a Supabase service-role key in Android or expose registry/security-event tables to the client.
- Test both accepted uploads and rejected mismatched-store attempts in a non-production environment before rollout.
- Keep `public.MASTER` untouched.

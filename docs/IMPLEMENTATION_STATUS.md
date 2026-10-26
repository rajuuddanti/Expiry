# Implementation Status — Mahalaxmi Expiry Manager

Last updated: 2026-10-26 (Asia/Kolkata)

## Current work branch

`recovery/restore-ui-supabase`

This is a separate recovery branch. `main`, `feature/room-offline-storage`, and the recovery baseline are not overwritten.

## Implemented in this branch

- Existing Scan, History, Settings UI and local Room storage are retained.
- Added an HTTPS Supabase REST/Auth client using the publishable/anon key (not service-role credentials).
- Added anonymous Auth session creation and refresh; anonymous sign-in must be enabled in Supabase Auth.
- Added initial product-master download with 1,000-row paging and transactional replacement of only the local product master.
- Added timestamp-based product-master delta refresh using `updated_at`.
- Added pending expiry-entry upload using stable local UUIDs and conflict-safe upsert.
- Soft-delete changes now re-enter the pending queue for synchronization.
- Added Settings → Sync Now control and sync status/error feedback.
- Added a SQL migration for RLS policies and owner-scoped expiry-entry access.
- Added setup and verification instructions in `docs/SUPABASE_ANDROID_SETUP.md`.

## Still required before this is considered working

- Apply migration `supabase/migrations/002_secure_android_sync.sql` in the actual Supabase project.
- Enable Anonymous Sign-Ins in Supabase Auth.
- Add the project's URL and publishable/anon key to the ignored root `local.properties`.
- Build and run the app in Android Studio; this branch has not yet been build-verified by the assistant.
- Test first-time product sync, delta sync, offline entry capture, upload retry, app restart, and soft-delete sync.
- Add automatic background retry; current retry is manual via Settings → Sync Now.
- Design a separately authenticated server-side path for shared all-store reporting. The Android app must never contain a service-role key.

## Data safety notes

- SKU and scan codes remain TEXT so leading zeroes are preserved.
- Product-master replacement is transactional and rejects an empty first-sync response, protecting the existing local cache.
- Expiry entries remain in Room; a product refresh never clears them.
- Expiry records are owner-scoped to the device's Supabase anonymous Auth user under the new RLS migration. This does not yet give an admin shared reporting access.
- Do not treat code committed to GitHub as a successful build or verified live Supabase integration until the listed setup and tests pass.

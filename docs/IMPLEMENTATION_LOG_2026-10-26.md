# Implementation Log — 2026-10-26

## Phase 1: master-data and lookup foundation

Branch: `feature/new-expiry-upload-table`

Implemented and committed:
- Reworked the Room product cache to retain repeated master rows using a local generated row ID and explicit row order.
- Added Room migration 1→2 to preserve the existing local cache.
- Added DAO support for FCFS first-name lookup and name/code search.
- Added a read-only SKU Lookup screen for product name, SKU code, and barcode/EAN searches.
- Changed sync reads to the observed live `public.MASTER` columns: `SkuCode`, `SkuLongName`, `Catg`.
- Changed sync writes to the existing `public.expiry_scan_uploads` table and its observed column names.
- Kept `public.MASTER` unchanged and did not add a separate master EAN column.

## Phase 2: upload security foundation (draft; not deployed)

- Added `supabase/migrations/005_expiry_upload_security_foundation.sql` to the branch. It proposes an IP column plus server-managed device registry and security-event tables. The migration has **not** been applied to production.
- Added `supabase/functions/expiry-upload/index.ts`. It validates the user session, checks device/store registration, audits rejected store mismatches, and writes accepted uploads including the observed IP when available.
- The user has separately added `ip_address inet` to the live `public.expiry_scan_uploads` table. Connected Supabase write tools returned a permissions error, so this session could not independently verify that change.
- The function is a source-code draft only. It expects the registry/event tables from migration 005 and a server-side `SUPABASE_SERVICE_ROLE_KEY` secret. It must not be deployed until the migration is applied, the observed-IP header contract is verified for this project, and a safe device-registration/admin workflow exists.
- Android still uploads directly to the REST table; it has **not** been switched to the Edge Function. Do not deploy the function or claim the protection is active yet.
- No Android build or device test has been run from this connector-only edit session.

## Important limits / next work
- Live `public.MASTER` introspection did not report a primary key. The REST paging/order behavior must be verified because FCFS source ordering cannot be guaranteed until the API returns the stacked rows consistently across pages.
- The current first-name association assumes the first row encountered for each exact normalized product name is its SKU row. This must be validated against real master samples before claiming a 100% SKU association.
- Category schedule display, category-aware save validation, date cutoff rules, PIN-protected store switching, and the 3 PM local-only history reset remain unimplemented.
- Confirm live RLS behavior and anonymous/authenticated session handling before production sync.

## Safety
- No service-role key was added to Android.
- `public.MASTER` remains untouched.
- No Supabase migration was applied by this session.

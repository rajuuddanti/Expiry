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

## Important limits / next work
- Live `public.MASTER` introspection did not report a primary key. The REST paging/order behavior must be verified because FCFS source ordering cannot be guaranteed until the API returns the stacked rows consistently across pages.
- The current first-name association assumes the first row encountered for each exact normalized product name is its SKU row. This must be validated against real master samples before claiming a 100% SKU association.
- Category schedule display, category-aware save validation, date cutoff rules, PIN-protected store switching, and the 3 PM local-only history reset remain unimplemented.
- The upload schema requires RLS and a working authenticated session. Confirm the live policy behavior before production sync.
- No Android build or device test has been run from this connector-only edit session. Do not label this phase build-verified.

## Safety
- No service-role key was added.
- No live Supabase schema/data was changed by these Android code edits.
- No migration was applied to Supabase.

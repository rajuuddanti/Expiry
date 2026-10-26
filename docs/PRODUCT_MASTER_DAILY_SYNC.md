# Product Master Daily Sync Strategy

Last updated: 2026-10-26 (Asia/Kolkata)

## User-confirmed workflow

The user updates the product master in Supabase daily. Usually 99.9% of products remain unchanged; only a small number of SKUs may be added or changed. The Android app should use Room as its local master and must not download the full product list on every launch.

The user chose Option A: upload the new file to a staging table, compare it against the current published master, and publish changes without deleting/recreating the published master table's rows.

## Desired app behavior

1. On the first successful online launch, fetch the complete published master and save it to Room.
2. Persist a local flag and last successfully applied master revision. Do not repeat the initial full import on every launch.
3. On later launches, make a lightweight revision check.
4. If the revision has not changed, download no master rows.
5. If it changed, request only changes since the locally applied revision, then apply them transactionally to Room.
6. If offline, continue using Room and retry on a later launch/network opportunity.
7. Never clear the local master before a replacement/delta has been fully received and validated.
8. Product lookup should remain available offline.

## Required server-side design

Use the existing production product_master table as the published lookup. Keep product_master_staging as an import helper. The user wants only two production business tables (product_master, expiry_entries); helper metadata can be implemented with a small sync metadata table or another suitable controlled mechanism, but confirm the final design before adding any table. Do not add a category field/table.

Recommended publish flow:
1. Upload the day's new master file into staging.
2. Validate the staged file: row counts, required values, normalized scan codes, duplicate/conflicting scan_code values, and SKU mapping.
3. Compare staged rows against the published master by scan_code.
4. Classify inserts, updates, and removals.
5. Publish atomically, preserving the previous published master if validation fails.
6. Record a new master revision and change set.
7. Keep removed products out of the current lookup. Prefer explicit tombstones/deactivation or a retained change log so devices can remove stale local records during delta sync.
8. Do not tell the user to delete all rows from product_master during the daily workflow.

## Delta synchronization choices

A version/check endpoint alone only tells the app that something changed; it does not tell the app which rows changed. To avoid downloading the entire master, the server must retain a delta/change log or equivalent snapshot history. A simple updated_at cursor by itself is not sufficient to detect rows removed from the master unless removals are represented explicitly.

A robust first design is a monotonically increasing master revision, retained per-revision changes keyed by scan_code (including removals), a lightweight current-revision check, a paginated delta query/endpoint returning changes after the local revision, and a full-snapshot fallback when a device is too far behind retained history.

If the user deletes/replaces all rows in the published table daily, old state is lost and the system cannot calculate a true delta. Always stage and compare first; update/publish the existing master only after validation.

## Safety and correctness

- Never embed the Supabase service-role key in the Android app.
- The assistant is not connected to the user's Supabase project; SQL must be run by the user in their own dashboard.
- Confirm actual live schema and RLS before choosing RPCs, views, or policies.
- Apply each delta to Room in one transaction; update the locally applied revision only after the transaction succeeds.
- Preserve all expiry-entry data and unsynced rows during product-master refresh.
- Product-master removals should affect product lookup only; do not delete expiry history referring to a removed SKU.
- Add idempotent retry, pagination, timeout handling, and basic sync diagnostics.
- First-launch full sync must be resumable or safely retryable for a master of this size.

## Implementation order

1. Inspect current repository/source tree and actual schemas.
2. Agree the minimal server metadata/change-log design and security policy.
3. Provide SQL migration and daily staging/publish procedure; user runs and shares results.
4. Test server comparison on a sample, including additions, changed names/mappings, removals, duplicates, and failed validation.
5. Implement Android initial full sync + revision check + delta application in Room.
6. Test first launch, unchanged master, small delta, removals, offline start, interrupted sync, and retry.
7. Document how the user uploads/publishes the master each day.
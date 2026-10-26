# Next Chat Handoff — Mahalaxmi Expiry Manager

Last updated: 2026-10-26 (Asia/Kolkata)

## Paste this into a new chat

Continue the Mahalaxmi Expiry Manager project from the GitHub repository `rajuuddanti/Expiry`. First read:
- `README.md`
- `docs/PROJECT_CONTEXT.md`
- `docs/DATA_MAPPING_AND_IMPORT.md`
- `docs/SQL_REFERENCE.md`
- `docs/DECISIONS_AND_CHANGELOG.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/PRODUCT_MASTER_DAILY_SYNC.md`
- `supabase/migrations/001_initial_schema.sql`

### Current exact status

The user imported this repository into Android Studio and said they had not created anything. Inspection of GitHub showed the repository already contains a minimal Android Compose project and a MainActivity screen, but the original expiry entries were only held in Compose memory. The assistant has started implementation on branch `feature/room-offline-storage`.

See `docs/IMPLEMENTATION_STATUS.md` for what has been added and what remains. The Room changes have NOT been build-verified. The assistant is NOT connected to the user's Supabase project and must not claim to have queried or changed it. Ask the user to fetch/check out the feature branch, sync Gradle, and run a build; use any build output to correct issues before proceeding.

### New confirmed decision: daily incremental master sync

The user updates the product master daily. Most rows remain unchanged, with only a few new/changed SKU rows. They chose Option A:
1. Upload the daily file to a staging table.
2. Validate it.
3. Compare it with the existing published product master.
4. Publish only inserts/updates/removals after validation; do not delete and recreate the published master table.
5. Track a master revision and retain changes/removal markers so Android can request only changes since its last successful revision.

Desired Android behavior:
- First successful online launch: download the full master into Room.
- Save the last successfully applied master revision locally.
- Every later launch: check a lightweight current revision.
- If unchanged: download zero master rows.
- If changed: download only the delta, apply it in one Room transaction, then update the local revision.
- If offline: continue using Room and retry later.
- Never clear local master before a delta/full snapshot is fully validated and ready to apply.
- Product removals update product lookup only; never delete expiry history or unsynced expiry entries.

Read `docs/PRODUCT_MASTER_DAILY_SYNC.md` for the full design. A version check alone is insufficient for delta sync; the server must retain a change log or equivalent, including removals. Confirm actual schema/RLS before finalizing SQL/RPC policies. Do not put the service-role key in the APK.

### Product mapping requirements

Source originally had `SkuCode/EAN` and `SkuLongName`; SKU and EAN rows were stacked in one code column. User wants one searchable `scan_code` in `product_master`.
- Keep codes as TEXT.
- If a code has fewer than 5 digits, pad left to 5; 5+ digits remain unchanged.
- SKU row: scan_code and sku_code are normalized source code.
- EAN row: scan_code is normalized EAN; canonical SKU comes from first exact product-name match in SKU source order (FCFS).
- The user previously estimated first 28–30K rows were SKU rows; verify rather than guess.
- Live product_master screenshot previously showed three required TEXT columns: scan_code, sku_code, sku_name. User later said the master is fixed, but actual current schema should be verified before SQL changes.

### App requirements

Android/Kotlin expiry capture app for Mahalaxmi Mahamart:
- store selection persisted locally and editable in Settings;
- scan or manually enter a SKU/EAN;
- autofill canonical SKU and product name;
- quantity and expiry date with calendar picker;
- Room/offline-first storage and retryable sync;
- history session from 3 PM IST to next day 3 PM;
- preserve unsynced rows; soft-delete expiry records;
- only two production business tables: product_master and expiry_entries; staging is an import helper;
- no category table/field;
- never put service-role key in APK.

### Next implementation steps

1. Fetch/check out `feature/room-offline-storage` in Android Studio, sync Gradle, and run a debug build.
2. Fix build/runtime issues using actual error output; do not claim build success before verified.
3. Implement product-master initial full import into Room and product lookup.
4. Inspect current live schemas and RLS from user-provided SQL results.
5. Design the staging validation/publish transaction and revision/change-log schema.
6. Test add/change/remove and failed validation scenarios before publishing.
7. Implement revision-based delta sync and retryable expiry-entry upload.
8. Test unchanged master, small delta, removed codes, offline launch, interrupted sync, and retry.
9. Keep implementation status/handoff Markdown current on the working branch.

### Accuracy rules

Never claim SQL was executed by the assistant, Supabase is connected, or Android is buildable/tested unless verified.
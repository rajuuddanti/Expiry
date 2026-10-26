# Implementation Status — Mahalaxmi Expiry Manager

Last updated: 2026-10-26 (Asia/Kolkata)

## Current work branch

`feature/room-offline-storage`

## Implemented in this branch

- Added Room entities for the local product lookup and expiry-entry records.
- Added a Room DAO for product lookup, product upserts, observing history, pending-entry retrieval, and soft deletion.
- Added a Room database singleton.
- Updated the Scan/History UI to save expiry entries into Room instead of keeping them only in Compose memory.
- History now reads from Room, so saved entries are intended to remain after app restarts.
- Product scan/manual code entry now attempts to resolve a locally cached product master row and fill the product name.

## Not implemented yet

- No Supabase connection or credentials were added.
- No initial product-master download is implemented.
- No revision check or delta-sync API is implemented.
- No expiry-entry upload/retry worker is implemented.
- The current repository state has not been built or run in Android Studio by the assistant. Treat this as code added but not yet build-verified.

## Important behavior

- Room uses TEXT codes so leading zeroes can be preserved.
- Expiry entries have a `syncStatus` field defaulting to `PENDING`.
- Deleting from History sets `deletedAt`; it does not physically delete the row.
- Product-master refresh must later be implemented transactionally and must never clear unsynced expiry entries.

## Next steps

1. Fetch and check out `feature/room-offline-storage` in Android Studio.
2. Sync Gradle and run a debug build.
3. Fix any build/runtime issues before moving on.
4. Implement product-master initial full sync into Room.
5. Implement the server revision/change-log mechanism only after reviewing the actual Supabase schema and RLS with the user.
6. Implement revision-based delta sync and expiry-entry upload with retry.

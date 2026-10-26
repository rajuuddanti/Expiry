# Android Supabase Sync Setup

Branch: `recovery/restore-ui-supabase`

## What this branch adds

- A Supabase REST/Auth client using HTTPS and the Supabase publishable (anon) key.
- Anonymous Supabase Auth sessions, persisted and refreshed on the device.
- Product-master initial download and timestamp-based incremental refresh into Room.
- Upload of pending expiry records, including soft-delete state.
- A manual **Settings → Sync Now** control plus WorkManager sync when connected (startup attempt and periodic retry, at a minimum 15-minute interval).
- A transactional product-master replacement that does not touch locally captured expiry entries.
- RLS policies that keep expiry entries scoped to the device's Supabase Auth user.

## Required local configuration

Do not commit real credentials. In the root project's ignored `local.properties`, add:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY
```

Use the project's publishable key (or legacy anon key), never the service-role key. The values are read by `app/build.gradle.kts` and generated into BuildConfig. The URL must be HTTPS.

## Supabase setup

1. Confirm migration `001_initial_schema.sql` has been applied.
2. Apply `002_secure_android_sync.sql` in the Supabase SQL Editor.
3. In Supabase Dashboard → Authentication → Sign In / Providers (or the equivalent Auth settings), enable **Anonymous Sign-Ins**. The client uses anonymous Auth so the service-role key is not shipped in the APK.
4. Confirm the product master is populated. Its codes must be text values, including leading zeroes.
5. Build and install the app, open **Settings**, select the store, then tap **Sync Now** while online.

## Data behavior

- First successful product sync downloads pages of 1,000 rows and atomically replaces only Room's product master.
- Later product syncs fetch rows with `updated_at` newer than the last successful sync watermark.
- Inactive product rows are received so a previously active code can be disabled locally.
- Pending expiry rows upload one by one with their stable local UUID as `client_record_id`. Duplicate retries use upsert on that id.
- A local row is marked `SYNCED` only after Supabase accepts it.
- Soft-deleting an already-synced row marks it pending again so the deletion can sync.
- The SQL policy scopes records to the anonymous Auth user on that device. It does not yet provide a shared all-store admin report path; implement that through a separately authenticated server-side endpoint rather than putting a service-role key in the APK.

## Verification status and limitations

The repository changes have been committed, but the Android project has **not yet been built or tested on a device**. Verify Gradle sync/build first, then test first-time sync, incremental updates, offline capture, reconnect/retry, app restart, and soft-delete synchronization.

Automatic background retry is not added yet; for now use **Settings → Sync Now** after connectivity returns. Product delta sync relies on `product_master.updated_at` being maintained by Supabase for every published change.

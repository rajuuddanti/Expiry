# Current Branch and In-App Update Audit

Audit date: 2026-11-28 (Asia/Kolkata)

## Repository state inspected

- Repository: `rajuuddanti/Expiry`
- Active implementation branch: `feature/v2.5-login-data-archive`
- Audited HEAD: `8aa2c7ad4a0a5e223d8fc91e8f5fed67f8608ff9`
- HEAD commit: `Confirm device ID reuse when returning to a previous store`
- Base branch: `feature/in-app-updates`
- Base commit: `eab8d2766d89312826e697f70d5d2aa4f1ce8d71`
- Branch comparison: 51 commits ahead of base, 0 behind at audit time.
- Android application ID: `com.mahalaxmi.expiry`
- Source version: `versionName = 2.5.2`, `versionCode = 8`.
- No branch switch, merge, rebase, Android source edit, build, release publication, or live Supabase mutation was performed during this audit.

## What the current branch contains

### 1. Store login and server-assigned store identity

Inspected:
- `MainActivity.kt`
- `data/StoreAccessClient.kt`
- `docs/V2_5_LOGIN_AND_DATA_ARCHIVE_HANDOFF.md`

Current source behavior:
- The app opens the mandatory Store Login dialog when `StoreAccessClient.hasSession()` is false.
- Login uses Mobile Number + Password and calls the `store-access` Edge Function with action `login`.
- The server response supplies the store name; the app saves that server-returned store identity rather than trusting a typed store name.
- The session token is encrypted using AES-GCM with a key held in Android Keystore.
- The password is not persisted.
- The session token is reused on subsequent launches while it remains locally decryptable.
- The local `store` preference is updated from the server-returned session store name.
- The previous PIN-protected manual Change Store Name UI was removed.

Important source-level caveat:
- `hasSession()` checks whether a locally stored token can be decrypted and is nonblank. It does not call the Edge Function `me` action on app startup to validate expiry/revocation.
- The client stores `expires_at`, but this current startup path does not independently validate it before allowing entry.
- Therefore the source proves local token presence, not that the server session is still valid. The backend still authenticates protected archive requests.

### 2. Data Archive implementation

Current branch includes:
- Separate Room archive entity and non-destructive Room migration 5→6.
- Archive DAO operations and local retention.
- Foreground paginated archive sync repository.
- Data Archive screen from History.
- Manual sync confirmation and progress/result feedback.
- Date, category, and token-order-independent local search filters.
- Current store filtering of locally displayed archive rows.
- Current app version display under Check App Updates.

Known limitations already recorded in the v2.5 handoff:
- Repeated sync can download rows already cached locally; Room ignores duplicate IDs, but that does not reduce network egress.
- Archive UI filters are local; current backend page retrieval is not proven to apply those selected filters.
- Server completion/daily lock is not independently proven to verify every page.
- Login attempt throttling is not implemented.
- No current build or physical-device test evidence was available during this audit.

### 3. Current in-app updater — actual source behavior

Inspected:
- `app/src/main/java/com/mahalaxmi/expiry/AppUpdateManager.kt`
- `MainActivity.kt`
- `AndroidManifest.xml`
- `res/xml/file_paths.xml`
- `app/build.gradle.kts`

The **0.2.4 baseline updater** on `feature/in-app-updates` (branch base commit) used:
- GitHub Releases API: `https://api.github.com/repos/rajuuddanti/Expiry/releases/latest`
- It searched the latest release assets for `update.json` and the APK asset.
- It selected the APK by `apkAssetName` when present.
- It treated the update as mandatory.

The current 2.5.2 source changes the update source to one static manifest URL:
- `https://raw.githubusercontent.com/rajuuddanti/Mahalaxmi-App-Updates/main/Expiry-Manager/update.json`

Current manifest fields read by Android:
- `versionCode`
- `versionName`
- `mandatory`
- `releaseNotes`
- `apkUrl`
- `sha256`

This is a different schema from the older release-asset manifest: current code expects a direct `apkUrl`, not `apkAssetName`.

Current updater flow:
1. Automatically checks approximately 1.2 seconds after app launch.
2. Compares manifest `versionCode` against installed `BuildConfig.VERSION_CODE`.
3. If a newer version is found, opens an update dialog.
4. Downloads the APK to the app's private cache `updates/` directory.
5. Verifies SHA-256 if the manifest supplies a nonblank hash.
6. Uses the existing FileProvider to open Android's package installer.
7. Android still requires the user to approve installation.

Current updater implementation caveats:
- The current code has one configured static manifest URL. It does not implement primary-to-secondary fallback or try both sources.
- The manifest is read from the public raw GitHub URL above; a private GitHub repository cannot serve this to unauthenticated app clients.
- SHA-256 verification is optional in code: a blank `sha256` skips verification. For production releases, always provide the hash of the exact APK.
- The `mandatory` field is parsed into `AppUpdate`, but the current dialog is always non-dismissible (`onDismissRequest = { }`) and its copy always says “Update required”. Thus the flag currently does not control dismissibility or dialog wording.
- The current manifest reader validates that `apkUrl` is nonblank and that `versionCode` is newer; it does not comprehensively validate every metadata field or enforce HTTPS on `apkUrl`.
- No evidence was found in this source audit that a current APK has been published at the manifest's `apkUrl`, that the manifest is live, or that an update was installed successfully on a device.

### 4. Build and configuration notes

- The current `app/build.gradle.kts` requires root `local.properties`.
- It reads `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` and injects them into BuildConfig.
- The audited source version is 2.5.2 / versionCode 8.
- Package ID remains `com.mahalaxmi.expiry`.
- Do not change Supabase URL/key, `local.properties`, signing identity, package ID, Room migrations, or existing data flows without explicit approval.

## Device monitoring and store-switch PIN status

The two device-monitoring handoff documents are already present:
- `docs/EXPIRY_APP_VERSION_MONITORING_HANDOFF.md`
- `docs/EXPIRY_DEVICE_ID_AND_STORE_SWITCH_AUTH_HANDOFF.md`

They record the requirements and confirmed store-switch policy, including reusing the same Android-ID/store assignment when returning to a previously used store. They are design documents only.

The common secret `STORE_CHANGE_AUTH_PIN` has been created by the user in Supabase Edge Function Secrets. The current branch source inspected here does not yet read or verify that secret, does not implement cross-store switch authorization, and does not allocate sequential store-specific Device IDs. Do not describe the PIN feature as active.

## Recommended next implementation sequence

1. Treat 2.5.2 / versionCode 8 as the current branch source baseline, not as proof of a published or installed release.
2. Before changing updater behavior, confirm that the public static manifest and APK hosting location are the intended second source and that the bridge release from 0.2.4 to 2.5.2 is still available through the old GitHub Releases endpoint.
3. Decide whether 2.5.2 should support true primary-to-secondary fallback. Current code only uses the static source.
4. Keep the common store-switch PIN server-side. Add verification to the existing `store-access` flow only after inspecting the deployed function and obtaining explicit permission for any live Supabase changes.
5. Implement Android ID hashing, assignment recovery, cross-store prompt, and store-specific Device ID display/update in a separate scoped change.
6. Build and test the APK locally, verify package/version/signing compatibility, then test an in-place update from an actual 0.2.4 installation.
7. Test login, archive, store-switch denial/approval, return-to-previous-store ID reuse, and local data preservation on test devices.

## Verification boundary

This audit is based on the current GitHub branch source and documentation. It is not a Gradle build, CI run, APK inspection, live manifest fetch, Edge Function test, Supabase schema inspection, release check, or physical-device test. Those remain unverified.


## Explicit branch comparison — confirmed baseline

The requested comparison is:

- **Present branch:** `feature/v2.5-login-data-archive`
- **Comparison/stable branch:** `feature/in-app-updates`
- The comparison branch's Gradle config is `versionName = 0.2.4`, `versionCode = 5`.
- The present branch's Gradle config is `versionName = 2.5.2`, `versionCode = 8`.
- Both use application ID `com.mahalaxmi.expiry`.
- Comparison base commit: `eab8d2766d89312826e697f70d5d2aa4f1ce8d71`.
- Present source commit reviewed: `8aa2c7ad4a0a5e223d8fc91e8f5fed67f8608ff9`.
- GitHub compare reported present branch 56 commits ahead and 0 behind at the time of this comparison (the count includes documentation-only commits already on the present branch).

### Exact updater delta

| Area | `feature/in-app-updates` (0.2.4) | `feature/v2.5-login-data-archive` (2.5.2) |
|---|---|---|
| Update metadata source | GitHub Releases API `rajuuddanti/Expiry/releases/latest` | Static raw manifest in `Mahalaxmi-App-Updates` |
| Manifest discovery | Search release assets for `update.json` | Direct fixed URL |
| APK resolution | Prefer asset named by `apkAssetName`; otherwise first APK asset | Direct `apkUrl` field |
| Manifest fallback | Version name/release notes can fall back to release tag/body | Defaults to `Unknown` / `App improvements.` |
| Mandatory flag | Hardcoded `true` in returned update | Parsed from manifest |
| UI handling of mandatory | Non-dismissible update dialog | Still non-dismissible; parsed flag currently has no UI effect |
| Download | Same cache download path | Same cache download path |
| SHA-256 | Verify if nonblank | Verify if nonblank |
| Install | Existing FileProvider + Android package installer | Same |
| Manual check | Same Settings action | Same Settings action, plus visible installed version text |

### Important rollout implication

The 0.2.4 APK's updater still expects the old GitHub Release layout. For an existing 0.2.4 installation to discover 2.5.2, a published release in `rajuuddanti/Expiry` must still contain a usable `update.json` and the matching APK asset. If `apkAssetName` is absent in that old manifest, the 0.2.4 code falls back to the first APK asset it finds. Do not assume the bridge works until the actual published release assets and manifest are checked and an in-place update is tested.

After 2.5.2 is installed, its updater reads the static `Mahalaxmi-App-Updates` manifest. The current source does **not** query GitHub Releases first and does **not** implement fallback between both sources.

### Unchanged updater plumbing

The 1.2-second launch check, manual Settings check, progress UI, cache destination, SHA-256 implementation, FileProvider authority, install permission handling, and Android package-installer handoff remain materially the same in the inspected source. The current updater changes the metadata discovery/resolution model, not the APK installation mechanism.

### Verification not performed

No live GitHub release/asset/manifest request, build, APK signature comparison, install, or physical-device update test was performed as part of this branch-source comparison.

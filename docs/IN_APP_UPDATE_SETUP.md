# In-app update channel — setup and release procedure

## Current implementation

The app checks for updates automatically shortly after launch and exposes **CHECK FOR APP UPDATES** in Settings. The check is independent of Supabase/product-master sync and expiry-entry uploads.

The app reads the latest **published GitHub Release** from:
`https://api.github.com/repos/rajuuddanti/Expiry/releases/latest`

A release is the admin-controlled rollout switch:
- Draft or unpublished releases are not offered.
- Publishing a release makes its update available to store devices.
- To stop offering an update, edit/delete the release or publish a newer release.
- Store devices check on app launch and when staff tap the manual check button.

## Required release assets

Every app release must contain exactly one APK asset and an asset named `update.json`.

Example `update.json`:

```json
{
  "versionCode": 3,
  "versionName": "0.3.0",
  "apkAssetName": "MahaMart-Expiry-Manager-v0.3.0.apk",
  "mandatory": false,
  "releaseNotes": "In-app update support and improvements.",
  "sha256": "REPLACE_WITH_LOWERCASE_SHA256_OF_APK"
}
```

- `versionCode` must be greater than the installed app's versionCode (currently 2).
- `apkAssetName` must exactly match the APK asset filename attached to the same release.
- `sha256` should be the APK's SHA-256 checksum. If present, the app verifies it before offering installation.
- `mandatory` controls the message and prevents dismissing the update dialog. It does not silently install or bypass Android's installer.
- `releaseNotes` is shown to staff.

## Android installation behavior

The app downloads the APK to its private cache, verifies SHA-256 when supplied, and opens Android's package installer using the existing FileProvider. If Android's per-app unknown-source permission is not enabled, the app opens the relevant Settings page. The user must return and tap INSTALL. Android's normal confirmation screen still appears.

The APK must have:
- The same application ID: `com.mahalaxmi.expiry`.
- A higher versionCode.
- The same signing certificate as the currently installed app.

**Signing is the release blocker to resolve before store rollout.** The current CI workflow creates a debug APK using the GitHub runner's debug keystore. That keystore is not guaranteed to match the debug keystore on the user's Windows PC and may differ between runners. Do not distribute that CI APK as an in-place update until the signing identity is confirmed. A stable release signing key should be stored as protected GitHub Actions secrets (never committed to the repository), or the exact established local signing key must be used consistently. Never uninstall store apps merely to work around an avoidable signing mismatch.

## Scope preserved

No Supabase configuration/schema, Room schema/version, product-master sync cooldown, expiry upload logic, or inspection business rules are changed by this feature.

## Verification still required

- GitHub Actions build result for this branch.
- Confirm actual installed app signing certificate and choose a stable release signing method.
- Publish a test release with APK + update.json.
- Test check, download, checksum validation, installer handoff, and successful in-place update on a test device before store rollout.


## First test release — 0.2.2 (2026-11-11)

The first in-app update test uses:
- Installed baseline: `0.2.1` / versionCode `2`.
- Test release: `0.2.2` / versionCode `3`.
- Release tag: `v0.2.2`; target branch: `feature/in-app-updates`.
- APK asset filename: `MahaMart-Expiry-Manager-v0.2.2.apk`.
- Metadata asset filename: exactly `update.json` (lowercase, no spaces).
- APK SHA-256: `1a2dfbe9df87ea475a3fd8c393c61e9296e08f54ef6efc5c759fd25e4e681d89`.

Use this metadata for the test release:

```json
{
  "versionCode": 3,
  "versionName": "0.2.2",
  "apkAssetName": "MahaMart-Expiry-Manager-v0.2.2.apk",
  "mandatory": false,
  "releaseNotes": "Settings icon update and in-app update test.",
  "sha256": "1a2dfbe9df87ea475a3fd8c393c61e9296e08f54ef6efc5c759fd25e4e681d89"
}
```

The user inspected the baseline APK in Android Studio and confirmed package `com.mahalaxmi.expiry`, versionName `0.2.1`, versionCode `2`. The baseline APK and locally built 0.2.2 APK have matching signing-certificate fingerprints. This confirms signing identity between those APK files, but does not verify the updater end-to-end.

At the latest recorded point, the GitHub Release was still being prepared; publication was not confirmed. Do not claim an update is available until the release is published and both asset names are exact. Keep the 0.2.1 app installed for the in-app test; do not manually install 0.2.2 first.


## Second test release — 0.2.3 (prepared 2026-11-11)

A second test release was prepared after restoring the original Settings gear icon:
- VersionName: `0.2.3`
- VersionCode: `4`
- Settings icon restored to `⚙`.
- APK asset filename: `MahaMart-Expiry-Manager-v0.2.3.apk`
- Metadata asset filename: exactly `update.json`
- Release tag: `v0.2.3`; target branch: `feature/in-app-updates`
- Release notes: `Restored the original Settings gear icon.`
- Mandatory: `false`

Build the APK locally on the same PC/signing identity, then calculate the exact APK checksum and place it in the `sha256` field. Do not reuse the 0.2.2 checksum. Keep 0.2.2 installed while testing the 0.2.2 → 0.2.3 update. Publishing v0.2.3 makes it the latest release; complete or consciously skip the earlier 0.2.1 → 0.2.2 test before publishing if that first hop still matters.

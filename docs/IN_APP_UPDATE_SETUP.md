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

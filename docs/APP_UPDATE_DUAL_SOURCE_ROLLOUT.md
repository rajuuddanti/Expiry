# Version 2.5.2 — Dual-Source App Update Rollout

Last updated: 2026-11-28 (Asia/Kolkata)

## Goal

Deliver one bridge update to existing installations through the update path they already use. Once devices are on version 2.5.2, future update checks can use a second source if the original GitHub repository becomes private.

## Agreed rollout

1. Current release line: 2.5.1.
2. Implement dual-source update checking in 2.5.2.
3. Keep the existing `rajuuddanti/Expiry` repository public while older installations receive 2.5.2 through their existing updater.
4. The user verifies that all intended devices have updated to 2.5.2.
5. Only after that confirmation, the user may change `Expiry` repository visibility to private.
6. Version 2.5.2 and later must retrieve update metadata and APKs from the second source without depending on anonymous access to the private repository.

## Client fallback behavior

The 2.5.2 updater should:
- Try the primary update source first.
- Fall back to the second source when the primary request fails, returns a non-success HTTP status (including 404), times out, or returns invalid/unparseable metadata.
- Validate required manifest fields and compare versionCode with the installed version.
- Download the APK from the selected source.
- Verify SHA-256 when supplied; reject a mismatch.
- Hand the verified APK to Android's package installer using the existing FileProvider flow.
- Keep user data and existing app behavior intact.

A malformed or incomplete response from the primary must not prevent a valid secondary source from being checked.

## Private-source security

A private GitHub repository cannot be read by an ordinary unauthenticated Android client. Never embed a personal access token, GitHub App secret, or other private credential in the APK.

The second source must therefore be one of:
- A genuinely public static update endpoint and APK hosting location; or
- A secure intermediary that authenticates server-side and exposes only the intended update manifest and APK download.

Do not treat a private raw GitHub URL as a working fallback. Do not expose private repository credentials to the client.

## Rollout safety

- Keep the current primary endpoint and repository public until the user confirms the 2.5.2 rollout is complete.
- Do not change repository visibility automatically.
- Do not publish a manifest pointing to an APK that has not actually been uploaded.
- Calculate SHA-256 from the exact APK asset being served.
- Preserve applicationId/package ID and signing certificate so Android can install the update over the existing app.
- Use a higher versionCode than 2.5.1.
- Do not uninstall the app as part of the update. Confirm Room/local data migration and retention on test devices.
- Android still requires the user to approve sideloaded package installation; the updater cannot silently install.

## Verification checklist

- [ ] Inspect the actual active branch and current updater implementation before editing.
- [ ] Confirm the 2.5.1 updater's existing primary URL and response format; ensure it can discover the 2.5.2 bridge build while the repo is public.
- [ ] Implement and review primary-to-secondary fallback in 2.5.2.
- [ ] Confirm the secondary endpoint works without any client-side secret.
- [ ] Build 2.5.2 and verify APK package ID, versionCode/versionName, and signing certificate.
- [ ] Publish the 2.5.2 APK and matching metadata to the existing primary path.
- [ ] Test update detection/download/hash/install from an actual 2.5.1 installation.
- [ ] Confirm local Room data and settings remain after in-place upgrade.
- [ ] Test fallback by making the primary endpoint unavailable in a controlled test.
- [ ] Track rollout until the user confirms all target devices are on 2.5.2.
- [ ] Only then may the user make the source repository private.
- [ ] Test the secondary update source after repository visibility changes.

## Current status

This file records the agreed architecture and rollout sequence only. It does not mean dual-source updater code has been implemented, 2.5.2 has been built or published, devices have updated, or the private-source fallback has been tested.


## Actual source audit — 2026-11-28

The current implementation was inspected in `AppUpdateManager.kt` and `MainActivity.kt` on `feature/v2.5-login-data-archive`.

### Bridge behavior now in source

- The 2.5.1 baseline source reads the latest published GitHub Release from `rajuuddanti/Expiry/releases/latest`, finds `update.json`, and resolves the APK asset.
- The current 2.5.2 source (versionCode 8) no longer queries the Releases API. It reads one static manifest from:
  `https://raw.githubusercontent.com/rajuuddanti/Mahalaxmi-App-Updates/main/Expiry-Manager/update.json`
- The current manifest schema expects `versionCode`, `versionName`, `mandatory`, `releaseNotes`, `apkUrl`, and `sha256`. It does not use the old `apkAssetName` field.
- Therefore, the intended staged bridge is represented in source: old 2.5.1 clients use the old Releases API to discover 2.5.2; after 2.5.2 is installed, its updater reads the static update host.

### Important correction to the original plan

The current 2.5.2 code uses the static endpoint as its **only** update source. It does not try the old primary endpoint first and fall back to the static source. The primary-to-secondary fallback checklist above is not implemented in the current code. If true fallback is still required, it needs a separate code change.

### Current code behavior and release cautions

- Automatic update check runs about 1.2 seconds after launch.
- A newer version opens a non-dismissible dialog. The current UI says “Update required” regardless of the manifest's `mandatory` value; the value is parsed but not used to control dismissal.
- APK download uses the manifest's direct `apkUrl`.
- SHA-256 is checked only when `sha256` is nonblank. Always supply a correct hash for a release.
- APK installation is handed to Android through FileProvider. Android still displays its package installer confirmation.
- The app's version source at audit time is 2.5.2 / versionCode 8. This is not evidence that 2.5.2 has been published or installed on every device.
- Do not make `rajuuddanti/Expiry` private until the user confirms the 2.5.2 bridge rollout is complete and the static manifest/APK host has been verified from an updated device.

See `docs/BRANCH_AND_UPDATER_AUDIT_2026-11-28.md` for detailed source findings and verification boundaries.

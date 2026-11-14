# APK SHA-256 and GitHub Release Checklist

Repository: `rajuuddanti/Expiry`  
Working branch: `feature/in-app-updates`  
App: MahaMart Expiry Manager (`com.mahalaxmi.expiry`)

## Purpose

The app's updater downloads the APK asset attached to the latest published GitHub Release. It calculates SHA-256 for the downloaded APK and compares that value with the `sha256` field in that release's `update.json`.

**The hash in `update.json` must belong to the exact APK file uploaded as the release asset.** Do not calculate the hash from a different build, an intermediate APK, or a similarly named file.

## PowerShell command used on the Windows PC

After downloading the release APK, locate its exact filename in Downloads. Windows/browser downloads may append an extra `.apk` suffix. In the November 14, 2026 test, the downloaded filename was:

`MahaMart-Expiry-Manager-v0.2.4.apk.apk`

From PowerShell, calculate its SHA-256 with:

```powershell
Get-FileHash "$env:USERPROFILE\Downloads\MahaMart-Expiry-Manager-v0.2.4.apk.apk" -Algorithm SHA256
```

The output includes the SHA256 hash and the file path. Copy the value in the `Hash` column. The hash can be represented in uppercase or lowercase; hexadecimal comparison is case-insensitive.

If the downloaded file has a different name, replace the filename in the command with the exact name shown by:

```powershell
Get-ChildItem "$env:USERPROFILE\Downloads" -Filter *.apk -Recurse -ErrorAction SilentlyContinue | Select-Object FullName
```

To calculate the hash for a release APK before uploading it, point `Get-FileHash` at that exact release APK file. After uploading, downloading the GitHub asset and hashing the downloaded copy is a useful verification that the asset matches the local file.

## Important distinction

- `Get-FileHash` **calculates** the SHA-256 of an existing file. It does not build an APK, download an APK, or rename an APK.
- The hash in `update.json` must match the exact APK asset named by `apkAssetName`.
- If the APK is renamed after hashing, its contents are unchanged and the hash remains the same. If it is rebuilt or modified, calculate the hash again.
- The APK's SHA-256 is not its Android signing-certificate fingerprint. APK signing compatibility is a separate requirement for installing an update over an existing installation.

## v0.2.4 manifest values

The v0.2.4 release manifest was prepared with:

```json
{
  "versionCode": 5,
  "versionName": "0.2.4",
  "apkAssetName": "MahaMart-Expiry-Manager-v0.2.4.apk",
  "mandatory": true,
  "releaseNotes": "-.-",
  "sha256": "<SHA-256 OF THE EXACT UPLOADED APK>"
}
```

Replace the placeholder with the hash calculated from the exact APK asset. The downloaded filename may have an extra `.apk` suffix from the browser; that does not mean the GitHub asset itself should have that doubled extension. Keep `apkAssetName` exactly aligned with the actual GitHub Release asset filename.

## Release verification checklist

1. Build the intended app version from `feature/in-app-updates`.
2. Confirm the APK package/version are the intended release.
3. Calculate SHA-256 for that exact APK.
4. Set `apkAssetName` to the exact APK asset filename and set `sha256` to the calculated hash in `update.json`.
5. Upload both the APK and `update.json` to the GitHub Release.
6. Publish the release (a draft is not returned by the latest published-release lookup).
7. Download the APK asset from the published release and calculate its SHA-256 using the PowerShell command above.
8. Compare the downloaded asset hash with `update.json`. They must match.
9. Test the in-app download, checksum verification, Android installer handoff, signing compatibility, and in-place update on a test device.

Do not report a successful update until the device test is actually completed. Android's package installer still requires user confirmation for sideloaded APK installation.

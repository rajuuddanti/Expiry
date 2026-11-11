# Implementation Log — 2026-11-11

## Project and purpose

MahaMart Expiry Manager is the Android app used by Mahalaxmi Mahamart store teams to record product expiry checks. It supports barcode/SKU lookup, category-based inspection scheduling, local Room storage, Supabase product-master/return-rule synchronization, NO EXPIRY recording, history, and CSV export. The business day is anchored at 3:00 PM Asia/Kolkata. This log records the latest work and preserves the decisions needed to continue the project in a later chat.

Repository: `rajuuddanti/Expiry`  
Active feature branch: `feature/20-day-sync-lock`  
Base branch: `feature/main-screen-redesign`  
Application ID: `com.mahalaxmi.expiry`

## Why this update was requested

The user wanted a manual product-master sync to be restricted after a successful sync, so store staff cannot repeatedly run the manual SYNC NOW action. The requested restriction is a 20-day cooldown. The user explicitly said the rest of the app must remain the same.

## Implemented: 20-day manual sync cooldown

The feature is implemented in `app/src/main/java/com/mahalaxmi/expiry/MainActivity.kt`.

- Reuses the existing SharedPreferences file `expiry_manager` and key `master_last_sync`.
- Calculates the manual-sync unlock time as the saved successful-sync timestamp plus 20 days.
- Disables the manual SYNC NOW button while the cooldown is active and displays `SYNC LOCKED`.
- Displays the remaining cooldown as days and hours (for example, `SYNC AVAILABLE IN 19D 23H`).
- Refreshes the displayed countdown once per minute.
- A successful `syncRepository.sync()` records the new timestamp and starts the cooldown.
- A failed sync does not save a new timestamp and therefore does not start or reset the cooldown.
- The manual button is enabled only when no manual sync is running and the cooldown is inactive.

## Explicit scope boundaries / decisions

The user specifically required that everything else stay the same. Therefore this change does **not**:

- Add a sync-history table or feature.
- Change the Supabase schema, URL, publishable key, policies, or project configuration.
- Change Room entities, database schema, or Room version.
- Add a PIN, administrator override, or bypass.
- Change background/periodic sync behavior or the existing WorkManager jobs.
- Change unrelated screens, UI, business rules, or expiry-entry behavior.

The 20-day lock applies to the manual SYNC NOW control. It is not intended to disable background sync.

## Version bump for installing over the existing app

File: `app/build.gradle.kts`

- `applicationId = "com.mahalaxmi.expiry"` (unchanged)
- `versionCode = 2`
- `versionName = "0.2.1"`

This was committed as `8066915edb5505f817595e3ecd8d14cec9b5d900` with message `Bump app version for 20-day sync lock update`.

An APK can update the installed app in place only if the installed app has a lower versionCode and the APK is signed with the same signing certificate as the installed app. Android will still show its normal installation confirmation. Do not advise uninstalling unless an actual signing/version conflict requires it.

## GitHub Actions

File: `.github/workflows/android-build.yml`

- Push builds are configured for `feature/new-expiry-upload-table` and `feature/20-day-sync-lock`.
- The workflow runs `gradle --no-daemon :app:assembleDebug`.
- It uploads `app/build/outputs/apk/debug/app-debug.apk` as the `Mahalaxmi-Expiry-Manager-debug` artifact for 14 days.

The workflow configuration is present in the branch. This log does not claim that the latest commit has completed a successful CI build; check the Actions run before claiming build success.

## User's local Git update situation

The user ran `git fetch origin` successfully and received `origin/feature/20-day-sync-lock`. Their local `git switch feature/20-day-sync-lock` and `git pull origin feature/20-day-sync-lock` were blocked because local changes to `app/build.gradle.kts` would be overwritten.

Safe next steps already provided:
1. `git stash push -m "local build.gradle backup" -- app/build.gradle.kts`
2. `git switch feature/20-day-sync-lock`
3. `git pull origin feature/20-day-sync-lock`
4. Verify with `Select-String -Path app/build.gradle.kts -Pattern "versionCode|versionName"`

Do not use `git reset --hard`, force checkout, or discard the user's local build.gradle change. Do not apply the stash automatically after switching: it may restore old version settings. Inspect it first if the user wants to recover any local edits.

## Next requested feature: in-app update checker/downloader

The user then asked to add an in-app update flow. The agreed direction is to design this after the current sync-lock update is preserved in the repository handoff. The proposed architecture discussed (not implemented yet):

- Publish versioned APKs as GitHub Releases.
- Publish a small version metadata JSON containing versionCode, versionName, APK download URL, mandatory/optional flag, and release notes.
- Android app checks the metadata and compares it with its installed version.
- Offer a check-for-updates action and/or check on app launch.
- Show update details, download progress, and an Install action.
- Android requires the user to confirm installation for APKs installed outside Google Play in the normal consumer-device setup; do not promise silent installation.
- Keep update checking/downloading separate from product-master sync and expiry data sync.

No in-app updater code, release workflow, metadata endpoint, permissions, or package-installer handling has been implemented yet. Confirm exact desired rollout behavior before coding. The user expressed interest in an admin-controlled rollout where the release is published by the admin and store devices are notified/offered the update.

## Verification status

Confirmed from the GitHub branch source:
- Manual cooldown logic is present in `MainActivity.kt`.
- Version is `2 / 0.2.1` in `app/build.gradle.kts`.
- GitHub Actions workflow includes the feature branch.
- Application ID remains `com.mahalaxmi.expiry`.

Not confirmed by this documentation commit:
- Successful GitHub Actions build for the latest source.
- APK installation/update on a physical device.
- Whether the release APK signing key matches the signing key of the currently installed store app.
- Real-device timing/behavior of the 20-day cooldown after success/failure.

## Working rules to carry forward

- Inspect the active branch, HEAD, worktree, and relevant files before editing.
- Preserve the user's local changes; never force checkout or reset.
- Do not switch branches, merge, rebase, or change branch history without explicit instruction.
- Do not change Supabase configuration or Room schema/version unless explicitly requested.
- Keep changes narrowly scoped to the requested feature.
- Separate source inspection, commit, CI build, APK install, and physical-device verification; never claim one proves another.
- Preserve all previous business requirements and historic implementation decisions in the existing docs. This log supplements rather than replaces them.

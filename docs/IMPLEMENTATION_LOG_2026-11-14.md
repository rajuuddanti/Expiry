# Implementation Log — 2026-11-14

Repository: `rajuuddanti/Expiry`  
Branch: `feature/in-app-updates`  
App: MahaMart Expiry Manager (`com.mahalaxmi.expiry`)

## 2026-11-14 — product-master sync must become manual-only (decision; implementation pending)

The user identified repeated complete product-master downloads as the cause of avoidable Supabase egress and requested that product-master sync be manual on every device.

### Agreed direction
- App opens using the locally cached Room product master.
- Do not automatically download `public.MASTER` on launch, resume, or in the background.
- Keep a clearly available manual **SYNC NOW** action.
- A manual action downloads the complete master using the existing pagination approach.
- Replace/update the local master only after the complete download succeeds; if sync fails, preserve the previous local master.
- Prevent overlapping manual master syncs.
- Keep expiry-entry uploads, local history, category/rule behavior, and the GitHub in-app updater unchanged.

### Important implementation boundary
This is a requirement/plan only. No Kotlin source, WorkManager configuration, Supabase function, database schema, or release asset was changed as part of this documentation update.

The existing source history includes a 20-day manual-sync cooldown and background WorkManager behavior. Both must be inspected in the current `feature/in-app-updates` source before implementation. The latest user request is manual-only across devices; confirm whether manual sync should be available at any time or retain the 20-day cooldown before removing/changing that cooldown.

### Supabase enforcement
- Phase 1 can be implemented in the Android app without changing Supabase schema or project configuration.
- App-only enforcement does not stop old installed app versions from making automatic requests.
- Optional Phase 2: after the updated app is deployed, add a server-side request guard so MASTER downloads require an explicit manual-sync request marker/header. Test the guard against the actual PostgREST request path before enabling it.
- Do not claim Supabase enforcement exists until implemented and HTTP-tested.

### Rollout
Use the existing GitHub Releases + `update.json` in-app updater to distribute a new app version after build, signing, and device tests. Publishing a GitHub Release is separate from pushing source code. Do not claim that all devices have updated until rollout is verified.

## 2026-11-14 — Supabase MASTER egress investigation

Read-only edge-log inspection for 2026-11-14 (00:00–11:10 IST) showed successful GET pagination against `/rest/v1/MASTER`.

- Request Range headers show pages of 1,000 rows (for example, `0-999`, `1000-1999`).
- The observed master contained 125,120 rows, so a full download requires 126 page requests (the last page covers rows 125,000–125,119).
- Confirmed complete page sequences by user-agent signature:
  - Xiaomi `24108PCE2I`, Android 16: 1,722 page requests; 12 complete sequences confirmed, with 5 additional first-page starts beyond the 12 matched final-page requests.
  - Xiaomi `24108PCE2I`, Android 15: 499 page requests; 4 complete sequences confirmed, with additional partial activity.
  - Vivo `V2240`, Android 15: 378 page requests; 3 complete sequences confirmed.
  - Xiaomi `2312BPC51H`, Android 16: 232 page requests; 1 complete sequence confirmed.
  - Xiaomi `24108PCE2I`, Android 14: 126 page requests; 1 complete sequence confirmed.
  - Moto G57 Power, Android 17: 26 page requests; no complete sequence confirmed in this window.
- At least 21 complete master downloads are therefore confirmed across these signatures in the checked window. Partial attempts are additional.
- IP addresses varied and must not be interpreted as physical-device counts. A user-agent signature is not proof of an exact handset count or store identity.
- Supabase log response content-length values were not populated in the inspected aggregate, so this investigation identifies request activity, not an exact byte/egress total.
- No additional device was blocked and no historical data was changed as part of this investigation.

## 2026-11-14 — updater release state carried forward

- Working branch: `feature/in-app-updates`.
- Version source was advanced to `versionCode 4` / `versionName 0.2.3` (commits recorded in prior handoff).
- The user previously confirmed release `v0.2.2` was published. The 0.2.1 → 0.2.2 in-app update device test was not confirmed.
- Version 0.2.3 build, release publication, and end-to-end device update remain unverified in this handoff. Do not infer these from the version bump.

## Earlier documentation update
The preceding commit recorded investigation findings only. The implementation below was added afterward.


## 2026-11-14 — manual-only MASTER refresh and mandatory app updates

Implemented on `feature/in-app-updates`:

- Removed the product-master download from `ExpirySyncWorker`. The existing initial/periodic WorkManager jobs now only upload locally saved pending expiry entries; they no longer call `repository.sync()` or download `public.MASTER`.
- The Settings **SYNC NOW** action remains the only product-master download entry point. Its existing 20-day cooldown and successful-sync timestamp behavior were preserved.
- The automatic app-update check remains shortly after app launch and reads GitHub Releases only. `AppUpdateManager` does not call Supabase.
- Every detected newer release is now mandatory in app code, regardless of the `mandatory` value in `update.json`.
- The update dialog cannot be dismissed. It shows one primary action: **UPDATE**, then download progress in the same dialog, then **INSTALL UPDATE** after download. No LATER/CLOSE action remains.
- The Settings manual update check opens the mandatory dialog only when a newer release exists; when already up to date it shows a toast instead of opening a blocking empty dialog.
- Bumped app version to `versionCode 5` / `versionName 0.2.4` so these changes can be distributed as a new release.

Important boundaries:
- Expiry uploads remain enabled through the existing WorkManager jobs and save-time retry worker.
- Return rules are still fetched from Supabase at app startup by the existing `fetchReturnRules()` call. That is separate from the updater; the updater itself uses GitHub only.
- If GitHub cannot be reached during the launch check, no newer version can be confirmed and the current app remains usable. Once a newer release is detected, its dialog is mandatory and non-dismissible.
- Android still requires the system package installer and user confirmation. If install-from-this-source permission is not enabled, Android Settings must be opened first.
- Source commits do not prove a successful Gradle build, release publication, APK signing match, or device update. Those remain to be tested.

Source commits:
- MainActivity mandatory dialog/manual check: `8f9382c`, `9a4b87c`
- Force newer releases mandatory: `300a656`
- Background worker upload-only: `f4ca462`
- Version 0.2.4 / code 5: `e1e4624`

## Changes made
App source and version updated as described above. No Supabase schema/configuration, Room schema/version, historical rows, or release assets were changed.

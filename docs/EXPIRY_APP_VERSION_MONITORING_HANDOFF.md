# Expiry Manager — App Version Monitoring Handoff

Last updated: 2026-11-28 (Asia/Kolkata)

## Scope

This feature is only for **MahaMart Expiry Manager**. It is not a group-wide app monitor and does not include MahaMart Scale Manager or other apps.

This is a requirements/design handoff only. No Android source, Supabase schema, live Supabase data, authentication behavior, or app configuration is changed by this document.

## Goal

See which store installations are running which Expiry Manager version, with the last activity timestamps, directly in the existing Supabase Table Editor. No separate dashboard is required.

The primary installation table should be simple and readable:

| device_id | store | version | last_opened | last_closed |
|---|---|---|---|---|
| DHARMARAM-01 | Dharmaram | 2.1 | 2026-11-28 09:15 | 2026-11-27 20:30 |
| DHARMARAM-02 | Dharmaram | 2.0 | 2026-11-28 08:45 | 2026-11-27 21:00 |

The user originally requested Device ID, Store, Version, and Last Seen. The agreed refinement is to keep separate last-opened and last-closed timestamps so the table gives more useful activity information.

## Update behavior — do not create version-history rows

There must be only one current installation row for a given store-specific device assignment.

- First successful registration for a store creates one installation row.
- Opening the app updates `last_opened` on that same row.
- When Android reports the app entering the background, update `last_closed` on that same row. This is best-effort; Android does not guarantee a final callback for force-stop, process kill, crash, battery loss, or device shutdown.
- Installing a newer APK updates `version` on the same row. Do not insert a new row for an app update.
- Reinstalling the app on the same Android device should recover the same device identity and store-specific Device ID, provided Android's app-scoped Android ID remains stable and the app is signed with the same signing identity.
- A device assigned to a different store after the additional authorization flow receives a new Device ID for that store. Keep the previous store assignment row for historical store-wise reporting.

Do not add heartbeat polling, periodic background reporting, or continuous tracking. The goal is minimal database traffic.

## Device ID format

Use sequential, store-specific IDs:

- `DHARMARAM-01`
- `DHARMARAM-02`
- `MALLIAL-01`

The next number is allocated by trusted server-side logic, not by the Android client. Allocation must be atomic so simultaneous first launches cannot receive the same number. Never reuse an allocated number, including after an app is uninstalled or a device moves to another store.

## Current version source

The installed version is reported by the app itself using its actual package version name (and version code internally if needed for comparisons). An APK update changes the version value on the existing row.

A separate release-history system is not part of this minimal request. If a latest-version comparison is later needed, add only the smallest required current-release setting after confirming the desired workflow.

## Free-tier / usage constraints

- Keep the feature event-driven: registration, successful login/store assignment, app open, best-effort background transition, and version change.
- No scheduled polling, no Edge Function heartbeat, no realtime subscription, no dashboard, and no historical version rows.
- Use small payloads and indexed lookup keys.
- Database updates and Edge Function invocations still count toward applicable Supabase quotas. The design aims to keep usage low; it cannot guarantee free-tier usage without checking the current project usage and plan limits.
- Do not add archive downloads or touch existing expiry-entry upload/sync behavior.

## Existing project constraints

- Repository: `rajuuddanti/Expiry`
- Working branch: `feature/v2.5-login-data-archive`
- Preserve the existing Supabase URL, publishable key, `local.properties`, package identity, Room database, expiry-entry data, archive behavior, and current store-login behavior.
- Use the existing store identity returned by the current server-authenticated login flow. Do not trust a locally typed store name as authorization.
- The repository's checked-in v2.5 work uses the private `store-access` backend foundation and existing `store_accounts` login identity. Inspect the current branch and deployed backend before implementation; do not assume a public `stores` table or invent a foreign key without verifying the live schema.
- Never place a Supabase service-role key or plaintext authorization PIN in the APK.

## Implementation is not authorized by this handoff

Before implementation:
1. Inspect the latest branch source and current login/session flow.
2. Inspect the live Supabase schema and RLS/backend functions; do not change live Supabase objects without explicit permission.
3. Confirm how the authenticated store is obtained and how device identity is persisted.
4. Implement the registry/assignment and installation upsert as a separate, reviewed change.
5. Build and test reinstall, app update, app background, force-stop, and store-switch cases.
6. Update this handoff with actual schema/function names and test evidence.

No implementation, SQL migration, live Supabase change, APK build, or device test is included in this documentation handoff.

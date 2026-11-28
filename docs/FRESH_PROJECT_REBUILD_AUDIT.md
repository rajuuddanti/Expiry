# Fresh Supabase + Android Rebuild Audit (v2.5)

Last audited: 2026-11-28 (Asia/Kolkata)  
Repository: `rajuuddanti/Expiry`  
Branch audited: `feature/v2.5-login-data-archive`  
Purpose: record what Git currently contains, what is only described in handoffs, what is missing, and the safe path to a reproducible fresh-project rebuild.

> **Status: NOT YET A COMPLETE REBUILD PACKAGE.** This document is an audit and handoff, not permission to modify the linked/live Supabase project. No live Supabase schema, data, Auth settings, secrets, or Edge Functions were changed for this audit.

## 1. Rebuild target

The intended end state is:

1. A new developer can clone this repository and understand the exact Android build inputs.
2. A new, empty Supabase project can be prepared from version-controlled migrations and checked-in Edge Function source.
3. Required Supabase Auth settings and server-side secrets can be configured from a safe checklist without committing credentials.
4. Store login accounts can be imported from an operator-provided credential workbook through a documented, secure process.
5. The Android APK can be built with the existing project URL and publishable key supplied locally or through CI secrets.
6. A fresh build can be verified with a repeatable smoke-test checklist.

The target workflow may eventually be one backend setup command and one APK build command. Do not advertise or use those commands until they are implemented and tested against a disposable fresh project.

## 2. What is present in Git (branch snapshot)

### Android project and build
- Android application source, Gradle build files, manifest, resources, and app assets are checked in.
- `.github/workflows/android-build.yml` exists.
- Android reads the existing Supabase URL and publishable/anon key through BuildConfig/local build configuration. The real values must remain outside Git.
- The app includes Room persistence, store access client, archive entity/repository, and UI integration as described in the v2.5 handoff.
- The exact current source and Gradle configuration must be inspected before declaring a build reproducible. No build or device test is implied by this audit.

### Supabase migrations present
The branch tree contains these migration files:
- `001_initial_schema.sql`
- `002_secure_android_sync.sql`
- `003_create_expiry_scan_uploads.sql`
- `004_add_catg_to_master.sql`
- `005_expiry_upload_security_foundation.sql`
- `006_expiry_store_ip_registry.sql`
- `20261028_create_expiry_entry_uploads.sql`
- `20261101_add_no_expiry_category.sql`
- `20261101_add_no_expiry_status.sql`
- `202611270001_v25_store_login_archive.sql`

These files are historical migration artifacts. Their presence does **not** prove that applying all of them in filename order creates a clean, working, current backend. They may contain assumptions about pre-existing tables, manually applied live changes, or branch-era schema. A fresh-install sequence must be reconciled and tested before use.

### Edge Functions present
- `supabase/functions/expiry-upload/index.ts` is present in the inspected tree.
- No `supabase/functions/store-access/index.ts` source was found in the inspected branch tree.
- The v2.5 handoff records that a deployed `store-access` Edge Function existed (version 1, ACTIVE, `verify_jwt=false`) on the linked project on 2026-11-27. That deployment statement is historical handoff evidence, not proof that the source or deployment can be recreated from this repository today.

### Documentation present
- `docs/V2_5_LOGIN_AND_DATA_ARCHIVE_HANDOFF.md` records the login/archive requirements, implementation notes, security boundaries, progress, and known gaps.
- `docs/SUPABASE_SCHEMA_HANDOFF.md`, `docs/SQL_REFERENCE.md`, and `docs/SUPABASE_ANDROID_SETUP.md` contain earlier schema/setup observations. They describe different points in the project's history and must not be treated as a single authoritative current schema.
- `docs/GIT_PUSH_COMMAND_CONVENTION.md` records the user's shorthand for pushing all relevant project files.

## 3. Explicit gaps found in the inspected branch

| Component | Audit result | Work required |
|---|---|---|
| `public.store_accounts` creation | No migration creating it was found in the inspected migration set | Add a reviewed migration only after confirming intended columns, constraints, uniqueness, grants and RLS/security model |
| Store mobile/password records | No store credential dataset is included | Obtain the operator's workbook separately; validate and import securely; never commit plaintext passwords |
| `store-access` Edge Function source | Not found in inspected repository tree | Recover the exact deployed source if available, review it, then commit source and tests |
| Edge Function deployment config | Not complete as a reproducible package | Add function config and deploy instructions after source is recovered |
| Edge Function secrets | Values correctly must not be committed; setup instructions are incomplete | Document required secret names, source, scope and safe CLI/dashboard setup; never expose values |
| Auth configuration | Historical Android setup describes anonymous sign-in; v2.5 login uses a custom store-access token flow | Reconcile the actual v2.5 architecture and document exact fresh-project Auth settings; do not blindly enable legacy anonymous sign-in |
| Fresh-project migration order | Not verified | Reconcile migrations into a clean ordered bootstrap and test on a disposable project |
| Master data and return rules | Some are live/imported data and not necessarily fully represented as seed data | Document separate, validated import process; preserve existing `public."MASTER"` schema and data |
| Android local config | URL and publishable key must be supplied separately | Document ignored local config and CI secret names without changing existing values |
| One-command backend setup | Not present/verified | Create an idempotent setup script only after migration and function deployment flow is proven |
| One-command APK build | Workflow exists, but complete clean-clone reproducibility is not established | Verify JDK/Gradle/SDK, config injection, signing behavior and artifact output |
| Automated backend tests | Not established by this audit | Add disposable-project smoke tests and Edge Function tests |
| Store account provisioning | Not reproducible from Git alone | Define secure initial import and reset/rotation process outside Git |

## 4. Important v2.5 backend migration detail

The checked-in migration `supabase/migrations/202611270001_v25_store_login_archive.sql` creates:
- Private schema `private`.
- `private.store_sessions`, with token hash, mobile number reference, expiry and creation timestamp.
- `private.store_archive_sync_state`, with per-store last-successful-sync and active-sync lease fields.
- Revokes access to the private schema/tables from `PUBLIC`, `anon`, and `authenticated`.
- Grants schema/table access to `service_role`.

**Dependency:** both private tables reference `public.store_accounts(mobile_number)`. The migration therefore depends on that public table already existing. No migration creating `public.store_accounts` was found in the inspected set. A clean database cannot safely apply this migration until that dependency is resolved.

The migration alone does not implement the Edge Function actions. The v2.5 handoff describes these deployed actions:
- `login`: checks mobile/password and issues an opaque 30-day token; stores only token hash.
- `me`: returns authenticated store identity.
- `archive-start`: checks the per-store cooldown and starts sync.
- `archive-page`: returns up to 100 eligible records for the authenticated store.
- `archive-complete`: records successful completion.
- `archive-cancel`: clears an abandoned sync lease.

Those behaviors must be confirmed against recovered function source before being treated as the deployable contract.

## 5. Security and data rules that must survive a rebuild

- Never put `SUPABASE_SERVICE_ROLE_KEY` in Android, Git, an APK, or a client-visible config.
- Keep Supabase project URL and publishable/anon key outside committed source. Do not rotate or change the existing project's values as part of documentation work.
- Store passwords must not be stored in plaintext in the Android app, Git, logs, or exported diagnostics.
- Store identity and archive authorization must be derived from server-verified authentication, not a client-supplied store-name field.
- Private session and archive-lock tables must not be accessible to `PUBLIC`, `anon`, or `authenticated`; only trusted server-side code should use the service role.
- Preserve existing `public.expiry_entry_uploads` as the archive source. Do not create a second archive table.
- Archive data is store-scoped; exclude `is_deleted=true`; include valid NO EXPIRY entries; newest first.
- Archive download is manual only. Do not add automatic archive downloads on app launch, screen open, or background worker.
- Archive retention is local-only (90 days) and must never delete server rows or pending local expiry entries.
- Master download is manual-only per the recorded user decision. Do not silently change MASTER schema or sync behavior.
- Preserve the 3 PM Asia/Kolkata business-day boundary for local history behavior.
- Do not apply migrations to the linked/live project during a fresh-project audit.

## 6. Recommended safe rebuild sequence (not yet an executable script)

### Phase A — Freeze and inventory
1. Record the exact branch HEAD and inspect all migrations, Android configuration, Edge Functions, and docs.
2. Export/read-only inspect the actual linked project's schema, RLS policies, Auth settings, deployed function names/versions, and required secrets. Do not print secret values.
3. Compare live state with migration history and label each difference as represented, missing, manually applied, or unknown.
4. Recover the exact deployed `store-access` source from a trusted source. Do not recreate it from memory or guess at its behavior.

### Phase B — Reconcile a clean schema
1. Define the minimum supported fresh-project baseline.
2. Resolve the missing `public.store_accounts` migration and its password-hash/authentication design.
3. Resolve migration dependencies and ordering, including the dependency in `202611270001_v25_store_login_archive.sql`.
4. Decide which historical migrations are required, superseded, or only relevant to legacy deployments.
5. Preserve current `MASTER` table contract; document data import separately from schema creation.
6. Test all migrations against a brand-new disposable Supabase project before publishing a setup command.

### Phase C — Edge Functions and Auth
1. Check in reviewed source for every required Edge Function, including `store-access`.
2. Add function configuration (including JWT verification behavior) and deployment instructions.
3. Document required server-side secret names and safe provisioning. Never add actual secret values.
4. Document Auth provider/settings required by the actual v2.5 design. Resolve conflict with older anonymous-auth instructions.
5. Add tests for invalid credentials, rate limiting, token expiry, store isolation, archive pagination, deleted rows, cancellation, and daily lock behavior.
6. Harden archive completion so the server independently knows all pages were delivered; do not rely solely on a client assertion.
7. Add login attempt throttling/brute-force protection before production use.

### Phase D — Store account and business data provisioning
1. Obtain the store credential workbook from the operator only when needed.
2. Validate store names, mobile numbers, duplicates, blank values and password quality.
3. Define secure password hashing and account provisioning; do not insert plaintext passwords.
4. Import accounts through a protected admin-only process or one-time secure script whose credentials are supplied outside Git.
5. Record how to rotate/reset a store password without exposing existing passwords.
6. Import `MASTER` and return-rule data through a separately documented, validated process. Do not commit sensitive store credentials or assume live data belongs in migrations.

### Phase E — Android build and verification
1. Clone the branch on a clean machine.
2. Supply only the existing Supabase URL and publishable key through ignored local config or CI secrets.
3. Run Gradle sync and the documented debug/release build.
4. Verify APK output path, package name, version, signing, and checksum.
5. Test login, app restart/session validation, offline entry capture, pending upload retry, manual archive sync, pagination, store isolation, retention, and no automatic MASTER/archive downloads.
6. Record actual command output and test evidence. A successful Git commit or workflow trigger is not proof of a successful APK or device test.

## 7. Candidate final operator workflow

Only after all phases above are complete and tested, the intended operator workflow can be reduced to:

- **Backend:** one documented command that validates CLI/project link, applies the reconciled migrations, deploys the required functions, and checks required secret names/configuration. It must stop before destructive operations and must not print secret values.
- **APK:** one documented command or GitHub Actions workflow that validates required local config and builds the requested APK variant.

Until then, do not publish placeholder commands that imply a fresh project can be rebuilt safely.

## 8. Verification record and limitations

- This audit inspected the GitHub tree for `feature/v2.5-login-data-archive`, the v2.5 handoff, the v2.5 SQL migration, the existing `expiry-upload` function source, and the older Supabase setup/schema reference docs.
- The v2.5 handoff reports a deployed `store-access` function on 2026-11-27; this audit did not independently query the live Supabase project.
- No live Supabase schema, data, Auth settings, secrets, or functions were changed.
- No fresh Supabase project was created.
- No migration was applied or tested against a disposable project.
- No Android build, emulator run, or physical-device test was performed for this audit.
- This file records the gaps and the safe work plan; it does not claim the rebuild package is complete.

## 9. Next actions

1. Recover the exact deployed `store-access` source and verify the live function contract.
2. Inspect the full migration set and reconcile it into a fresh-install sequence.
3. Design and document `public.store_accounts` creation and secure account provisioning.
4. Add missing function source/configuration and a safe deployment script.
5. Add a clean Android build script/workflow validation.
6. Test end-to-end in a disposable Supabase project and record evidence here.
7. Only then mark the repository as a reproducible source of truth.

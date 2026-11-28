# Fresh Chat Build Instructions — MahaMart Expiry Manager

Last updated: 2026-11-28  
Repository: `rajuuddanti/Expiry`  
Working branch: `feature/v2.5-login-data-archive`

## The user's intended workflow

The user may lose this chat and start a completely fresh ChatGPT conversation. In that fresh conversation, the user wants to be able to say:

> Connect to my Expiry GitHub repository and Supabase project, inspect everything, and build the APK.

The new assistant/developer must then:
1. Connect to the GitHub repository and inspect the current branch, latest commit, README and handoff docs before editing.
2. Inspect the actual Android Gradle configuration to determine exactly which project-specific values the build requires.
3. Ask the user for the **Supabase Project URL** and **publishable key** when needed for the selected project/build. These values are project-specific and must not be guessed or reused from another project.
4. Never ask the user to paste a service-role key into chat or Android. If a server-side secret is needed, explain where the user can enter it securely in Supabase Dashboard/CLI without sharing its value in chat.
5. Inspect the repository's SQL migrations and Edge Function source. Execute only the SQL required for a fresh project, in a safe, ordered and verified way. Do not run migrations against the user's existing/live project unless the user explicitly approves that operation.
6. Identify any Auth or Dashboard action the user must perform personally. Give the user a short, exact checklist and explain why it cannot be performed by SQL or safely automated. Do not ask the user to repeat information already in Git.
7. Build the APK only after the project configuration is supplied and validated. Report the actual build result and artifact location; do not claim success without build evidence.

## What belongs in Git versus what the user supplies

| Item | Source of truth / handling |
|---|---|
| SQL schema, tables, indexes, database functions, triggers, grants and RLS policies | Version-controlled SQL migrations in `supabase/migrations/`, once reconciled and tested |
| Edge Function source | Version-controlled source under `supabase/functions/<function-name>/` |
| Supabase CLI project configuration | Version-controlled non-secret config files, after validation |
| Supabase Project URL | User supplies for the selected project at build/setup time; do not store a real URL in Markdown |
| Supabase publishable key | User supplies for the selected project at build time; do not store a real key in Markdown |
| Service-role key and other privileged secrets | User provisions directly in Supabase secret storage or CI secret storage; never paste into chat or commit |
| Auth provider toggles, redirect URLs, email/SMS provider credentials and project-level Auth settings | Automate through supported CLI/config only if the project's supported config can represent them reliably; otherwise provide the user exact Dashboard steps |
| Store mobile/password accounts | Secure account import/provisioning process; never commit plaintext passwords or credential workbooks |
| APK signing key/password | User-provided secure local/CI secret; never commit |

**Do not create Markdown files containing example assignments such as** `SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co`. The user does not want project URL/key values embedded in Markdown. The project URL and publishable key are inputs collected when needed, not documentation constants.

## SQL versus Auth clarification

SQL is appropriate for database objects and database permissions:
- Tables and columns
- Indexes and constraints
- PostgreSQL functions and triggers
- RLS enablement and policies
- Database grants and schema privileges

SQL does **not** configure every Supabase Auth feature. Provider enablement, site URL, redirect allowlists, email/SMS delivery providers, OAuth credentials and other project-level Auth options may require Dashboard or supported Supabase CLI/configuration. For each fresh project:
- Inspect the actual app and Edge Function authentication flow first.
- Check whether each required setting is expressible in the checked-in Supabase config.
- Put repeatable database-side setup in migrations.
- Put supported project configuration in version-controlled non-secret Supabase config.
- For settings that require Dashboard action, give the user exact steps and a checklist to confirm completion.
- Never enable Anonymous Sign-Ins just because an older document mentions them. The v2.5 custom store-login flow must be reconciled with the actual deployed `store-access` function and Android client.

## Current known v2.5 caveat

The repository audit recorded:
- SQL migration `supabase/migrations/202611270001_v25_store_login_archive.sql` creates private session and archive-sync-state tables, but references `public.store_accounts(mobile_number)`.
- No migration creating `public.store_accounts` was found in the inspected migration set.
- The v2.5 handoff records a deployed `store-access` Edge Function, but its source was not found in the inspected Git tree.
- Therefore the login/auth backend is **not yet fully reproducible from Git**. Do not invent a replacement function or assume the migration chain can create a clean project.
- Recover and review the exact deployed function source (or have the user provide/export it), add it to Git, reconcile the missing store-account migration and test the complete migration sequence on a disposable Supabase project.
- Existing older docs mention anonymous Auth. Do not treat that as the v2.5 requirement without source verification.

## Existing project versus fresh project

Always ask/confirm whether the target is:
- **Existing project:** inspect/read-only first. Do not apply schema changes, reset, seed, alter Auth settings, replace functions or rotate secrets without explicit permission.
- **Brand-new project:** use the verified fresh-install procedure after it exists. A fresh project must not be confused with the user's existing live project.

Do not run `supabase db reset` or destructive SQL on a linked project. Do not apply historical migrations blindly. A migration marked as applied in the live project's migration history may not be safe to re-run manually.

## Desired end-state commands

Once the missing source and setup gaps are resolved and tested, aim for:
- One backend setup command that validates the target project, applies only the verified migrations, deploys checked-in Edge Functions, checks required secret names/configuration, and stops safely when user-only Dashboard actions remain.
- One APK build command that validates the selected project's URL and publishable key are supplied locally/through CI and produces the APK.

Do not document or advertise these as working commands until they actually exist and pass a fresh-project test.

## Required final report after a build

Report:
- Git repository, branch and commit used.
- Target Supabase project reference (safe identifier only; do not expose keys).
- Which migrations were applied, and to which project.
- Which Edge Functions were deployed.
- Which Auth/Dashboard steps the user completed or still needs to complete.
- Exact Gradle/build command and success/failure.
- APK output path, version and checksum if available.
- Any unverified behavior or device tests still outstanding.

## Safety reminders

- Never commit `local.properties`, real project URL/key values, service-role keys, store passwords, signing keys or private credential workbooks.
- Never print secret values in logs or final responses.
- Do not change the user's existing Supabase URL/key configuration unless specifically asked.
- Do not modify live Supabase schema/data/policies/functions/secrets without explicit permission.
- Do not claim build, install, release or device verification without evidence.

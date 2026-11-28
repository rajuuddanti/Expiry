# Supabase Project URL, Publishable Key, and Auth Configuration

Last updated: 2026-11-28  
Branch: `feature/v2.5-login-data-archive`

## Yes — these values are project-specific

Each Supabase project has its own:
- Project URL, usually `https://<project-ref>.supabase.co`.
- Publishable key (or legacy anon key).
- Project reference / project ID.
- Auth provider and security settings.
- Server-side Edge Function secrets and deployed functions.

When building the APK for a different Supabase project, use that project's URL and publishable key. Do not reuse values from another project.

## Android configuration format

The Android build expects these property names:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_PROJECT_PUBLISHABLE_OR_ANON_KEY
```

The checked-in template is:
- `config/supabase-project.example.properties`

For a local build:
1. Copy the two property lines from the template.
2. Paste them into the root Android project's `local.properties`.
3. Replace the placeholders with values from the intended Supabase project's API settings.
4. Keep `local.properties` local and ignored by Git.
5. Build the APK.

The example template intentionally contains placeholders, not real credentials. Never put a `service_role` key in Android, `local.properties`, the template, or Git. The publishable/anon key is the client key; it is not a substitute for server-side authorization.

## Can this be an SQL file?

**No — not for the project URL or publishable key.** SQL runs inside a database and cannot select or change which Supabase project the Android app connects to. The URL and publishable key are client build configuration, so use a properties file or environment variables.

Auth settings are also not all SQL settings. Database migrations can create tables, indexes, functions and database grants/policies. Supabase Auth provider settings, URL configuration, redirect URLs, email/SMS provider settings and project-level security options are configured through the Supabase Dashboard or supported Supabase CLI/configuration APIs. Do not pretend a SQL migration configures all of these.

## Auth must be recorded per project

For each new Supabase project, maintain a project setup record with:
- Project name and project reference (non-secret identifier).
- Project URL.
- Publishable key location (store the value in the approved secret/config store, not in Git).
- Which Auth design the deployed app actually uses.
- Required Auth provider toggles and URL/redirect settings.
- Edge Function names and their JWT verification settings.
- Required Edge Function secret *names* and how an administrator provisions their values.
- Date and verification evidence for the setup.

### Important v2.5 distinction

The v2.5 handoff describes a custom store-login flow implemented by the `store-access` Edge Function. It must not be confused with the older anonymous Supabase Auth setup described in `docs/SUPABASE_ANDROID_SETUP.md`.

Before configuring a fresh project, inspect the exact deployed `store-access` source and the current Android client. Confirm whether the function uses the publishable key plus its own opaque store-session bearer token, and confirm its JWT verification setting. Do not automatically enable Anonymous Sign-Ins just because an older setup document mentions them.

The v2.5 Edge Function source was not found in the inspected branch at the time this file was written. Therefore this document deliberately does not claim a complete Auth provider recipe or provide guessed settings. Recover and review the actual function source first.

## Separate configuration layers

| Layer | Where it belongs | Commit to Git? |
|---|---|---|
| Project URL | Android `local.properties` / CI variable | Placeholder only |
| Publishable/anon key | Android `local.properties` / CI secret | Placeholder only |
| Service-role key | Supabase Edge Function secret store only | Never |
| Auth provider / redirect settings | Supabase Dashboard or supported CLI config | Document settings; no secret values |
| Database schema, RLS and grants | Reviewed SQL migrations | Yes, after validation |
| Edge Function source | `supabase/functions/<function>/index.ts` | Yes |
| Edge Function secret values | Supabase secret store | Never |
| Store mobile/password records | Secure admin provisioning/import process | Never plaintext in Git |

## Do not overwrite existing project configuration

- This template does not change the existing project's URL or key.
- Do not replace the current `local.properties` automatically.
- Do not change Supabase Auth settings or Edge Function secrets without explicit approval.
- Do not apply migrations to the linked/live project as part of a local APK build.
- For multiple projects, maintain a separate local properties file or CI environment for each target and verify the selected project before building.

## Verification still required

This file documents the correct configuration boundary and property names. It does not prove that a fresh Supabase project is fully configured. The full rebuild gaps are tracked in `docs/FRESH_PROJECT_REBUILD_AUDIT.md`.

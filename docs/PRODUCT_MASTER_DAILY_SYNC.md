# Product Master Daily Sync Strategy — Historical Proposal

Last updated: 2026-10-28 (Asia/Kolkata)

> **Superseded/needs review:** This document records an earlier proposed design based on `product_master`, `product_master_staging`, cloud `expiry_entries`, and revision/delta sync. The latest reported backend direction uses `public.MASTER` plus `public.expiry_return_rules`, and expiry entries are intended to remain local in Room and be exported/shared as CSV. Do not implement the old cloud expiry-upload or staging/delta architecture unless the user explicitly changes the requirement.

## Historical proposal

The user previously considered daily staging, validation, and delta publishing for a product master where most rows remain unchanged. That design included a master revision, change log, tombstones for removals, and Room delta application. It remains useful only if the user later requests incremental master publishing/sync and the current schema is revalidated.

## Current applicable principles

- Keep the product master available locally for offline lookup.
- Preserve source code values as text and preserve leading zeroes.
- Never clear the local master until a replacement is validated and ready to apply.
- A master refresh must not delete local expiry records or unsynced work.
- Do not change Supabase URL/key configuration unless explicitly asked.
- Never embed a service-role key in the APK.
- Do not claim the assistant has queried the user's Supabase project unless actual authorized connector results support that claim.

For current requirements, use `docs/CURRENT_HANDOFF.md`, `docs/PROJECT_CONTEXT.md`, and `docs/EXPIRY_RETURN_RULES_AND_SCHEDULE.md`.
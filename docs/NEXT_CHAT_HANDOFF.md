# Next Chat Handoff — Mahalaxmi Expiry Manager

Last updated: 2026-10-29 (Asia/Kolkata)

## Paste this into a new chat

Continue Mahalaxmi Expiry Manager in `rajuuddanti/Expiry`. Read these first:
- `README.md`
- `docs/CURRENT_HANDOFF.md`
- `docs/PROJECT_CONTEXT.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/DATA_MAPPING_AND_IMPORT.md`
- `docs/EXPIRY_RETURN_RULES_AND_SCHEDULE.md`
- `docs/DECISIONS_AND_CHANGELOG.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/SQL_REFERENCE.md`
- `docs/SCHEMA_REFERENCE.md`
- `docs/EXPIRY_UPLOAD_SECURITY_PLAN.md`
- `docs/IMPLEMENTATION_LOG_2026-10-29.md`

## Important current decisions

- Product master is reported as `public.MASTER`, with `"SkuCode"`, `"SkuLongName"`, `"Catg"`. User reported 124,570 rows; do not treat this as a live verified count.
- The single table `public.expiry_return_rules` holds both category and brand rules. It reportedly has 32 rows.
- Brand rules: HIMALAYA 60 days, KELLOGGS 30 days. Brand overrides category.
- Expiry records are local-only in Room and exported/shared as CSV. Do not implement cloud upload of expiry entries unless the user changes their mind.
- Keep the Supabase config unchanged.
- FCFS canonical SKU mapping for duplicate product names; preserve code values as text and pad digit-only codes shorter than 5 digits to 5 digits.
- Schedule covers 2026-10-23 to 2027-12-31, with no more than two categories per day. Continue beyond 2027 from the existing rotation state.
- Proposed effective category return days: `max(configured_return_days, schedule_interval_days)`. Do not change source rule values. Brand exemption from this floor remains unconfirmed.
- Requested Return Rules popup should show today's scheduled category return windows and always list the two brand rules.
- These popup/schedule enhancements are not confirmed implemented or tested.

## Branch and code status

Recent work is associated with `feature/new-expiry-upload-table`, but branch histories have diverged. Verify branch HEAD and inspect current source files before applying patches. Historical commit IDs recorded in prior context:
- `9002bde4b230e5475a15ecebeae374120c26bd43`
- `ca764f735349bc2d4eea7efc0b81dbaf96392668`
- `3dd706937bc072f77e5a15a0ac9bd6d61c911d01`
- `caa9ea584da921bcad72138380e8385680beab12`

These SHAs are notes, not proof that the current branch contains all of them. No Android build/test success was reported in the previous session.

## Next steps

1. Verify current branch, recent commits, and actual Android source tree.
2. Build with Android Studio/Gradle and use real build output to fix compiler issues.
3. Verify direct SKU and EAN scan mapping, preserving the raw scanned code.
4. Verify product master cache/sync timing and offline behavior without changing Supabase config.
5. Implement reading/caching active rules from `expiry_return_rules`.
6. Add Return Rules icon/dialog: categories scheduled today plus category return days, then Himalaya 60 / Kellogg's 30.
7. Implement the effective return-window calculation after verifying schedule intervals and user intent on brand overrides.
8. Preserve the supplied calendar through 2027 and implement correct continuation beyond 2027, maintaining at most two categories per day.
9. Update docs with verified implementation/test results; never claim build success without evidence.

## Accuracy/security rules

- Do not claim Supabase was queried/changed unless it actually was.
- Treat the database row counts above as user-reported context.
- Do not modify the `MASTER` schema or Supabase URL/key settings unless explicitly requested.
- Never ship a service-role key in the APK.


## Latest recorded work — 2026-10-29

- Branch: `feature/new-expiry-upload-table`.
- Latest UI change before the documentation commits: `6413c3737b763483b64a3630a5b664c15fef1342` — the main screen uses the actual category names scheduled today as the heading. Do not add a separate “Present Day Categories” label. Place Return Rules on the row below.
- Documentation commits: README index and the changelog were updated after the UI commit. Check the live branch HEAD before making changes.
- New log: `docs/IMPLEMENTATION_LOG_2026-10-29.md`.
- Use `README.md` as the documentation index and this file as the copy-ready entry point for a fresh chat.

## Before coding — resolve documentation conflicts

Some older handoff documents disagree about expiry entry storage/upload behavior: parts describe local-only Room entries with CSV export, while other recent work describes manual uploads to `public.expiry_entry_uploads`. Do not guess which description is authoritative. Inspect current branch source, migrations, and the latest explicit user decision, then update `CURRENT_HANDOFF.md`, `IMPLEMENTATION_STATUS.md`, and this file consistently. Never change Supabase configuration during that reconciliation.

## Fresh-chat verification checklist

1. Fetch current branch HEAD and inspect recent commits before assuming recorded SHAs are current.
2. Inspect the actual Kotlin source and SQL migration files relevant to the requested task.
3. Check GitHub Actions for the current commit; report success/failure/in-progress accurately.
4. Separate source-level implementation from build verification and real-device/backend verification.
5. After each material change, update the dated implementation log, implementation status, and changelog/handoff as appropriate.

## UI follow-up — 2026-10-29

- Latest Scan screen refinement: today's scheduled categories are separated by newlines so two categories appear one above the other.
- Commit: `9dee3136d61a3af31e9f74dd19be510d0d5d2136`.
- Check GitHub Actions and inspect the screen on a device/emulator before calling the change verified.

## Modern Scan screen styling — 2026-10-29

- Scan screen visual refresh commit: `b5b8e3040410fce4431981c5c9d781e9a3895e1a`.
- The header is now white with small red MAHALAXMI MAHAMART branding and a bold dark Expiry Manager title; current-store card is soft lavender; fields use rounded corners and red focus accents; expiry date and Save Entry controls have updated shapes.
- Categories remain dynamic from the schedule and are stacked vertically.
- Check GitHub Actions for this commit and inspect the UI on a device/emulator before stating the change is verified.

# Implementation Plan — Mahalaxmi Expiry Manager

Last updated: 2026-10-28 (Asia/Kolkata)

Use `docs/CURRENT_HANDOFF.md` as the authoritative current handoff. Check the actual branch/source before marking any item complete.

## Repository/build verification
- [ ] Verify current branch HEAD and whether recorded recent commits are present.
- [ ] Inspect current Android source tree and Gradle configuration.
- [ ] Run a real debug build; record the actual result/log.
- [ ] Install/run on a device and test scanning, local persistence, and CSV share before claiming readiness.

## Product master and scan lookup
- [ ] Verify actual `MASTER` query/API path and current row count from user-provided/live-authorized evidence.
- [ ] Verify master refresh runs on intended 24-hour cadence and manual refresh works.
- [ ] Verify offline lookup after a successful import.
- [ ] Test direct SKU scan: canonical SKU and scanned code can match.
- [ ] Test barcode/EAN scan: product name resolves, FCFS canonical SKU is selected, and raw scanned code is retained.
- [ ] Test short-code padding (<5 digits to 5) and preserve 5+ digit codes as text.
- [ ] Verify no master refresh clears local expiry entries.

## Local expiry records and export
- [ ] Confirm expiry entries are stored in Room, not cloud-uploaded.
- [ ] Verify app restart and offline use preserve entries.
- [ ] Verify CSV columns against current user requirement: Store, SKU code, SKU name, QTY; check whether Scanned code and Expiry date are also included.
- [ ] Test share sheet with email and WhatsApp-compatible targets.
- [ ] Verify store selection/PIN behavior and 3 PM rollover.

## Return rules popup
- [ ] Add active-rule model/repository for `public.expiry_return_rules`.
- [ ] Read rules without modifying `public.MASTER` or Supabase config.
- [ ] Add tappable Return Rules info icon on the main screen.
- [ ] Popup shows categories scheduled for the current inspection date with configured/effective return days.
- [ ] Popup always includes Himalaya — 60 days and Kellogg's — 30 days in a separate Brand Rules section.
- [ ] Brand rules override category rules.
- [ ] Calculate category effective days as `max(configured_return_days, schedule_interval_days)` once schedule interval logic is verified.
- [ ] Keep configured rule values in Supabase unchanged.
- [ ] Confirm whether brand rules are exempt from the schedule-day floor; until then, display them as fixed values.

## Schedule and calendar
- [ ] Inspect `SCHEDULER.csv` and existing code to capture exact category rotation and rollover rules.
- [ ] Preserve supplied schedule dates from 2026-10-23 through 2027-12-31.
- [ ] Ensure no more than two categories are assigned per day.
- [ ] Implement continuation after 2027 from exact final schedule state; do not restart rotation arbitrarily.
- [ ] Verify schedule never causes a product to miss its return window under the agreed effective-days formula.
- [ ] Test month/year boundaries and leap-year dates.

## Data/security constraints
- [ ] Do not change Supabase URL/key config unless user explicitly asks.
- [ ] Never put a service-role key in the APK.
- [ ] Do not reintroduce cloud expiry-upload/device-registration/security tables without explicit user approval.
- [ ] Verify actual RLS/access policies before claiming production safety.

## Older tasks that may no longer apply
- [ ] Historical product_master staging/publish and delta-sync design: re-evaluate before continuing because latest reported master table is `MASTER` and expiry entries are local-only.

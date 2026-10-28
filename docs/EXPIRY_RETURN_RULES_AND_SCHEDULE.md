# Return Rules and Category Schedule

Last updated: 2026-10-28 (Asia/Kolkata)

## Purpose

This document captures the agreed design for how category inspection scheduling and supplier return windows should work together in Mahalaxmi Expiry Manager.

**Important status:** This is the documented target behavior. Do not claim the Android UI or logic has implemented or tested it until the source code is changed and a real build/test confirms it.

## Current Supabase return-rule table

The configured rules are stored together in one table: `public.expiry_return_rules`.

Rule types:
- `category`: category-specific return window.
- `brand`: brand-specific override.

Expected columns:
- `id`
- `rule_type` (`category` or `brand`)
- `match_value`
- `return_days_before_expiry`
- `priority`
- `note`
- `is_active`
- `created_at`

A unique constraint is intended on (`rule_type`, `match_value`). The table was created with RLS enabled and active-rule SELECT access granted to anon/authenticated, according to the recorded Supabase migration result. Verify actual live policy/schema if making further backend changes.

The intended production data tables currently recorded in Supabase are:
- `public.MASTER`: product master, with `"SkuCode"`, `"SkuLongName"`, and `"Catg"`.
- `public.expiry_return_rules`: category and brand return rules.

The obsolete cloud-upload/device/security tables were removed in the recorded migration. Expiry entries are intended to stay on-device and be exported/shared as CSV; do not reintroduce a cloud upload table unless the user explicitly changes this requirement.

## Configured rules

### Brand overrides
- HIMALAYA: 60 days before expiry.
- KELLOGGS: 30 days before expiry.

Brand rules take precedence over category rules when a product matches a configured brand. For example, a Himalaya product in Beverages uses the Himalaya rule, not the Beverages category rule.

### Category rules
The currently recorded category rules are:
- DAIRY FOODS: 10 days
- PACKED ITEMS: 10 days
- SWEETS: 10 days
- PICKLES: 30 days
- PACKED ATTA: 10 days
- NOODLES and PASTA: 20 days
- NAMKEEN CHIPS: 15 days
- MASALA SPICES: 30 days
- JAMS and SAUCES: 30 days
- INSTANT FOODS: 10 days
- DRY FRUITS: 20 days
- BISCUITS: 15 days
- CHOCOLATES: 15 days
- BREAKFAST CEREALS: 30 days
- BEVERAGES: 20 days
- SOAPS AND BODY WASH: 45 days
- SKIN CARE: 45 days
- SHAVING NEEDS: 30 days
- SANITARY PADS: 45 days
- ROOM FRESHENERS: 30 days
- MOSQUITO REPELLANT RAT POISON: 45 days
- HYGIENE: 30 days
- HOUSE And KITCHEN CLEANING: 45 days
- HEALTH And OTC: 45 days
- HAIR CARE: 45 days
- DETERGENTS: 45 days
- DEOS And SPRAYS: 45 days
- DENTAL CARE: 45 days
- BABY CARE: 45 days
- TALC POWDER: 45 days

These are the recorded seed values, not proof that every category label matches every current `MASTER.Catg` value exactly. Normalize/cross-check category strings when implementing lookups; do not silently invent mappings.

## Return-days versus schedule-days adjustment

User's proposed rule:
- If configured return days are less than the category's inspection schedule interval, increase the effective return window to the schedule interval.
- Otherwise, keep the configured return days.

Formula for category rules:
`effective_return_days = max(configured_return_days, schedule_interval_days)`

This is an app-side calculation proposal. Preserve the configured value in Supabase; do not overwrite the source rule merely to calculate the effective value.

Brand rules are special overrides. The user has not yet explicitly confirmed whether the schedule-day floor should also modify brand-specific values. Safest default until confirmed: display configured brand rules as fixed (Himalaya 60, Kellogg's 30) and do not silently change those values.

Important distinction: this formula widens the app's eligibility/inspection window; it does not change a supplier's actual contractual return policy. If supplier terms impose a strict deadline, the app must not represent a calculated window as changing that supplier policy.

## Proposed Return Rules popup

Add a tappable info/help icon labelled something like **Return Rules** on the main screen. On tap, show a popup/dialog with:

1. Categories scheduled for the current inspection date.
2. Each category's configured return days and, where applicable, effective return days.
3. A separate Brand Rules section that always shows:
   - Himalaya — 60 days
   - Kellogg's — 30 days

Example only (values are illustrative and must be populated from the live rules and schedule):
- Beverages — return window 20 days
- Dairy Foods — return window 10 days
- Himalaya — 60 days
- Kellogg's — 30 days

Do not hard-code example category values into the UI. Fetch active rules from `expiry_return_rules` and derive today's categories from the schedule.

## Schedule through 2027 and beyond

The current inspection calendar was supplied as `SCHEDULER.csv` and covers 23 October 2026 through 31 December 2027. The exact rotation rules and rollover-priority behavior must be preserved from that source; do not invent a new sequence.

Desired long-term behavior:
- Keep the existing calendar intact through 31 December 2027.
- Continue the sequence from the final scheduled state at the end of 2027.
- Generate future dates from the rotation rules instead of requiring a new yearly CSV.
- Never schedule more than two categories on a day, preserving the existing Rollover Priority Engine rule.
- Make the schedule and return windows cooperate so a product does not get missed because its return window is shorter than the inspection interval.

Before implementing indefinite generation, inspect and document the actual schedule format and rollover algorithm from `SCHEDULER.csv` / current code. Do not restart the rotation arbitrarily on 1 January 2028.

## Implementation checklist

- [ ] Inspect current Android source and locate the schedule/calendar implementation.
- [ ] Add typed client model/repository for `expiry_return_rules`.
- [ ] Fetch active rules safely; cache locally if the app must show rules offline.
- [ ] Determine today's scheduled categories from the existing schedule.
- [ ] Add tappable Return Rules icon and popup.
- [ ] Show today's category return windows and fixed brand rules.
- [ ] Implement brand-over-category precedence.
- [ ] Implement `max(configured return days, schedule interval)` for category effective windows, once schedule interval calculation is verified.
- [ ] Decide explicitly with user whether brand overrides are exempt from the schedule-day floor.
- [ ] Preserve schedule through 2027 and continue the exact rotation beyond 2027.
- [ ] Test boundary dates, category rollover, brand overrides, missing rules, offline mode, and schedule/return-window edge cases.
- [ ] Build and run the app before claiming completion.

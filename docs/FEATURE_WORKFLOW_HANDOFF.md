# Feature Workflow & Implementation Checklist
Updated: 2026-10-26
Branch: `feature/new-expiry-upload-table`

## Screen/workflow outline
1. App opens and determines the business date using local time with a 3:00 PM rollover.
2. Display category rows scheduled for that date from the Excel-derived schedule.
3. Staff select one assigned category.
4. The category screen offers a SKU Lookup module and the main expiry-entry flow.
5. SKU Lookup searches by name and SKU code, and by EAN by searching the same `SkuCode` field (SKU and EAN codes are stacked in rows; do not add a separate EAN column). Results show `Catg` and clearly identify whether it matches today's assigned categories.
6. Main entry validates every scanned/manual EAN against MASTER. Correct category: verified and name auto-filled. Different category: block saving. No match: keep name blank and permit only as unverified, retaining EAN, quantity, and expiry date. Blank/ambiguous category: do not treat as verified.
7. Expiry calendar independently applies the allowed cutoff for the product's category.
8. Save locally and sync to Supabase according to the existing sync contract.
9. At 3 PM, start the new business day and clear only local daily history. Supabase history remains untouched.

## Scheduling
- Workbook: `expiry_schedule_till_dec_2027.xlsx` (user-provided; stored in ChatGPT Library, not yet added to this repository).
- Excel schedule is authoritative through 2027-12-31. Preserve all assigned dates, labels, cycle requirement values, and statuses; do not run a new algorithm over those dates.
- Starting 2028-01-01, continue existing cycles using each category's last scheduled date and its cycle frequency.
- Post-2027 collision policy: max two categories/day, core item + previous day's carried item prioritized, overflow carries forward.
- Do not silently reinterpret category cycle frequencies from category names.

## Current implementation status
- [x] `public.MASTER.Catg` exists in the live Supabase schema.
- [x] `public.expiry_scan_uploads` exists in live Supabase schema and in migration 003.
- [x] Requirement/schema handoff docs added to this branch.
- [ ] Add migration for `MASTER.Catg` if no equivalent checked-in migration exists.
- [ ] Decide schedule storage/import design and bring the user workbook into the repository or app asset pipeline.
- [ ] Confirm EAN/barcode field/mapping.
- [ ] Confirm category-level expiry cutoffs where user policy lists conflicting periods.
- [ ] Implement schedule display, SKU Lookup, validation, date-picker restrictions, 3 PM local reset, store PIN, and soft-delete sync.
- [ ] Review RLS policies and run advisors.
- [ ] Build and test Android app, including offline and sync scenarios.

## Test cases required before declaring done
- A scheduled Excel date renders its exact categories and status.
- Two or more Excel assignments on a date are preserved exactly.
- Post-2027 cycles continue from last scheduled dates, not from Jan 1.
- Post-2027 collisions never exceed two categories/day; overflow carries forward.
- Product in today's category saves with name from MASTER.
- Product from a different category cannot be saved as a verified scan.
- Unknown EAN keeps name blank and retains EAN/qty/expiry date as unverified.
- Search works by name and SKU; EAN search after mapping confirmation.
- Date beyond category cutoff cannot be selected.
- At 3 PM local time, local daily history resets without any Supabase row deletion.
- Explicit deletion syncs `is_deleted=true`.
- Store change requires PIN.
- Room schema changes include a version bump and migration.

# Expiry App — Requirements Handoff
Updated: 2026-10-26
Repository: `rajuuddanti/Expiry`
Working branch: `feature/new-expiry-upload-table`

## Guardrails
- This is the actual Expiry app repository. Do not copy configuration or Supabase details from the unrelated MahaMartAudit reference archive.
- Preserve the existing app UI and working behavior unless a specific change is required.
- The current task is to document the requirements and database contract before implementation. Do not claim the Android features are implemented until code is changed and built/tested.
- Do not create a subcategory field/module. Category is only `public.MASTER.Catg`.

## Daily category schedule
- Source of truth through 2027-12-31: user-provided `expiry_schedule_till_dec_2027.xlsx`.
- Excel has columns: Date, Day, Assigned Category, Original Cycle Requirement, Status. It already contains the user's scheduling decisions and collision handling. Do not recalculate or rewrite any date in this range.
- The schedule includes multiple category assignments on some dates. Preserve all rows exactly as supplied.
- From 2028-01-01 onward, continue category cycles (Option B) using the original cycle requirement and last scheduled date for each category; do not restart cycles on Jan 1.
- Carry-forward/priority behavior after Excel ends must follow the user's established rule: maximum two categories per business day; prioritize the core item and yesterday's carried-over item; excess moves forward. Do not apply this logic to modify Excel-dated rows.
- Business day rolls over at 3:00 PM local device time.
- Show the current business day's assigned categories when the app is opened.
- The example schedule records found in the Excel include 2026-10-23 Dairy Foods (Completed), 2026-10-24 Packed Items (Completed). The workbook itself is the authority for all other schedule rows.

## Category SKU Lookup
For the categories assigned to the current business day, provide a separate SKU Lookup module/tab. Search full master by:
- Product name (`SkuLongName`)
- SKU code (`SkuCode`)
- EAN/barcode using the same `SkuCode` field; SKU and EAN codes are stacked as rows, not separate columns. SKU-code rows appear first, followed by EAN rows for those products. Preserve source order and use the agreed FCFS association.

Results should display product name, SKU code, EAN when available, and actual category from `Catg`. Clearly mark whether a result belongs to today's assigned category.

## Main expiry entry validation
- If barcode/EAN resolves to a product whose `Catg` matches one of today's assigned categories, allow entry and populate the product name from MASTER.
- If it resolves to a product from another category, block saving as a valid scan and show a clear wrong-category message.
- If EAN is not found in MASTER, keep product name blank and preserve scanned EAN, quantity, and expiry date as an unmatched/unverified entry. Do not invent a name/category. Flag it for later review.
- If a lookup resolves to an ambiguous product or category is blank, do not silently treat it as verified; flag/block pending verification.
- Apply the same checks to scanned and manually entered EANs, and SKU Lookup selection must not bypass validation.

## Expiry-date calendar restrictions
- Independently of daily category assignment, use the product's `Catg` from `public.MASTER` to apply its category return-policy cutoff. Dates beyond the allowed cutoff must be disabled/greyed out.
- Do not use subcategories; master currently has only Catg.
- Some previously provided category policy descriptions contain more than one return period for the same category. Do not guess based on product/brand/shelf life when that information is absent from MASTER. Resolve such conflicts with a single category-level policy before implementing those cases.
- The user gave an example cutoff for Beverages and a formula using “expiry day < today + 20 days”. Confirm whether the exact boundary date is allowed before finalizing implementation; the inequality as written excludes the boundary.

## Local history and Supabase
- Local scan history resets at 3:00 PM local time for the new business day.
- Daily local reset must not delete or bulk-delete rows in Supabase.
- Explicit deletion of one scan is a soft delete: set `is_deleted = true` and sync that update to Supabase.
- Store switching requires PIN `3331`. Do not expose the PIN in UI or logs.
- Daily schedule completion is day-scoped; don't mix a prior business day's records into today's list.

## Open implementation checks
1. Confirm the EAN/barcode column or mapping. Current `public.MASTER` schema only shows `SkuCode`, `SkuLongName`, `Catg`; no EAN column is currently present in the inspected schema.
2. Confirm a single cutoff rule for categories whose policy descriptions contain multiple periods.
3. Confirm boundary semantics for “today + N days”.
4. Inspect existing Android code before altering Room schema or sync logic; bump Room DB version and provide migration if schema changes.
5. Validate RLS policies and sync operations; never ship service-role credentials in Android.
6. Build and test before reporting completion.

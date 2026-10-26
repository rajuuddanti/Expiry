# Decisions and Conversation Changelog

Last updated: 2026-10-26

This file records the decisions confirmed in conversation. Distinguish user-confirmed decisions from items still to verify.

## Confirmed decisions

### Purpose
Build an Android expiry-date capture tool for Mahalaxmi Mahamart store staff. Staff scan products, record quantity and expiry date, and later review/sync the records.

### Master file
- Original master uploaded in conversation: `Multiple MRP Report20260223060241.CSV(1).xlsx`.
- Source columns: `SkuCode/EAN`, `SkuLongName`.
- SKU codes and EANs are intentionally stacked in one code column, not provided in separate SKU and EAN columns.
- User says first approximately 28–30K rows are SKU codes in sequence and EAN rows follow. Verify actual boundary using staging data.
- One searchable destination column `scan_code` is desired so app scanning logic doesn't have to choose between separate SKU and EAN columns.

### Code formatting
User's exact rule: “if any sku/ean code is less that 5 digits add 0 before to amke it 5 digit thats it”.
- Fewer than 5 digits: left-pad to five digits.
- Five or more digits: unchanged.
- Keep all codes as text.

### Matching / duplicates
User's exact intended logic:
- Whichever code is scanned should get the product name.
- If an EAN row's name is used to reverse lookup a SKU, search SKU rows in source order and choose the first match (FCFS).
- If duplicate names occur, first matching SKU wins.
- Avoid overcomplicating this logic.

### Tables
- Two production tables only: `product_master` and `expiry_entries`.
- A temporary `product_master_staging` helper table is currently used to import the two-column source.
- No category column/table is wanted.
- Live `product_master` screenshot showed exactly three NOT NULL TEXT columns: `scan_code`, `sku_code`, `sku_name`.

### App and data handling
- Store selected on first launch and saved locally; editable in Settings.
- Scan by camera or manual code entry.
- Autofill product name and canonical SKU.
- Quantity and expiry date (calendar picker).
- Offline-first: persist locally, then sync online.
- Daily session runs 3 PM IST to next day 3 PM.
- Preserve unsynced records.
- Soft-delete corrections/deletions; don't physically delete expiry entries.
- Keep UI straightforward and store-staff friendly.

### Security
- Never put service-role key into APK.
- Store selection is not authentication.
- Configure RLS and a controlled access path before production.

## Conversation and import troubleshooting

1. User reported inability to upload master to Supabase and attached an error report.
2. Error report indicated an Excel XLSX binary was being parsed as CSV (`PK... [Content_Types].xml`), causing malformed quote and field-count errors. Solution was to export as real CSV UTF-8 from Excel, not rename XLSX.
3. User supplied the actual XLSX master. It was previously observed as about 124,212 rows with source columns `SkuCode/EAN` and `SkuLongName`; a prior quick inspection reported 12,358 blank-code rows. Revalidate from the source/staging rather than treating those counts as final truth.
4. Direct CSV import into `product_master` failed with SQLSTATE 23502 because `scan_code` was null; failing row looked like `(null, 00001, DISCONTINUED 4065)`. This shows the original two-column file was not transformed into the required three-column schema.
5. The user explained stacked rows and FCFS logic. The assistant initially misunderstood; current understanding is the user's logic above.
6. User ran schema-inspection SQL in their own Supabase SQL Editor. Screenshot showed `product_master` columns `scan_code`, `sku_code`, `sku_name`, all TEXT and NOT NULL.
7. Assistant gave staging SQL first without identity row id, then a revised version with `row_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`, `source_code TEXT`, `source_name TEXT`.
8. User said “import done broo” after the revised staging instructions. Staging data is not yet verified.
9. User explicitly corrected the assistant: “supabase was never connected bro”. The assistant's Supabase connector must not be treated as access to the user's project. All SQL execution and live inspection are by the user in their own dashboard; assistant can prepare SQL and read screenshots/results.

## Current next actions

1. Continue from the user-reported completed staging CSV import.
2. Ask user to run the diagnostic SQL in `docs/SQL_REFERENCE.md` and paste results/screenshots, or give one short SQL query that reports schema, counts and samples.
3. Confirm staging table columns and row counts.
4. Inspect first rows and rows around the approximate SKU/EAN boundary.
5. Confirm row order is retained by `row_id`.
6. Build a preview query for mapping, using verified boundary and FCFS.
7. Show counts of mapped SKU rows, mapped EAN rows, blank rows, unmatched EAN names, and duplicate/conflicting scan codes.
8. Only after preview review, provide final INSERT/UPSERT SQL.
9. Then resume Android implementation. Check actual source tree before stating build status; README currently says repository is not yet a buildable Android app.

## Working style

User prefers a casual, direct “bro” tone and step-by-step instructions. Do not ask them to repeat context already documented. When user says “push .md files”, they mean commit the Markdown files directly to `rajuuddanti/Expiry` on GitHub.

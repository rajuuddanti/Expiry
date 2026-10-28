# Product Master Data Mapping and Import

Last updated: 2026-10-28 (Asia/Kolkata)

## Read this before using older import instructions

The earlier document described `product_master` and `product_master_staging`. The latest reported backend direction uses `public.MASTER` and the existing schema must not be changed. Do not run old staging/publish SQL against the current project without explicitly revalidating the user's live schema and asking before any destructive action.

## Latest reported master

The user reported uploading a fresh master containing 124,570 rows, with columns:
- `"SkuCode"`
- `"SkuLongName"`
- `"Catg"`

The source has no separate EAN column; SKU and barcode/EAN code values are stacked in the source code column. Category is now included in the master. Do not modify the `MASTER` table schema.

The row count and previously reported blank-code count are historical user-reported values, not a current live check. Earlier figures included 12,385 blank codes and 0 blank names; re-check with a user-run read-only query if needed.

## Normalization

- Keep codes as TEXT/string values.
- For digit-only codes shorter than 5 characters, add leading zeroes until length is 5.
- Codes of 5 or more characters remain unchanged.
- Do not truncate long barcodes or cast codes to numeric types.
- Trim surrounding whitespace where safe; do not change internal characters.

Examples: `1` → `00001`; `1234` → `01234`; `12345` stays `12345`; `8901023020353` stays unchanged.

## FCFS canonical SKU mapping

The user's rule is first-come-first-served in original source order:
- If the scanned code is itself a SKU, the displayed canonical SKU can be the same code.
- If the scanned code is an EAN/barcode, resolve its product name and map to the first SKU row with that exact product name in original source order.
- Preserve the raw scanned code separately from the canonical SKU.
- Duplicate product names use the first matching SKU; do not invent a more complex matching rule without a concrete failure.

The earlier source file was discussed as `SkuCode/EAN` and `SkuLongName`. The exact SKU/EAN row boundary must be verified from the actual data/order rather than guessed from code length. Keep original order meaningful.

## Important example from conversation

The user corrected that the relevant screenshot was Cinthol, not Freedom. For a Cinthol EAN such as `8901023020353` and name `CINTHOL LIME B3G1 100GM`, the expected SKU lookup should be independently verified against the current master. Do not repeat a SKU as verified unless the source data actually confirms it.

## Safe workflow

1. Do not change the `MASTER` schema or Supabase config.
2. If validating data, use read-only SELECT queries in the user's Supabase SQL Editor and ask the user to share results; do not claim direct access to their project.
3. Verify source row order and code/name values before changing lookup logic.
4. Test a known direct SKU scan and a known barcode scan against the same product.
5. Test padding for codes shorter than 5 digits and ensure longer codes remain unchanged.
6. Never clear the local cache or expiry records before a replacement master is fully validated.

See `docs/CURRENT_HANDOFF.md` for the current app/backend direction and `docs/EXPIRY_RETURN_RULES_AND_SCHEDULE.md` for the return-rule model.
# Retail Floor Precision UI implementation — 2026-11-21

## Branch
`feature/stitch-retail-floor-precision`

Created from `feature/no-expiry-fix`. Do not merge to main without approval.

## Implemented in this checkpoint
- Added `RetailFloorTheme.kt` with the Retail Floor Precision light colour scheme, typography scale, and Material 3 shape system.
- Applied `MahaMartRetailTheme` around the existing app content.
- Removed the nested default `MaterialTheme` wrapper so the new colour, typography, and shape tokens actually reach the screens.
- Updated the primary ruby red to `#C62828` and the main canvas to `#FAF8FF`.
- Styled the four-tab NavigationBar with white dock, ruby selected state, soft-red indicator, and slate inactive state.
- Refined Scan screen spacing, current-store card, scan trigger sizing, date action, and input corner radii.
- Refined SKU lookup and History cards with white surfaces, 16dp corners, and subtle borders.

## Explicitly preserved
No intentional changes were made to Room entities/DAO/schema, Supabase URL or keys, barcode lookup/scanning flow, expiry validation, return rules, NO EXPIRY restrictions, category schedule, or 3 PM business-day calculation.

## Not yet complete
- This is a first visual integration pass, not a full pixel-match implementation of all four Stitch screens.
- SKU Lookup, Entry History, and Settings still need their screen-specific layout refinements against the exported Stitch references.
- Inter font asset has not yet been bundled; typography currently uses Compose's default sans-serif font family with the specified size/weight scale.
- No Android Gradle build, APK install, or Zebra TC22 device test has been run from this branch. GitHub Actions did not return a PR workflow run for the latest checkpoint.

## Next steps
1. Finish the Scan screen details and confirm all required actions remain visible on rugged handheld displays.
2. Refine SKU Lookup, Entry History, and Settings layouts without changing their existing data or business logic.
3. Run `.\gradlew --stop`, then `.\gradlew :app:assembleDebug` locally after closing any process locking `app/build`.
4. Record build result and test on the Zebra TC22 before calling the UI work complete.

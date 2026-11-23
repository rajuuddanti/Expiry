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
- Refined SKU Lookup with a dedicated screen heading, today's scheduled-category chips, and a clearer search field.
- Refined Entry History with current-business-day context, entry and quantity summary cards, a prominent CSV export action, and a quieter secondary Supabase upload action.
- Refined Settings with a scrollable layout, clearer section heading, consistent white bordered sync card, and a compact current-store summary card.
- Refined Scan category presentation into red-tinted capsules and changed the displayed expiry date to DD-MM-YYYY while retaining ISO date storage and parsing.

## Explicitly preserved
No intentional changes were made to Room entities/DAO/schema, Supabase URL or keys, barcode lookup/scanning flow, expiry validation, return rules, NO EXPIRY restrictions, category schedule, or 3 PM business-day calculation.

## Not yet complete
- This is a first visual integration pass, not a full pixel-match implementation of all four Stitch screens.
- The four screens have received a first screen-specific styling pass. Exact pixel matching against the original Stitch export remains pending because the original export assets are not currently available in the repository context.
- Inter font asset has not yet been bundled; typography currently uses Compose's default sans-serif font family with the specified size/weight scale.
- No Android Gradle build, APK install, or Zebra TC22 device test has been run from this branch. GitHub Actions did not return a PR workflow run for the latest checkpoint.

## Next steps
1. Review all four screens on a handset-sized emulator and confirm controls remain visible on rugged handheld displays.
2. Recover the original Stitch export assets if exact pixel matching is required.
3. Run `.\gradlew --stop`, then `.\gradlew :app:assembleDebug` locally after closing any process locking `app/build`.
4. Record build result and test on the Zebra TC22 before calling the UI work complete.

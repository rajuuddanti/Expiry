# Main screen spacing refinement

Branch: `feature/main-screen-redesign`

Base: `feature/no-expiry-fix` at `c881a6a143021f0d685f50a53711b4657a8c7045`.

## Scope

This branch keeps the original Scan screen and form layout. It makes small vertical-space improvements without introducing a new dashboard:
- Reduces top header vertical padding.
- Places the scheduled category name and Return Rules action on one row.
- Reduces spacing between form controls.
- Makes the current-store card more compact.

## Preserved

The original SKU/barcode field, camera scanner, product name, category, quantity, expiry-date picker, Save Entry button, and bottom navigation remain. No changes are made to Room entities/DAO/database, Supabase configuration or schema, sync workers, inspection schedule, return rules, scan lookup, save validation, NO EXPIRY business rules, history actions, or settings workflows.

## Verification status

The earlier dashboard-style main-screen experiment was replaced on this branch with the original form layout plus compact spacing. The original `feature/no-expiry-fix` branch remains unchanged. Build and visually test this branch before deciding whether to keep it.

# Main screen redesign

Branch: `feature/main-screen-redesign`

Base: `feature/no-expiry-fix` at `c881a6a143021f0d685f50a53711b4657a8c7045`.

## Scope

This branch contains a visual-only refresh of the Expiry Manager Scan/main screen:
- Compact MAHAMART header.
- Today's inspection card with business date and scheduled categories.
- Per-category status labels derived from active local entries.
- Soft-deleted entries are excluded from the displayed counts and NO EXPIRY status.
- Compact current-store card with the 3 PM business-day reminder.

## Preserved

No changes were made to Room entities/DAO/database, Supabase configuration or schema, sync workers, inspection schedule, return rules, scan lookup, save validation, NO EXPIRY business rules, history actions, or settings workflows.

The existing scan/manual-entry controls remain in place; this change is a layout and presentation refresh only.

## Verification status

Source changes are committed to this feature branch. An Android Gradle build and on-device visual test have not yet been run. Test this branch on a device before deciding whether to keep it. The original `feature/no-expiry-fix` branch remains unchanged and is the fallback.

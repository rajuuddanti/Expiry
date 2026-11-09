# Read-only Web Schedule Handoff

## Artifact
- File: `MahaMart_Expiry_Schedule_ReadOnly.html`
- Location: repository root
- Purpose: standalone, read-only HTML page for the MahaMart Expiry inspection calendar.

## Display
- Shows exactly three business dates: Previous Day, Today, and Next Day.
- Uses the Asia/Kolkata business-day boundary: before 3:00 PM, the business date is the previous calendar date; at/after 3:00 PM, it is the current calendar date.
- Loads `app/src/main/assets/inspection_schedule.json` from this branch at page load with cache disabled.
- Mirrors the schedule's post-calendar rollover behavior, with no more than two categories per day.

## Scope and exclusions
- Read-only reference page only.
- No barcode scanning, data entry, local persistence, uploads, Supabase writes, or Android app changes.
- The page requires an internet connection to fetch the current schedule JSON.
- Refresh/reopen the page to retrieve the latest calendar.

## Source
- Schedule JSON: `app/src/main/assets/inspection_schedule.json`
- Branch: `feature/main-screen-redesign`

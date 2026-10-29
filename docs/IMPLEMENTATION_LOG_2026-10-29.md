# Implementation Log — 2026-10-29

## Repository and branch
- Repository: `rajuuddanti/Expiry`
- Working branch: `feature/new-expiry-upload-table`
- Android package: `com.mahalaxmi.expiry`

## Main screen: today's scheduled categories
- The main-screen heading displays the actual category names scheduled for the current day.
- Do not add a separate literal heading such as “Present Day Categories”; the category names themselves are the heading.
- If multiple categories are scheduled, display them separated by a bullet (` • `).
- Use a compact font and line height so longer category names fit reasonably.
- If no categories are scheduled, show “No categories scheduled today”.
- Keep the “ⓘ Return Rules” control on a separate row below the category heading, aligned to the right.

## Session history
- Entry history is filtered to the current 3 PM-anchored session.
- The daily session boundary is 3:00 PM local device time.
- Clearing the on-device history must not delete records already stored in Supabase.

## Product master and upload reminders
- Product master data is stored in Supabase `public."MASTER"` and cached locally in Room.
- Preserve the existing Supabase URL and publishable-key configuration; do not change configuration as part of routine UI work.
- Expiry entry uploads use `public.expiry_entry_uploads`; uploaded records include store/location, SKU and scanned code, quantity, expiry date, category, entry date/time, deletion state, and session metadata.
- Treat successful end-to-end upload testing as a separate verification step; a successful GitHub build alone does not prove that a device upload completed.

## Latest UI commit
- Commit: `6413c3737b763483b64a3630a5b664c15fef1342`
- Message: `Use today's scheduled categories as main screen heading`

## Follow-up UI refinement — categories stacked vertically

- Updated the Scan screen's scheduled-category heading to put each scheduled category on its own line instead of joining categories with a bullet.
- This makes two scheduled categories easier to read on smaller screens.
- The actual category names still come from today's schedule; no categories are hardcoded.
- No business logic or Supabase configuration was changed.
- Code commit: `9dee3136d61a3af31e9f74dd19be510d0d5d2136`.
- Build/device verification: not yet confirmed.

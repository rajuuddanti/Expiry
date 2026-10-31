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

## Modern Scan screen visual refresh

- Changed the Scan screen header to a clean white header with small red MAHALAXMI MAHAMART branding and a larger dark “Expiry Manager” title.
- Added a subtle divider beneath the header.
- Kept today's actual scheduled categories on separate lines.
- Refined the current-store card with a soft lavender surface, stronger store name typography, and consistent spacing.
- Rounded form field corners, softened inactive borders, and used Mahalaxmi red for focused fields.
- Refined the expiry-date button and Save Entry button shapes.
- Code commit: `b5b8e3040410fce4431981c5c9d781e9a3895e1a`.
- Build/device verification: pending; check GitHub Actions before considering this verified.

## End-of-day handoff — Scan screen visual refresh

- Latest app UI commit: `b5b8e3040410fce4431981c5c9d781e9a3895e1a`.
- Updated the Scan screen header to a white surface, small red MAHALAXMI MAHAMART branding, and a prominent dark “Expiry Manager” title.
- Current-store card uses a soft lavender surface; form fields use rounded corners and subtle grey borders with red focus accents; expiry-date and Save Entry controls have refreshed shapes.
- Today's scheduled categories remain dynamic and display one category per line.
- Related code commits today: `9dee3136d61a3af31e9f74dd19be510d0d5d2136` (vertical categories) and `b5b8e3040410fce4431981c5c9d781e9a3895e1a` (visual refresh).
- GitHub Actions run for the visual refresh was still in progress at last check: https://github.com/rajuuddanti/Expiry/actions/runs/40854750424 . Recheck before claiming the build passed.
- No device/emulator visual verification has been recorded yet.
- End-of-day state: pause further changes; next session should first inspect current branch HEAD and Actions status, then verify the Scan screen UI.

## 2026-10-29 — quantity reset and automatic expiry upload

- After a successful save, the quantity field is cleared instead of being reset to `1`; this avoids accidentally carrying the previous quantity into the next entry.
- Expiry entries now attempt an immediate upload to `public.expiry_entry_uploads` after being saved locally.
- If automatic upload fails (for example, no connection), the entry remains stored locally and a connected-network one-time retry is scheduled. The background sync worker also attempts to upload pending entries when it runs.
- The History screen's manual upload action remains available as a fallback.
- Code commits: `6c6194f0e540b350ba4bb39a0740110d99f47a33`, `c862f3ed96d8a642764ae434093c0422e618bf85`, and `bee929f29bdbcc23250302f540728ba871648b16`.
- Build and live-device/Supabase verification are pending; inspect GitHub Actions before claiming success.

## CSV export filename — 2026-10-29

- CSV export filenames now follow `Store_Category-Date.csv`, for example `Dharmaram_NAMKEEN-CHIPS-2026-10-29.csv`.
- Filename parts are sanitized to use letters, numbers, and hyphens so store/category names do not create invalid file paths.
- If entries contain more than one distinct category, the category part becomes `Multiple-Categories`; if category is missing, it becomes `Uncategorised`.
- The CSV column format and exported entry data remain unchanged.
- Code commit: `8493393283200b5034f5b76007b778c5ad1b6b03`.
- Build/device verification pending.

## 2026-10-31 — use 3 PM business date for category schedule

- Fixed the Scan screen's scheduled categories to use the existing `currentSessionDate` instead of `LocalDate.now()`.
- Before 3:00 PM local time, the active business date is yesterday; from 3:00 PM onward, it is the current calendar date.
- This aligns the category heading and category validation with the same 3 PM session boundary used by entry history.
- The underlying inspection schedule/calendar was not modified.
- Code commit: `43ceba4271faa69fad0703f54b40907d43e77b4d`.
- Build and device verification pending; check GitHub Actions and test before/after 3 PM.

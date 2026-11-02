# Current Project Handoff — Mahalaxmi Expiry Manager

Last updated: 2026-11-02 (Asia/Kolkata)

## Start here

- Repository: `rajuuddanti/Expiry`
- Active working branch: `feature/no-expiry-fix`
- App: Mahalaxmi Expiry Manager
- Android package: `com.mahalaxmi.expiry`
- Last app-source commit before this documentation update: `b7c22c15d9e8ff758c181f95d3851de5ac92f372` — **Prevent NO EXPIRY when category has active expiry entries**.
- Do not switch to `main` or merge branches unless the user explicitly asks. The working branch is ahead of `feature/new-expiry-upload-table`.

Read these first:
1. `README.md`
2. `docs/CURRENT_HANDOFF.md`
3. `docs/NEXT_CHAT_HANDOFF.md`
4. `docs/IMPLEMENTATION_STATUS.md`
5. `docs/PROJECT_CONTEXT.md`
6. `docs/DECISIONS_AND_CHANGELOG.md`
7. `docs/NO_EXPIRY_FEATURE.md`
8. `docs/NO_EXPIRY_CATEGORY_SELECTION.md`
9. `docs/GOOGLE_SHEETS_EXPORT_SETUP.md`

## Non-negotiable working rules

- Inspect the active branch, HEAD, and relevant source before changing code.
- Do not switch branches, merge, rebase, or alter branch history unless asked.
- Do not change Supabase URL, publishable/anon key, `local.properties`, or existing Supabase configuration unless explicitly requested.
- Never place a Supabase service-role key in Android, GitHub, or chat. The export service-role key belongs only in the private Apps Script project's Script Properties.
- A source commit is not proof of a successful Android build, installation, or device test. Report only evidence actually obtained.
- Do not claim an Apps Script deployment from a GitHub commit. The standalone Apps Script editor is a separate deployment.
- Keep changes scoped to the user's request. Do not make unrelated UI, schema, or configuration changes.

## App purpose and core behavior

The Android app is used by store staff to record product expiry information.

- Scan a barcode/EAN/SKU or enter a code manually.
- Resolve the product name from the locally cached product master.
- Record quantity and expiry information.
- Save locally in Room so the app can be used offline; pending records sync when connectivity is available according to the implemented upload flow.
- Store/location is selected manually and can be changed in Settings behind PIN `3331`.
- Business-day history is anchored at **3:00 PM Asia/Kolkata** and runs until the next day's 3:00 PM.
- Corrections/deletions in history use soft-delete behavior; do not physically delete history records.
- Preserve the existing red-and-white Mahalaxmi styling and large, readable store controls unless the user asks for a UI change.

## Product master and lookup rules

The connected Expiry Supabase project was inspected on 2026-11-02. Its `public.MASTER` table had columns `SkuCode`, `SkuLongName`, and `Catg`; the observed count at that check was 125,120 rows. Counts can change and must not be treated as permanent.

Master/lookup rules:
- Source data contains SKU and EAN/barcode codes stacked in one code column; do not invent a separate source EAN column.
- Keep all codes as text. For digit-only codes shorter than five characters, left-pad to five; do not change codes already five or more characters and do not truncate long barcodes.
- Preserve source order. When resolving duplicate product names from EAN rows to a canonical SKU, use first match (FCFS).
- Preserve the raw scanned code separately from the canonical SKU.
- Daily master refresh is intended to check for changes once per day, with a local cache for offline use. Inspect current source before changing refresh behavior.

## Schedule and return rules

- The inspection schedule is in `app/src/main/assets/inspection_schedule.json`; the supplied category calendar covers 2026-10-23 through 2027-12-31.
- Never schedule more than two categories per business day.
- Continue the rotation beyond 2027 from the final schedule state; do not restart the rotation or invent dates.
- The user-defined brand return rules are Himalaya = 60 days and Kellogg's = 30 days, overriding category rules.
- Category return rules are stored in `public.expiry_return_rules` (32 rows were observed in the live project on 2026-11-02).
- The proposed category effective-window formula is `max(configured_return_days, schedule_interval_days)`. Preserve configured values in Supabase; calculate effective values in the app. Do not implement or change brand-floor behavior without confirming the user's decision.
- Inspect `docs/EXPIRY_RETURN_RULES_AND_SCHEDULE.md` before schedule/rule changes.

## NO EXPIRY behavior — current source-level implementation

Current implementation is on `feature/no-expiry-fix`.

### Category selection
- One category scheduled: it is selected automatically for NO EXPIRY and staff confirm.
- Two categories scheduled: staff select which category is NO EXPIRY, then confirm.
- A category already marked NO EXPIRY cannot be marked again while that mark is active.
- NO EXPIRY marks are associated with the selected store and the current 3 PM–3 PM business day.
- A soft-deleted NO EXPIRY mark no longer blocks the category and permits it to be entered/marked again.

### Prevent conflicting entries
Before saving a NO EXPIRY mark for a category, the app checks the current business-day history for the selected store/category:
- If an active ordinary expiry entry exists in that category, refuse the NO EXPIRY mark and tell staff to delete the entries from History first.
- Soft-deleted ordinary entries do not count.
- Categories are checked independently. An active entry in one category does not prevent NO EXPIRY for the other category on a two-category day.

### Block new entries after NO EXPIRY
- Exactly one category scheduled: once that category is marked NO EXPIRY, block all new expiry entries for the remainder of that business day, including unscheduled categories.
- Exactly two categories scheduled: marking one category blocks new entries only for that category. The other scheduled category and unscheduled categories remain available.
- If both scheduled categories are marked NO EXPIRY, block all new expiry entries for that business day.
- Validation is at the final save point for both scanner-filled and manually entered codes. Do not disable the whole Scan screen while an allowed category remains.
- Schedule data itself is unchanged.

These are source-level behaviors recorded in commits `1ee734e685c174077dbd60388068b51144a572b0` and `b7c22c15d9e8ff758c181f95d3851de5ac92f372`. Android build and device verification are still separate and must not be assumed.

## Supabase state and expiry uploads

The live project connected as **Expiry** was inspected on 2026-11-02. Tables observed:
- `public.MASTER`
- `public.expiry_return_rules`
- `public.expiry_entry_uploads`

The expiry upload table includes fields such as `id`, `location`, `skucode`, `skuname`, `ean_sku_scanned`, `qty`, `expiry_date`, `category`, `date`, `time`, `is_deleted`, `created_at`, `deleted_at`, `session_start`, `uploaded_at`, `expiry`, and `no_expiry_category`.

Do not change the live schema, policies, or project configuration unless requested. Never expose credentials. Use the connected Expiry project only when the user asks for a live check or a task that needs it.

## Google Sheets daily export — verified by user on 2026-11-02

The standalone Google Apps Script exports `public.expiry_entry_uploads` to Google Drive.

### Intended operation
- Time-driven trigger runs daily near **3:15 PM IST**; Apps Script timing is approximate.
- Export window is the completed previous business day: 3:00 PM IST inclusive to the following 3:00 PM IST exclusive, filtered by `session_start`.
- Drive structure: `MahaMart Expiry Reports / YYYY / Month / D MMM <scheduled categories>`.
- Folder category names come from `inspection_schedule.json` for the exported business date.
- One Google Sheet is created per category actually present in the source rows; all stores are combined and `location` is retained.
- Unexpected categories are exported to their own sheet.
- All source columns are retained in category sheets, including `is_deleted`.
- Active NO EXPIRY confirmations are excluded from category sheets and written to a separate `no expiry.csv`; deleted NO EXPIRY marks are excluded.
- Pagination is 1,000 Supabase rows; Sheets writes are batched.
- Reruns reuse the dated folder and category sheet names, replace sheet contents, and update `_export_log`.
- Existing sheet filters are removed before a new filter is created.

### 2026-11-02 successful run
The user confirmed after the 3 PM export that **everything works as intended**. The live Apps Script editor contains the corrected CSV separator:
`lines.join('\\r\\n')` in source must be changed to the JavaScript string literal `lines.join('\\r\\n')`? No: use exactly `lines.join('\\r\\n')` only if the source displays two backslashes. The correct source line is:
```javascript
folder.createFile('no expiry.csv',lines.join('\r\n'),MimeType.CSV);
```
In the actual JavaScript file this must contain one backslash before each r/n escape sequence, so the generated CSV has real CRLF line breaks.

### Important GitHub/live-script mismatch
At the time of this handoff, the GitHub copy `scripts/google-apps-script/Code.gs` still contains the old source expression `lines.join('\\\\r\\\\n')` (two backslashes in the JS source). The user corrected the complete file in the standalone Apps Script editor and confirmed the live export works. **Do not overwrite the working live Apps Script project with the stale GitHub copy.** The repository script has not been changed in this documentation-only update. If the user later asks to sync it, update the GitHub source to match the working editor version and test separately.

Secrets remain in the standalone Apps Script project's Script Properties:
- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `ROOT_FOLDER_ID`
- Optional `SCHEDULE_JSON_URL`

Never ask the user to paste the service-role key into chat.

## Current verification boundary

Verified:
- The active GitHub branch and latest app-source commit were inspected.
- Live Supabase project `Expiry` was accessible for read-only checks on 2026-11-02.
- The user confirmed the live Google Sheets export works after correcting the Apps Script file.

Not verified in this handoff:
- A fresh Android Gradle build, install, or end-to-end device test of the latest NO EXPIRY source.
- A fresh CI result for the latest app-source commit.
- GitHub's Apps Script copy is not yet synchronized with the user's corrected live editor copy.

## Next work

1. Start by checking the branch HEAD and current working tree/source; do not assume this handoff is newer than GitHub.
2. If the user asks for app work, inspect relevant Kotlin files and current CI/build evidence before changing code.
3. Keep the current working branch; do not merge or switch to main.
4. If asked to sync Apps Script, first compare the live working editor copy supplied by the user with the repository copy, then update only the intended script and setup documentation.
5. Keep this handoff, `NEXT_CHAT_HANDOFF.md`, `IMPLEMENTATION_STATUS.md`, and dated implementation logs consistent after meaningful work.

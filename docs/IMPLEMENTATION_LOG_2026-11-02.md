# Implementation Log — 2026-11-02

Project: Mahalaxmi Expiry Manager  
Repository: `rajuuddanti/Expiry`  
Working branch: `feature/no-expiry-fix`

## Android source — NO EXPIRY safeguards

Latest app-source commit before this documentation update:

- `b7c22c15d9e8ff758c181f95d3851de5ac92f372`
- Message: **Prevent NO EXPIRY when category has active expiry entries**

The current branch includes the following source-level behavior:

1. Before inserting a NO EXPIRY mark, check the current store/category/business-day session for active ordinary expiry entries.
2. If an active ordinary entry exists, refuse the NO EXPIRY mark and direct staff to remove the entry from History first.
3. Ignore soft-deleted ordinary entries when checking the conflict.
4. Keep the check category-specific, so an entry in one category does not prevent marking the other category NO EXPIRY.
5. Keep active NO EXPIRY duplicate checks; soft-deleted marks do not count as active.
6. For one scheduled category, marking it NO EXPIRY blocks all new expiry entries for that 3 PM–3 PM business day, including unscheduled categories.
7. For two scheduled categories, marking one blocks only that category; the other scheduled category and unscheduled categories remain available. If both scheduled categories are marked, all new entries are blocked.
8. Enforce the entry restriction at the final save point for both scan and manual entry paths.

No schedule data was changed. These are source-level notes; no fresh Android build, CI result, or device test is recorded here.

## Google Sheets export — user confirmation

The user confirmed on 2026-11-02, after the scheduled afternoon export, that **everything works as intended**.

The working standalone Apps Script project has:
- Supabase source `public.expiry_entry_uploads`.
- A daily trigger near 3:15 PM Asia/Kolkata.
- A previous-business-day window anchored at 3 PM IST.
- Category spreadsheets generated from categories present in the source data, with all stores combined.
- Active NO EXPIRY confirmations exported separately to `no expiry.csv`; deleted confirmations are excluded.
- An export log and rerun behavior that refreshes the same dated output.

### CSV line separator correction

The complete Apps Script file was corrected in the user's live editor so the CSV uses actual CRLF line breaks. The correct JavaScript source line is:

```javascript
folder.createFile('no expiry.csv',lines.join('\r\n'),MimeType.CSV);
```

The JavaScript source must contain one backslash before `r` and one before `n`; do not use a double-escaped literal that writes the characters `\r\n` into the CSV.

**Repository warning:** at the time of this log, `scripts/google-apps-script/Code.gs` in GitHub still contains the older double-escaped line separator. The live Apps Script editor is working and was confirmed by the user. This documentation-only update does not modify the repository script or deploy anything to Apps Script. Do not replace the working live script with the stale GitHub copy.

## Documentation update

Updated the current handoff, next-chat handoff, and implementation status to make the current decisions and verification boundaries consistent. Added this dated log.

## Next verification / maintenance

- Do not change the working export unless requested.
- If the user asks to synchronize the repository Apps Script copy, update it from the exact working live version and commit that separately.
- If Android code is changed, build and test the active branch; do not infer success from a commit.
- Preserve Supabase configuration and keep the service-role key out of Android, GitHub, and chat.

# Implementation plan

## Product master data and daily sync
- [x] Two production business tables intended: product_master and expiry_entries
- [x] No category field/table; one scan_code lookup handles SKU and EAN
- [x] Text-only codes; pad digit-only codes shorter than 5 digits, preserve 5+ digits
- [x] FCFS canonical SKU match for duplicate product names
- [x] User selected daily staging -> validate -> compare -> publish (Option A)
- [ ] Verify actual live schemas and RLS from user-run SQL results
- [ ] Build staging validation for blanks, duplicate/conflicting scan codes and mapping errors
- [ ] Build safe publish process that compares staging to existing master without clearing the published table first
- [ ] Track a monotonically increasing master revision and retained changes, including removals
- [ ] Add lightweight revision check and paginated delta retrieval for devices
- [ ] Add full-snapshot fallback if a device's revision is too old for retained changes
- [ ] Test add, update, removal, duplicate, and failed-validation cases

## Android product master sync
- [ ] Inspect actual Android source tree and build setup before implementation claims
- [ ] First successful online launch downloads full master into Room
- [ ] Persist locally applied master revision
- [ ] Subsequent launches check revision only; if unchanged, fetch no master rows
- [ ] If changed, download only changes since local revision
- [ ] Apply each delta in a single Room transaction and advance local revision only after success
- [ ] Offline start uses Room and retries later
- [ ] Never clear local master before successful full/delta validation
- [ ] Product master refresh never removes expiry history or unsynced expiry entries
- [ ] Test interrupted download, retry, pagination, removals, offline mode, and unchanged revision

## Expiry app requirements
- [ ] Store selection saved locally and editable in Settings
- [ ] Camera barcode scanning and manual code entry
- [ ] Exact lookup against product_master.scan_code
- [ ] Autofill canonical SKU and product name
- [ ] Quantity and calendar expiry picker
- [ ] History session 3 PM IST to next day 3 PM
- [ ] Room offline persistence and retryable expiry-entry sync
- [ ] Soft delete manager corrections/deletions; never physically delete expiry records
- [ ] Build and test on device before claiming readiness

## Security
- [ ] No service-role key in APK
- [ ] Confirm safe RLS and controlled access path for master revision/delta reads and expiry writes
- [ ] Do not assume assistant has direct access to user's Supabase project

See docs/PRODUCT_MASTER_DAILY_SYNC.md for the detailed sync design.
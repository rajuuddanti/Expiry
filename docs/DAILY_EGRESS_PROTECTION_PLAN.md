# Daily Egress Protection Plan

**Status:** Proposed only — not implemented  
**Date:** 2026-11-14  
**Target:** Keep Supabase egress below 100 MB per day without sacrificing expiry-entry uploads.

## Current state

The user asked whether daily egress can be restricted to 100 MB. No Supabase setting, app code, database schema, edge function, or project configuration was changed. The user explicitly requested no changes for now.

Do not describe this target as an enforced Supabase hard cap. A daily app-side budget is not equivalent to a platform-wide bandwidth cap.

## Goals

1. Reduce unnecessary product MASTER downloads.
2. Keep offline product lookup available through local MASTER CSV import.
3. Prioritize uploading locally saved expiry entries.
4. Preserve every unsent expiry entry in Room DB until the server confirms acceptance.
5. Avoid changing the current working setup until the user approves implementation.

## Proposed application-side controls

| Estimated daily usage | Proposed action |
|---|---|
| 70 MB | Show a usage warning |
| 85 MB | Stop optional/non-essential downloads |
| 95 MB | Block product MASTER refresh |
| 100 MB | Enter protection mode; continue only essential operations where possible |

Thresholds are provisional and must be reviewed before implementation.

### Traffic priority

- Highest priority: pending expiry-entry uploads.
- Local-only: scan, save, view history, and import MASTER CSV.
- Low priority: product MASTER download/refresh and other optional reads.

Do not discard pending rows or mark them uploaded before a successful server acknowledgement.

## Limitations

- Per-device counters do not enforce a shared 100 MB/day budget across all devices.
- Client-estimated transfer sizes may differ from Supabase's billed egress.
- Uploads still require the Supabase project and Data API to be operational. If the project is paused/restricted or the API is unavailable, entries remain queued locally until service returns.
- A local MASTER import protects product lookup; it does not provide remote upload capability.

## Possible implementation phases

1. **Observe only:** collect daily request/byte estimates and show a dashboard; do not block traffic.
2. **Device-level protection:** warn and throttle optional downloads on each device.
3. **Global coordination (optional):** introduce a centralized budget coordinator and server-side accounting if a true fleet-wide limit is required.

## Approval gate

No implementation or Supabase changes until the user explicitly approves the design and rollout.

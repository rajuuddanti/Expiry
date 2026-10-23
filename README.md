# Mahalaxmi Expiry Manager

Android app for store-wise expiry-date capture across Mahalaxmi Mahamart stores.

## Core workflow

- Manager selects a store name; no device registration is required.
- Scan a product barcode with the phone camera.
- Look up EAN/SKU in the Supabase product master and populate SKU code, SKU name, EAN, and category.
- Enter quantity and expiry date using a calendar picker.
- Save locally first; sync to Supabase when a connection is available.
- Managers can review and soft-delete mistakes. Deleting sets `is_deleted = true`; it never physically deletes a row.
- The on-device visible history is scoped to a daily session from 3:00 PM IST through, but not including, the next day's 3:00 PM.
- Office-side records remain independent of local session history. Server clearing/export must not clear the device's local data or reset its session.

## Daily session rule

Use Asia/Kolkata local time. For a timestamp before 15:00, its session starts at 15:00 on the previous calendar day. At or after 15:00, its session starts at 15:00 on the current day. The session is calculated from the clock, not app-launch time.

## Initial data model

See `supabase/migrations/001_initial_schema.sql`.

## Security note

Do not ship a Supabase service-role key in the Android app. The initial schema is a data-model starting point; configure Row Level Security and a safe write path before production use. Store-name selection alone is not authentication and cannot prove a manager belongs to a particular store.

## Proposed app stack

- Kotlin / Android
- CameraX with ML Kit Barcode Scanning
- Room for offline-first local data and pending-sync queue
- Supabase Postgres for product master and central expiry records
- Material components with Mahalaxmi's red header / light background UI inspired by the supplied Stock Audit screenshots

## Build status

This repository was initially empty. The first commit establishes requirements and schema only; it is not yet a buildable Android application.

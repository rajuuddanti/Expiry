-- Run once in Supabase SQL Editor before installing the updated Android build.
ALTER TABLE public.expiry_entry_uploads
  ADD COLUMN IF NOT EXISTS no_expiry_category text NOT NULL DEFAULT '';
COMMENT ON COLUMN public.expiry_entry_uploads.no_expiry_category IS
  'Category selected by the store when confirming NO EXPIRY FOUND; blank for normal expiry entries.';

-- Run once in Supabase SQL Editor before installing the updated Android build.
ALTER TABLE public.expiry_entry_uploads
  ADD COLUMN IF NOT EXISTS no_expiry_category text NOT NULL DEFAULT '';

COMMENT ON COLUMN public.expiry_entry_uploads.no_expiry_category IS
  'Category selected by the store when confirming NO EXPIRY FOUND; blank for normal expiry entries.';

-- Keep standard expiry uploads valid, and allow the NO EXPIRY row to keep all
-- product/category fields blank as requested. Case-insensitive to tolerate the
-- existing lowercase 'yes' default on expiry.
DROP POLICY IF EXISTS "expiry upload insert" ON public.expiry_entry_uploads;
CREATE POLICY "expiry upload insert" ON public.expiry_entry_uploads
FOR INSERT TO anon, authenticated
WITH CHECK (
  id IS NOT NULL AND location <> '' AND (
    (
      upper(trim(expiry)) = 'NO EXPIRY'
      AND skucode = '' AND skuname = '' AND ean_sku_scanned = ''
      AND qty = 0 AND expiry_date IS NULL AND category = ''
    )
    OR
    (
      upper(trim(expiry)) <> 'NO EXPIRY'
      AND skucode <> '' AND skuname <> ''
      AND qty > 0 AND expiry_date IS NOT NULL
    )
  )
);

DROP POLICY IF EXISTS "expiry upload retry update" ON public.expiry_entry_uploads;
CREATE POLICY "expiry upload retry update" ON public.expiry_entry_uploads
FOR UPDATE TO anon, authenticated
USING (true)
WITH CHECK (
  id IS NOT NULL AND location <> '' AND (
    (
      upper(trim(expiry)) = 'NO EXPIRY'
      AND skucode = '' AND skuname = '' AND ean_sku_scanned = ''
      AND qty = 0 AND expiry_date IS NULL AND category = ''
    )
    OR
    (
      upper(trim(expiry)) <> 'NO EXPIRY'
      AND skucode <> '' AND skuname <> ''
      AND qty > 0 AND expiry_date IS NOT NULL
    )
  )
);

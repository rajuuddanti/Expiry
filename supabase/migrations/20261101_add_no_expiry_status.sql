-- Add a status discriminator for ordinary expiry entries vs. explicit no-expiry confirmations.
-- Run this migration in the same Supabase project used by the Android app.
ALTER TABLE public.expiry_entry_uploads
    ADD COLUMN IF NOT EXISTS expiry text NOT NULL DEFAULT 'YES';

-- NO EXPIRY confirmation rows have no product expiry date and quantity is 0.
ALTER TABLE public.expiry_entry_uploads
    ALTER COLUMN expiry_date DROP NOT NULL;

ALTER TABLE public.expiry_entry_uploads
    DROP CONSTRAINT IF EXISTS expiry_entry_uploads_qty_check;

DROP POLICY IF EXISTS "expiry upload insert" ON public.expiry_entry_uploads;
CREATE POLICY "expiry upload insert"
    ON public.expiry_entry_uploads
    FOR INSERT TO anon, authenticated
    WITH CHECK (
        (expiry = 'NO EXPIRY' AND location <> '')
        OR
        (expiry = 'YES' AND location <> '' AND skucode <> '' AND skuname <> ''
            AND qty > 0 AND expiry_date IS NOT NULL)
    );

DROP POLICY IF EXISTS "expiry upload retry update" ON public.expiry_entry_uploads;
CREATE POLICY "expiry upload retry update"
    ON public.expiry_entry_uploads
    FOR UPDATE TO anon, authenticated
    USING (true)
    WITH CHECK (
        (expiry = 'NO EXPIRY' AND location <> '')
        OR
        (expiry = 'YES' AND location <> '' AND skucode <> '' AND skuname <> ''
            AND qty > 0 AND expiry_date IS NOT NULL)
    );

-- Existing grants and the conflict-key SELECT policy remain unchanged.

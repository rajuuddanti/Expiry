-- Add category to the live imported master table used by the Expiry app.
-- Idempotent so this is safe on environments where Catg was added manually.
-- Does not alter or reload master data.
alter table if exists public."MASTER"
  add column if not exists "Catg" text;

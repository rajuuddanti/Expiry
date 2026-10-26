-- Android sync for the actual Expiry project schema.
-- product_master_staging is the authoritative SKU/EAN code-to-name map.
-- This migration is additive and preserves all existing master/expiry rows.

alter table public.product_master_staging enable row level security;
grant select on public.product_master_staging to authenticated;

drop policy if exists "Authenticated app can read product name map" on public.product_master_staging;
create policy "Authenticated app can read product name map"
  on public.product_master_staging
  for select to authenticated
  using (true);

alter table public.expiry_entries
  add column if not exists client_record_id uuid,
  add column if not exists owner_id uuid references auth.users(id) on delete set null,
  add column if not exists session_start timestamptz,
  add column if not exists created_at timestamptz not null default now(),
  add column if not exists device_created_at timestamptz,
  add column if not exists is_deleted boolean not null default false,
  add column if not exists deleted_at timestamptz;

create unique index if not exists expiry_entries_client_record_id_full_uidx
  on public.expiry_entries (client_record_id);
create index if not exists expiry_entries_owner_id_idx
  on public.expiry_entries (owner_id);

grant select, insert, update on public.expiry_entries to authenticated;

drop policy if exists "Users can read their own expiry entries" on public.expiry_entries;
create policy "Users can read their own expiry entries"
  on public.expiry_entries for select to authenticated
  using (owner_id = auth.uid());

drop policy if exists "Users can insert their own expiry entries" on public.expiry_entries;
create policy "Users can insert their own expiry entries"
  on public.expiry_entries for insert to authenticated
  with check (owner_id = auth.uid());

drop policy if exists "Users can update their own expiry entries" on public.expiry_entries;
create policy "Users can update their own expiry entries"
  on public.expiry_entries for update to authenticated
  using (owner_id = auth.uid())
  with check (owner_id = auth.uid());

-- Anonymous sign-in must be enabled in Supabase Auth.
-- Existing expiry rows without owner_id remain inaccessible to app users.
-- Keep product_master_staging restricted to authenticated reads; do not enable anon reads.

-- Separate upload/history table. Existing product_master_staging is unchanged.
create table if not exists public.expiry_scan_uploads (
  id uuid primary key default gen_random_uuid(),
  skuname text not null,
  skucode text not null,
  ean text not null,
  qty integer not null default 1 check (qty > 0),
  expirydate date not null,
  is_deleted boolean not null default false,
  store_name text,
  device_id text,
  owner_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists expiry_scan_uploads_owner_created_idx
  on public.expiry_scan_uploads (owner_id, created_at desc);
create index if not exists expiry_scan_uploads_ean_idx
  on public.expiry_scan_uploads (ean);

alter table public.expiry_scan_uploads enable row level security;

drop policy if exists "Users can read their own expiry scan uploads" on public.expiry_scan_uploads;
create policy "Users can read their own expiry scan uploads"
  on public.expiry_scan_uploads for select to authenticated
  using (owner_id = auth.uid());

drop policy if exists "Users can insert their own expiry scan uploads" on public.expiry_scan_uploads;
create policy "Users can insert their own expiry scan uploads"
  on public.expiry_scan_uploads for insert to authenticated
  with check (owner_id = auth.uid());

drop policy if exists "Users can update their own expiry scan uploads" on public.expiry_scan_uploads;
create policy "Users can update their own expiry scan uploads"
  on public.expiry_scan_uploads for update to authenticated
  using (owner_id = auth.uid())
  with check (owner_id = auth.uid());

-- Device history clearing must only clear local Room data; do not delete rows from this table.

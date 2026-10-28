-- Dedicated destination for explicitly uploaded expiry records.
-- Does not modify public.MASTER or expiry_return_rules.
create table if not exists public.expiry_entry_uploads (
    id text primary key,
    location text not null,
    skucode text not null,
    skuname text not null,
    ean_sku_scanned text not null,
    qty integer not null check (qty > 0),
    expiry_date date not null,
    category text not null default '',
    date date not null,
    time time not null,
    is_deleted boolean not null default false,
    created_at timestamptz not null,
    deleted_at timestamptz,
    session_start timestamptz not null,
    uploaded_at timestamptz not null default now()
);

create index if not exists expiry_entry_uploads_store_created_idx
    on public.expiry_entry_uploads (location, created_at desc);
create index if not exists expiry_entry_uploads_expirydate_idx
    on public.expiry_entry_uploads (expiry_date);

alter table public.expiry_entry_uploads enable row level security;

-- The Android app uses the existing publishable/anon key and no store login.
-- This policy permits upload/upsert only; it does not grant SELECT access.
drop policy if exists "expiry upload insert" on public.expiry_entry_uploads;
create policy "expiry upload insert"
    on public.expiry_entry_uploads
    for insert to anon, authenticated
    with check (
        id is not null and location <> '' and skucode <> ''
        and skuname <> '' and qty > 0
    );

drop policy if exists "expiry upload retry update" on public.expiry_entry_uploads;
create policy "expiry upload retry update"
    on public.expiry_entry_uploads
    for update to anon, authenticated
    using (true)
    with check (
        id is not null and location <> '' and skucode <> ''
        and skuname <> '' and qty > 0
    );

-- Upsert conflict handling needs access to the conflict key only. Do not grant SELECT on payload columns.
drop policy if exists "expiry upload conflict key lookup" on public.expiry_entry_uploads;
create policy "expiry upload conflict key lookup"
    on public.expiry_entry_uploads
    for select to anon, authenticated
    using (true);

grant insert, update on public.expiry_entry_uploads to anon, authenticated;
grant select (id) on public.expiry_entry_uploads to anon, authenticated;

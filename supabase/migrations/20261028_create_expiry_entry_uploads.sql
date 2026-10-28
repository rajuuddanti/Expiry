-- Dedicated destination for explicitly uploaded expiry records.
-- Does not modify public.MASTER or expiry_return_rules.
create table if not exists public.expiry_entry_uploads (
    id text primary key,
    store_name text not null,
    skucode text not null,
    skuname text not null,
    ean text not null,
    qty integer not null check (qty > 0),
    expirydate date not null,
    is_deleted boolean not null default false,
    created_at timestamptz not null,
    deleted_at timestamptz,
    session_start timestamptz not null,
    uploaded_at timestamptz not null default now()
);

create index if not exists expiry_entry_uploads_store_created_idx
    on public.expiry_entry_uploads (store_name, created_at desc);
create index if not exists expiry_entry_uploads_expirydate_idx
    on public.expiry_entry_uploads (expirydate);

alter table public.expiry_entry_uploads enable row level security;

-- The Android app uses the existing publishable/anon key and no store login.
-- This policy permits upload/upsert only; it does not grant SELECT access.
drop policy if exists "expiry upload insert" on public.expiry_entry_uploads;
create policy "expiry upload insert"
    on public.expiry_entry_uploads
    for insert to anon, authenticated
    with check (
        id is not null and store_name <> '' and skucode <> ''
        and skuname <> '' and qty > 0
    );

drop policy if exists "expiry upload retry update" on public.expiry_entry_uploads;
create policy "expiry upload retry update"
    on public.expiry_entry_uploads
    for update to anon, authenticated
    using (true)
    with check (
        id is not null and store_name <> '' and skucode <> ''
        and skuname <> '' and qty > 0
    );

grant insert, update on public.expiry_entry_uploads to anon, authenticated;

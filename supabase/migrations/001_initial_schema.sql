-- Mahalaxmi Expiry Manager: initial schema
-- Review and apply in Supabase SQL Editor.
-- This migration intentionally does not create open/public policies.

create extension if not exists pgcrypto;

-- Canonical product identity: one row per internal SKU.
create table if not exists public.product_master (
    sku_code text primary key,
    sku_name text not null,
    category text,
    is_active boolean not null default true,
    updated_at timestamptz not null default now()
);

-- Any code the manager may scan can resolve to a canonical SKU.
-- This includes the internal SKU code itself and one or more EAN/barcodes.
create table if not exists public.product_barcodes (
    barcode text primary key,
    sku_code text not null references public.product_master(sku_code) on update cascade on delete restrict,
    code_type text not null check (code_type in ('SKU', 'EAN', 'OTHER')),
    source_value text,
    updated_at timestamptz not null default now()
);

create index if not exists product_barcodes_sku_code_idx
    on public.product_barcodes (sku_code);
create index if not exists product_master_active_idx
    on public.product_master (is_active);

-- Store codes as text so leading zeros are preserved. Do not put service-role
-- credentials in the Android app. Product master import must flag ambiguous
-- barcode -> SKU mappings instead of silently picking one.

create table if not exists public.expiry_entries (
    id uuid primary key default gen_random_uuid(),
    client_record_id uuid not null unique,
    store_name text not null,
    sku_code text not null,
    sku_name text not null,
    ean text,
    category text,
    qty integer not null check (qty > 0),
    expiry_date date not null,
    session_start timestamptz not null,
    created_at timestamptz not null default now(),
    device_created_at timestamptz,
    uploaded_at timestamptz not null default now(),
    is_deleted boolean not null default false,
    deleted_at timestamptz,
    delete_reason text,
    exported_at timestamptz,
    constraint expiry_entries_session_start_3pm_ist check (
        (session_start at time zone 'Asia/Kolkata')::time = time '15:00:00'
    )
);

create index if not exists expiry_entries_store_session_idx
    on public.expiry_entries (store_name, session_start);
create index if not exists expiry_entries_expiry_date_idx
    on public.expiry_entries (expiry_date);
create index if not exists expiry_entries_deleted_idx
    on public.expiry_entries (is_deleted);
create index if not exists expiry_entries_created_at_idx
    on public.expiry_entries (created_at);

alter table public.product_master enable row level security;
alter table public.product_barcodes enable row level security;
alter table public.expiry_entries enable row level security;

-- No client policies are created here. Add a controlled read/write path before
-- production use. Do not use unrestricted anonymous insert/update policies.

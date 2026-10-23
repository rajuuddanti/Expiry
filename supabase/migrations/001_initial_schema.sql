-- Mahalaxmi Expiry Manager: two-table schema
-- Tables required: product_master and expiry_entries.
-- Review/apply in Supabase SQL Editor. No public client policies are created here.

create extension if not exists pgcrypto;

-- One row per scannable code (SKU code or EAN), with each code resolving
-- to the canonical SKU. All code columns are text to preserve leading zeros.
-- Multiple EAN rows may point to the same sku_code.
create table if not exists public.product_master (
    scan_code text primary key,
    sku_code text not null,
    sku_name text not null,
    ean text,
    is_active boolean not null default true,
    updated_at timestamptz not null default now()
);

create index if not exists product_master_sku_code_idx
    on public.product_master (sku_code);
create index if not exists product_master_ean_idx
    on public.product_master (ean);
create index if not exists product_master_active_idx
    on public.product_master (is_active);

-- Manager-submitted expiry records. No physical deletion: corrections set is_deleted=true.
create table if not exists public.expiry_entries (
    id uuid primary key default gen_random_uuid(),
    client_record_id uuid not null unique,
    store_name text not null,
    sku_code text not null,
    sku_name text not null,
    ean text,
    qty integer not null check (qty > 0),
    expiry_date date not null,
    session_start timestamptz not null,
    created_at timestamptz not null default now(),
    device_created_at timestamptz,
    uploaded_at timestamptz not null default now(),
    is_deleted boolean not null default false,
    deleted_at timestamptz,
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
alter table public.expiry_entries enable row level security;

-- Before production, configure a controlled read/write path. Do not put a
-- Supabase service-role key in Android and do not grant unrestricted anon writes.

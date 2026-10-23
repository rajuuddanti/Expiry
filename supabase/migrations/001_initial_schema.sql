-- Mahalaxmi Expiry Manager: initial schema
-- Apply in Supabase SQL Editor after reviewing table names and access policies.
-- This migration intentionally does not create open/public policies.

create extension if not exists pgcrypto;

create table if not exists public.product_master (
    id uuid primary key default gen_random_uuid(),
    sku_code text not null,
    sku_name text not null,
    ean text,
    category text,
    is_active boolean not null default true,
    updated_at timestamptz not null default now()
);

create index if not exists product_master_sku_code_idx
    on public.product_master (sku_code);
create index if not exists product_master_ean_idx
    on public.product_master (ean);
create index if not exists product_master_active_idx
    on public.product_master (is_active);

-- A product can have more than one barcode, and the master may contain duplicate
-- SKU rows from source imports until the master import rules are finalized.
-- Therefore we deliberately do not enforce uniqueness on EAN/SKU in this first schema.

create table if not exists public.expiry_entries (
    id uuid primary key default gen_random_uuid(),
    client_record_id uuid not null,
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
    constraint expiry_entries_client_record_id_key unique (client_record_id),
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

-- Enable RLS now. Policies / secure RPC or Edge Function are deliberately a
-- separate deployment step: the app must not use a service-role key, and
-- unrestricted anon insert/update policies are not safe for 17 store clients.
alter table public.product_master enable row level security;
alter table public.expiry_entries enable row level security;

-- No client policies are created in this migration. The app is not production-ready
-- until an authenticated/controlled write path and appropriate read policies exist.

-- Security foundation for expiry uploads.
-- REVIEW ONLY: do not apply to production until the Edge Function and registration flow are ready.
-- public.MASTER is intentionally untouched.

alter table public.expiry_scan_uploads
  add column if not exists ip_address inet,
  add column if not exists security_flag boolean not null default false,
  add column if not exists security_reason text;

-- Server-managed device-to-store binding. No client-facing RLS policies are created.
create table if not exists public.expiry_device_registry (
  device_id text primary key,
  registered_store text not null,
  is_active boolean not null default true,
  registered_by uuid references auth.users(id) on delete set null,
  registered_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.expiry_device_registry enable row level security;

-- Append-only security event log, written by a trusted server-side function.
create table if not exists public.expiry_security_events (
  id uuid primary key default gen_random_uuid(),
  event_type text not null,
  device_id text,
  owner_id uuid references auth.users(id) on delete set null,
  registered_store text,
  claimed_store text,
  observed_ip inet,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists expiry_security_events_created_at_idx
  on public.expiry_security_events (created_at desc);
create index if not exists expiry_security_events_device_created_idx
  on public.expiry_security_events (device_id, created_at desc);

alter table public.expiry_security_events enable row level security;

-- Intentionally no policies for direct client reads/writes. Server-side access must
-- be narrowly implemented in the Edge Function; never put a service-role key in Android.

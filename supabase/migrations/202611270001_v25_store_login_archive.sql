-- V2.5 store login sessions and per-store archive sync cooldown.
-- The schema is intentionally not exposed through the Supabase Data API.
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table if not exists private.store_sessions (
  token_hash text primary key,
  mobile_number text not null references public.store_accounts(mobile_number) on delete cascade,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);
create index if not exists store_sessions_mobile_idx
  on private.store_sessions(mobile_number);

create table if not exists private.store_archive_sync_state (
  mobile_number text primary key references public.store_accounts(mobile_number) on delete cascade,
  last_successful_sync_at timestamptz,
  active_sync_id uuid,
  active_sync_started_at timestamptz
);

revoke all on all tables in schema private from public, anon, authenticated;
grant usage on schema private to service_role;
grant all on all tables in schema private to service_role;

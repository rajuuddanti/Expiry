-- Store + approved public IP allowlist for expiry-upload monitoring.
-- Unknown IPs are allowed by the Edge Function but flagged for admin review.
-- public.MASTER is intentionally untouched.
create table if not exists public.expiry_store_ip_registry (
  store_name text not null,
  approved_ip inet not null,
  is_active boolean not null default true,
  approved_by uuid references auth.users(id) on delete set null,
  approved_at timestamptz not null default now(),
  notes text,
  primary key (store_name, approved_ip)
);

alter table public.expiry_store_ip_registry enable row level security;

create index if not exists expiry_store_ip_registry_active_store_idx
  on public.expiry_store_ip_registry (store_name)
  where is_active = true;

-- No direct client policies. Manage approved store/IP pairs only through trusted
-- admin SQL or server-side admin tooling. Never expose service-role credentials.

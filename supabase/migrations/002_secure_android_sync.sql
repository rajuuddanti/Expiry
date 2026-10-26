-- Secure client sync for Mahalaxmi Expiry Manager.
-- Requires Anonymous Sign-Ins enabled in Supabase Auth.
-- No service-role key is used by the Android client.

alter table public.expiry_entries
    add column if not exists owner_id uuid references auth.users(id) on delete set null;

create index if not exists expiry_entries_owner_id_idx
    on public.expiry_entries (owner_id);

grant select on public.product_master to anon, authenticated;
grant select, insert, update on public.expiry_entries to authenticated;

drop policy if exists "Published product master readable by app" on public.product_master;
create policy "Published product master readable by app"
    on public.product_master
    for select
    to anon, authenticated
    using (is_active = true);

drop policy if exists "Users can read their own expiry entries" on public.expiry_entries;
create policy "Users can read their own expiry entries"
    on public.expiry_entries
    for select
    to authenticated
    using (owner_id = auth.uid());

drop policy if exists "Users can insert their own expiry entries" on public.expiry_entries;
create policy "Users can insert their own expiry entries"
    on public.expiry_entries
    for insert
    to authenticated
    with check (owner_id = auth.uid());

drop policy if exists "Users can update their own expiry entries" on public.expiry_entries;
create policy "Users can update their own expiry entries"
    on public.expiry_entries
    for update
    to authenticated
    using (owner_id = auth.uid())
    with check (owner_id = auth.uid());

-- Existing rows with owner_id NULL remain protected from client access.
-- Admin-wide reporting should use a separately authenticated server-side path,
-- not a service-role key embedded in the APK.

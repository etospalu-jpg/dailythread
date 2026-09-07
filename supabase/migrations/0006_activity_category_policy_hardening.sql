drop policy if exists categories_own on public.activity_categories;
create policy categories_select on public.activity_categories for select using (user_id is null or user_id = auth.uid());
create policy categories_insert_own on public.activity_categories for insert with check (user_id = auth.uid());
create policy categories_update_own on public.activity_categories for update using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy categories_delete_own on public.activity_categories for delete using (user_id = auth.uid());
create unique index if not exists activity_categories_system_name_uidx on public.activity_categories(lower(name)) where user_id is null;
create unique index if not exists activity_categories_user_name_uidx on public.activity_categories(user_id, lower(name)) where user_id is not null;

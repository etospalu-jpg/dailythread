-- Use a stable search_path and prevent direct execution of trigger helpers.
create or replace function public.touch_version()
returns trigger language plpgsql set search_path = public as $$
begin
  new.updated_at = now();
  if tg_op = 'UPDATE' then new.version = old.version + 1; end if;
  return new;
end $$;

revoke execute on function public.handle_new_user() from public, anon, authenticated;
revoke execute on function public.log_entity_change() from public, anon, authenticated;

-- Replace RLS expressions with init-plan friendly auth.uid lookups.
drop policy if exists profiles_own on public.profiles;
create policy profiles_own on public.profiles for all using (id = (select auth.uid())) with check (id = (select auth.uid()));

drop policy if exists devices_own on public.devices;
create policy devices_own on public.devices for all using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists focus_own on public.focus_items;
create policy focus_own on public.focus_items for all using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists tasks_own on public.tasks;
create policy tasks_own on public.tasks for all using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists activities_own on public.activities;
create policy activities_own on public.activities for all using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists habits_own on public.habits;
create policy habits_own on public.habits for all using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists habit_entries_own on public.habit_entries;
create policy habit_entries_own on public.habit_entries for all using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists reviews_own on public.daily_reviews;
create policy reviews_own on public.daily_reviews for all using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists sync_mutations_own on public.sync_mutations;
create policy sync_mutations_own on public.sync_mutations for all using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists change_log_read_own on public.change_log;
create policy change_log_read_own on public.change_log for select using (user_id = (select auth.uid()));

drop policy if exists categories_own on public.activity_categories;
drop policy if exists categories_read on public.activity_categories;
drop policy if exists categories_insert on public.activity_categories;
drop policy if exists categories_update on public.activity_categories;
drop policy if exists categories_delete on public.activity_categories;
create policy categories_read on public.activity_categories for select using (user_id is null or user_id = (select auth.uid()));
create policy categories_insert on public.activity_categories for insert with check (user_id = (select auth.uid()) and is_system = false);
create policy categories_update on public.activity_categories for update using (user_id = (select auth.uid()) and is_system = false) with check (user_id = (select auth.uid()) and is_system = false);
create policy categories_delete on public.activity_categories for delete using (user_id = (select auth.uid()) and is_system = false);

-- Current sync foundation and security hardening applied after 0001.
create or replace function public.touch_version()
returns trigger language plpgsql set search_path = public as $$
begin
  new.updated_at = now();
  if tg_op = 'UPDATE' then new.version = old.version + 1; end if;
  return new;
end $$;

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, username, display_name)
  values (
    new.id,
    nullif(lower(trim(coalesce(new.raw_user_meta_data->>'username',''))), ''),
    coalesce(nullif(trim(coalesce(new.raw_user_meta_data->>'display_name','')), ''), split_part(coalesce(new.email,''), '@', 1), '')
  ) on conflict (id) do nothing;
  return new;
end $$;

create or replace function public.log_entity_change()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  op public.sync_operation;
begin
  if tg_op = 'INSERT' then op := 'CREATE';
  elsif new.deleted_at is not null and old.deleted_at is null then op := 'DELETE';
  else op := 'UPDATE'; end if;
  insert into public.change_log(user_id, entity_type, entity_id, operation, version, origin_device_id)
  values (new.user_id, tg_argv[0], new.id, op, coalesce(new.version,1), new.origin_device_id);
  return new;
end $$;

revoke execute on function public.handle_new_user() from public, anon, authenticated;
revoke execute on function public.log_entity_change() from public, anon, authenticated;

create index if not exists sync_mutations_user_id_idx on public.sync_mutations(user_id);
create index if not exists tasks_linked_focus_id_idx on public.tasks(linked_focus_id);

-- Change-log triggers
create trigger log_focus_change after insert or update on public.focus_items for each row execute function public.log_entity_change('focus_item');
create trigger log_task_change after insert or update on public.tasks for each row execute function public.log_entity_change('task');
create trigger log_activity_change after insert or update on public.activities for each row execute function public.log_entity_change('activity');
create trigger log_habit_change after insert or update on public.habits for each row execute function public.log_entity_change('habit');
create trigger log_habit_entry_change after insert or update on public.habit_entries for each row execute function public.log_entity_change('habit_entry');
create trigger log_review_change after insert or update on public.daily_reviews for each row execute function public.log_entity_change('daily_review');

-- Auth profile trigger
create trigger on_auth_user_created after insert on auth.users for each row execute function public.handle_new_user();

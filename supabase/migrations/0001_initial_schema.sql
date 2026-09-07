-- Daily Thread initial schema
create extension if not exists pgcrypto;

create type public.focus_status as enum ('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED');
create type public.task_status as enum ('TODO','DONE','CANCELLED');
create type public.task_priority as enum ('LOW','MEDIUM','HIGH');
create type public.sync_operation as enum ('CREATE','UPDATE','DELETE');

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique,
  display_name text not null default '',
  target_focus_minutes integer not null default 120 check (target_focus_minutes between 15 and 720),
  timezone text not null default 'Asia/Makassar',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.devices (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text,
  platform text not null default 'android',
  app_version text,
  last_seen_at timestamptz,
  created_at timestamptz not null default now(),
  unique(user_id,id)
);

create table public.focus_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  legacy_id text,
  focus_date date not null,
  title text not null,
  notes text not null default '',
  estimate_minutes integer not null default 0 check (estimate_minutes >= 0),
  sort_order smallint not null default 1 check (sort_order between 1 and 3),
  status public.focus_status not null default 'PLANNED',
  version bigint not null default 1,
  origin_device_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.tasks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  legacy_id text,
  scheduled_date date,
  title text not null,
  description text not null default '',
  priority public.task_priority not null default 'MEDIUM',
  due_at timestamptz,
  status public.task_status not null default 'TODO',
  linked_focus_id uuid references public.focus_items(id) on delete set null,
  estimate_minutes integer not null default 0 check (estimate_minutes >= 0),
  version bigint not null default 1,
  origin_device_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.activity_categories (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id) on delete cascade,
  name text not null,
  is_system boolean not null default false,
  created_at timestamptz not null default now(),
  unique(user_id,name)
);

create table public.activities (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  legacy_id text,
  activity_date date not null,
  title text not null,
  note text not null default '',
  source text not null default 'manual',
  linked_entity_id uuid,
  category_name text not null default 'Lainnya',
  started_at timestamptz not null,
  ended_at timestamptz not null,
  duration_minutes integer not null check (duration_minutes > 0),
  version bigint not null default 1,
  origin_device_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  check (ended_at > started_at)
);

create table public.habits (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  legacy_id text,
  name text not null,
  is_active boolean not null default true,
  frequency jsonb not null default '{"type":"daily"}'::jsonb,
  version bigint not null default 1,
  origin_device_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.habit_entries (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  habit_id uuid not null references public.habits(id) on delete cascade,
  entry_date date not null,
  completed boolean not null default false,
  completed_at timestamptz,
  version bigint not null default 1,
  origin_device_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.daily_reviews (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  legacy_id text,
  review_date date not null,
  achievement text not null default '',
  blocker text not null default '',
  tomorrow_priority text not null default '',
  mood text not null default '',
  daily_score smallint not null default 0 check (daily_score between 0 and 100),
  version bigint not null default 1,
  origin_device_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.sync_mutations (
  mutation_id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  device_id uuid,
  entity_type text not null,
  entity_id uuid not null,
  operation public.sync_operation not null,
  received_at timestamptz not null default now()
);

create table public.change_log (
  cursor bigserial primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  entity_type text not null,
  entity_id uuid not null,
  operation public.sync_operation not null,
  version bigint not null,
  origin_device_id uuid,
  changed_at timestamptz not null default now()
);

create unique index focus_items_active_slot_uidx on public.focus_items(user_id, focus_date, sort_order) where deleted_at is null;
create unique index habits_active_name_uidx on public.habits(user_id, lower(name)) where deleted_at is null;
create unique index habit_entries_active_date_uidx on public.habit_entries(habit_id, entry_date) where deleted_at is null;
create unique index daily_reviews_active_date_uidx on public.daily_reviews(user_id, review_date) where deleted_at is null;
create index focus_items_user_date_idx on public.focus_items(user_id, focus_date) where deleted_at is null;
create index tasks_user_date_idx on public.tasks(user_id, scheduled_date) where deleted_at is null;
create index activities_user_date_idx on public.activities(user_id, activity_date) where deleted_at is null;
create index habit_entries_user_date_idx on public.habit_entries(user_id, entry_date) where deleted_at is null;
create index change_log_user_cursor_idx on public.change_log(user_id, cursor);

-- updated_at/version helper
create or replace function public.touch_version()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  if tg_op = 'UPDATE' then
    new.version = old.version + 1;
  end if;
  return new;
end $$;

create trigger touch_focus before update on public.focus_items for each row execute function public.touch_version();
create trigger touch_tasks before update on public.tasks for each row execute function public.touch_version();
create trigger touch_activities before update on public.activities for each row execute function public.touch_version();
create trigger touch_habits before update on public.habits for each row execute function public.touch_version();
create trigger touch_habit_entries before update on public.habit_entries for each row execute function public.touch_version();
create trigger touch_reviews before update on public.daily_reviews for each row execute function public.touch_version();

-- RLS
alter table public.profiles enable row level security;
alter table public.devices enable row level security;
alter table public.focus_items enable row level security;
alter table public.tasks enable row level security;
alter table public.activity_categories enable row level security;
alter table public.activities enable row level security;
alter table public.habits enable row level security;
alter table public.habit_entries enable row level security;
alter table public.daily_reviews enable row level security;
alter table public.sync_mutations enable row level security;
alter table public.change_log enable row level security;

create policy profiles_own on public.profiles for all using (id = auth.uid()) with check (id = auth.uid());
create policy devices_own on public.devices for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy focus_own on public.focus_items for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy tasks_own on public.tasks for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy categories_own on public.activity_categories for all using (user_id is null or user_id = auth.uid()) with check (user_id = auth.uid());
create policy activities_own on public.activities for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy habits_own on public.habits for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy habit_entries_own on public.habit_entries for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy reviews_own on public.daily_reviews for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy sync_mutations_own on public.sync_mutations for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy change_log_read_own on public.change_log for select using (user_id = auth.uid());

-- Realtime-friendly primary entities can be added to publication after project creation.

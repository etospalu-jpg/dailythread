create index if not exists sync_mutations_user_idx on public.sync_mutations(user_id);
create index if not exists tasks_linked_focus_idx on public.tasks(linked_focus_id) where linked_focus_id is not null;

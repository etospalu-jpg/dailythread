-- The project already contains optimized category policies from the security-hardening migration.
-- Remove the temporary duplicate policy names introduced by 0006.
drop policy if exists categories_select on public.activity_categories;
drop policy if exists categories_insert_own on public.activity_categories;
drop policy if exists categories_update_own on public.activity_categories;
drop policy if exists categories_delete_own on public.activity_categories;

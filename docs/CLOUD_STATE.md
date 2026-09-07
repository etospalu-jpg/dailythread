# Cloud State — Daily Thread

Supabase project ref: `kmrgpityqzksuqvuqyfv`
Region: `ap-southeast-1`

Current cloud migration history:
1. `0001_initial_schema`
2. `0002_offline_sync_foundation`
3. `0003_realtime_entities`
4. `0004_security_hardening`
5. `0005_fk_indexes`
6. `0006_activity_category_policy_hardening`
7. `0007_activity_category_policy_cleanup`

Edge Functions:
- `sync` version 2, JWT verification enabled.

Security advisor after current changes: no security lints.
Performance advisor: only unused-index informational notices remain, expected on a new/empty project.

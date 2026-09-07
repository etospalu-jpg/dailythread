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
8. `0008_sync_observability`

Edge Functions:
- `sync` version 5, JWT verification enabled.
- Push mutations are idempotent through `sync_mutations`.
- Pull uses per-user `change_log` cursors.
- Unique/version conflicts return the latest server entity for explicit client resolution.
- v5 records PUSH/PULL observability in `sync_events`.
- v5 device heartbeat records device name, app version, platform, and last-seen time.

Security posture:
- Row Level Security is enabled on application tables.
- Sync forces ownership from the verified JWT user; client-provided `user_id` cannot override ownership.
- Android `0.4.0-rc1` also scopes its local outbox and sync cursor per user to prevent cross-account mutation replay after account switching.
- Service-role credentials are never embedded in the APK or web dashboard.

Security Advisor after v5 deployment: no security lints.
Performance Advisor currently reports only unused-index informational notices, expected while the production dataset is still new/empty.

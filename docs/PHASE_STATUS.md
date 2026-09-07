# Daily Thread — Phase Status

## Phase 1 — Analysis & blueprint
Status: COMPLETE

Existing Apps Script / Google Sheet behavior has been mapped to the new Android + Supabase architecture.

## Phase 2 — Cloud setup
Status: COMPLETE

Supabase project: `kmrgpityqzksuqvuqyfv` (Daily Thread, Singapore).

## Phase 3 — Backend & database foundation
Status: COMPLETE (foundation)

Implemented:
- Auth-backed profiles
- Row Level Security
- Focus, Tasks, Activities, Habits, Habit Entries, Reviews
- Device registry
- Change log cursor
- Mutation receipt / idempotency table
- Realtime publication
- Versioning + soft delete
- Security hardening

## Phase 4 — Android native foundation
Status: COMPLETE

Implemented:
- Kotlin + Jetpack Compose
- Room / SQLite
- DataStore session
- WorkManager periodic sync
- Retrofit Supabase Auth + Edge Function client
- Token refresh support
- Offline session bootstrap

## Phase 5 — Full offline CRUD
Status: IMPLEMENTED v0.2

Implemented locally:
- Focus: create/read/update/status/delete, max 3 slots/day
- Tasks: create/read/update/toggle/move tomorrow/delete
- Activities: create/read/update/delete
- Habits + daily entries: create/read/rename/complete/delete
- Night Review: create/read/update/delete
- Daily Score calculation
- Per-user local reads
- Outbox mutation queue for all entities
- Tombstone soft delete
- Local pending count UI

## Phase 6 — Sync + realtime
Status: IN PROGRESS

Implemented:
- Push queue
- Pull cursor
- Full entity mapping
- Version conflict detection
- Conflict-safe pull (does not overwrite unsynced local changes)
- Refresh-token based online session renewal
- Device heartbeat registration
- Edge Function sync v2

Remaining:
- User-facing conflict resolution screen
- Realtime socket listener into Room
- Backoff observability / sync error history

## Phase 7 — Admin + automation
Status: NOT STARTED

## Phase 8 — Testing + release
Status: STARTED

Implemented:
- Unit test skeleton for Daily Score
- GitHub Actions workflow for test + debug APK build

Remaining:
- Instrumentation tests
- Offline/online matrix tests
- Multi-device tests
- Release signing / AAB

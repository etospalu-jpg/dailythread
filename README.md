# Daily Thread — Offline-first Full Stack

Daily Thread is being migrated from Google Apps Script + Google Sheets into a native Android application whose local Room database remains usable without internet and synchronizes to Supabase when connectivity returns. A Next.js web dashboard now reads the same cloud data through Supabase Auth + Row Level Security.

## Stack

- Android: Kotlin + Jetpack Compose
- Local data: Room / SQLite
- Session: DataStore
- Background sync: WorkManager
- Cloud: Supabase Auth + PostgreSQL + Edge Functions + Realtime
- Web dashboard: Next.js
- CI: GitHub Actions
- Deployment target: Vercel

## Current state

- Android `0.3.0`: realtime + conflict resolution foundation
- Supabase `/sync`: Edge Function v4 with persistent sync observability
- Phase 7: web dashboard + automation in progress

## Offline flow

```text
UI
 ↓
Repository
 ↓
Room transaction ─────→ Outbox mutation
 ↓                         ↓
Instant local UI       WorkManager when online
                           ↓
                     Supabase /sync
                           ↓
                       Postgres
                           ↓
                       change_log
                           ↓
                       cursor pull
                           ↓
                          Room
```

## Implemented entities

Focus, Tasks, Activities, Habits, Habit Entries, Daily Review.

Each local write is committed to Room first and queued for cloud sync in the same Room transaction. Foreground Supabase Realtime invalidation triggers pull sync, while version conflicts can be resolved by keeping the server copy or retrying the local copy against the latest server version.

## Web dashboard

The `web/` Next.js app includes:
- Supabase login
- Daily Score and today metrics
- Focus and task overview
- Night Review snapshot
- Device last-seen monitoring
- Sync history from `sync_events`

The browser uses only the public/publishable Supabase key. It never uses a service-role key.

## Supabase

Project ref: `kmrgpityqzksuqvuqyfv`

## Build Android

```bash
cd android
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

## Build Web

```bash
cd web
npm install
npm run build
```

GitHub Actions validates both Android and web builds.

See `docs/PHASE_STATUS.md`, `docs/CLOUD_STATE.md`, and `docs/WEB_DASHBOARD.md`.

# Daily Thread Android — Offline-first Full Stack

Daily Thread is being migrated from Google Apps Script + Google Sheets into a native Android application whose local Room database remains usable without internet and synchronizes to Supabase when connectivity returns.

## Stack

- Android: Kotlin + Jetpack Compose
- Local data: Room / SQLite
- Session: DataStore
- Background sync: WorkManager
- Cloud: Supabase Auth + PostgreSQL + Edge Functions + Realtime
- CI: GitHub Actions

## Current version

`0.2.0` — Phase 5 offline CRUD foundation.

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

Each local write is committed to Room first and queued for cloud sync in the same Room transaction.

## Supabase

Project ref: `kmrgpityqzksuqvuqyfv`

The Android app uses the public/publishable key only. Never put a service-role key in the APK or GitHub source.

## Build

From the `android` directory:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

GitHub Actions runs both automatically once this project is pushed into its own repository.

See `docs/PHASE_STATUS.md` and `docs/CLOUD_STATE.md`.

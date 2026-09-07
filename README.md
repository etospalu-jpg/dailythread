# Daily Thread — Offline-first Full Stack

Daily Thread is being migrated from Google Apps Script + Google Sheets into a native Android application whose local Room database remains usable without internet and synchronizes to Supabase when connectivity returns. A Next.js web dashboard reads the same cloud data through Supabase Auth + Row Level Security.

## Stack

- Android: Kotlin + Jetpack Compose
- Local data: Room / SQLite
- Session: DataStore + Android Keystore token encryption
- Background sync: WorkManager
- Cloud: Supabase Auth + PostgreSQL + Edge Functions + Realtime
- Web dashboard: Next.js
- CI: GitHub Actions
- Deployment target: Vercel

## Current state

- Android `0.4.0-rc2`: release-candidate testing track
- In-app account registration + login through Supabase Auth
- Access/refresh tokens encrypted with Android Keystore AES-GCM
- Cleartext Android network traffic disabled
- Supabase `/sync`: Edge Function v5
- Device heartbeat reports device name, app version, and last-seen time
- Persistent sync observability through `sync_events`
- Per-user Room outbox and sync cursor isolation
- Automatic offline-to-online WorkManager sync scheduling and retry
- Android CI gates: JVM tests, lint, instrumentation APK compile, debug APK build
- Emulator instrumentation workflow covers Room isolation and Keystore behavior
- Web dashboard production build passes in GitHub Actions
- Vercel production deploy is temporarily blocked by the connected Hobby team's API deployment quota, not by a source build failure

## Offline flow

```text
UI
 ↓
Repository
 ↓
Room transaction ─────→ User-scoped outbox mutation
 ↓                              ↓
Instant local UI          WorkManager when online
                                ↓
                          Supabase /sync v5
                                ↓
                            Postgres
                                ↓
                            change_log
                                ↓
                         per-user cursor pull
                                ↓
                               Room
```

## Implemented entities

Focus, Tasks, Activities, Habits, Habit Entries, Daily Review.

Each local write is committed to Room first and queued for cloud sync. Foreground Supabase Realtime invalidation triggers pull sync, while version conflicts can be resolved by keeping the server copy or retrying the local copy against the latest server version.

Daily Score uses the same rules across Android and web: Focus 40%, Tasks 20%, productive time 20%, Habits 20%; `Istirahat` is excluded from productive minutes.

## Web dashboard

The `web/` Next.js app includes:

- Supabase login
- Daily Score and today metrics
- Focus and task overview
- Night Review snapshot
- Device name/version/last-seen monitoring
- Sync history from `sync_events`

The browser uses only the public/publishable Supabase key. It never uses a service-role key.

## Supabase

Project ref: `kmrgpityqzksuqvuqyfv`

## Build Android

```bash
cd android
gradle :app:testDebugUnitTest
gradle :app:lintDebug
gradle :app:assembleDebugAndroidTest
gradle :app:assembleDebug
```

For emulator instrumentation tests:

```bash
gradle :app:connectedDebugAndroidTest
```

## Build Web

```bash
cd web
npm install
npm run build
```

GitHub Actions validates Android and web builds. See `docs/PHASE_STATUS.md`, `docs/CLOUD_STATE.md`, `docs/WEB_DASHBOARD.md`, `docs/TEST_MATRIX.md`, and `docs/RC2_DEVICE_TEST.md`.

# Daily Thread — Offline-first Full Stack

Daily Thread is a native Android productivity app migrated from the original Google Apps Script + Google Sheets implementation. The Android app is offline-first: Room remains usable without internet, while Supabase handles cloud synchronization when connectivity returns.

## Stack

- Android: Kotlin + Jetpack Compose
- Local data: Room / SQLite
- Local profile/session: DataStore + Android Keystore token encryption
- Background sync: WorkManager
- Cloud: Supabase PostgreSQL + anonymous Auth + Edge Functions + Realtime
- Web dashboard: Next.js
- CI: GitHub Actions
- Deployment target: Vercel

## Current Android release candidate

`0.5.0-rc3`

The APK is intentionally **no-login and portrait-first**:

- no email/password screen in the Android app
- app opens directly into Daily Thread
- the visible user name can be changed from Profile
- focus target can be changed from Profile
- a Supabase anonymous cloud identity is created silently in the background when internet is available
- local writes always go to Room first and are queued for sync
- phone layout uses compact 12dp page gutters and portrait orientation
- launcher artwork uses the Daily Thread DT ribbon mark

Important identity note: because Android now uses anonymous cloud identity, a secure device-pairing mechanism is required before two phones/tablets or the protected web dashboard can intentionally share the same Daily Thread cloud identity. This is the next sync milestone; the app does not reintroduce email/password login.

## Current cloud state

- Supabase `/sync`: Edge Function v5
- device heartbeat reports device name, app version, and last-seen time
- persistent sync observability through `sync_events`
- per-user Room outbox and sync cursor isolation
- automatic offline-to-online WorkManager scheduling and retry
- service-role credentials are never embedded in the APK

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

## Core features

Focus, Tasks, Activities/Timeline, Habits, Habit Entries, Night Review, Focus Timer, Progress, Daily Score, Profile, offline mutation queue, conflict handling, background sync, and Realtime invalidation.

Daily Score rules are consistent across Android and web: Focus 40%, Tasks 20%, productive time 20%, Habits 20%; `Istirahat` is excluded from productive minutes.

## Web dashboard

The `web/` Next.js dashboard build is CI-validated and includes Daily Score, today metrics, device monitoring, and sync-event monitoring. Production deployment is temporarily blocked by the connected Vercel Hobby API deployment quota. The web dashboard is not required to use the Android APK.

Because the Android app is now no-login, web-to-Android identity pairing is intentionally marked as pending rather than silently relying on a different email/password account.

## Supabase

Project ref: `kmrgpityqzksuqvuqyfv`

## Android validation

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

## Web validation

```bash
cd web
npm install
npm run build
```

GitHub Actions validates Android and web builds. See `docs/PHASE_STATUS.md`, `docs/CLOUD_STATE.md`, `docs/WEB_DASHBOARD.md`, `docs/TEST_MATRIX.md`, and `docs/RC2_DEVICE_TEST.md`.

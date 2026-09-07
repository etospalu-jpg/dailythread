# Daily Thread 0.4.0-rc2 — Physical Device Test

Use the newest RC2 APK produced by GitHub Actions.

## Test A — First account + initial sync
1. Install APK.
2. Choose **Belum punya akun? Buat akun**.
3. Register with an email and password of at least 6 characters.
4. If email confirmation is required, confirm it and then login.
5. Add one Focus, one Task, one Habit, one Activity, and one Night Review.
6. Open **Sync** and verify the pending count returns to zero while online.

Expected: the app remains usable, data appears locally immediately, and cloud sync completes.

## Test B — Offline -> online
1. While logged in, enable airplane mode.
2. Add/edit/complete several records.
3. Close and reopen the app while still offline.
4. Confirm local data remains visible and editable.
5. Disable airplane mode.
6. Wait briefly or open **Sync**.

Expected: queued changes sync automatically and pending returns to zero without data loss.

## Test C — App restart and session
1. Login online once.
2. Close the app completely.
3. Disable internet.
4. Reopen Daily Thread.

Expected: the existing local account/session can open offline. Authentication tokens are stored encrypted with Android Keystore.

## Test D — Two devices
1. Install the same RC2 on device A and device B.
2. Login using the same account.
3. Create a Task on device A and sync.
4. Open device B online and verify the Task arrives.
5. Edit the same Task differently on A and B while one device is offline.
6. Reconnect and sync both devices.

Expected: no duplicate silent overwrite; a version conflict is surfaced when necessary and can be resolved with **Pakai server** or **Pakai lokal**.

## Test E — Account isolation
1. On one Android device, login to account A and create offline data.
2. Logout before it is synced if practical.
3. Login as account B.

Expected: account A outbox/data must never be pushed as account B. Pending/conflict counts are user scoped.

## Pass criteria
RC2 is ready for production-signing work when tests A-E pass, GitHub Android Build is green, Android Instrumentation is green, and Supabase Security Advisor has no security lint.

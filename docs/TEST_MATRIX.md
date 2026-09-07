# Daily Thread — Release Test Matrix

Target candidate: `0.4.0-rc1`

## Automated gates

| Area | Scenario | Expected result | Automation |
|---|---|---|---|
| Daily Score | Perfect day | Score = 100 | JVM unit test |
| Daily Score | Empty day | Score = 0 | JVM unit test |
| Daily Score | Rest activity | Does not add productive-time score | JVM unit test |
| Daily Score | Inactive habit | Does not reduce habit score | JVM unit test |
| Daily Score | Soft-deleted data | Ignored by score calculation | JVM unit test |
| Daily Score | Excess productive minutes | Time contribution capped at 20 points | JVM unit test |
| Room | Soft-deleted focus | Hidden from date query | Android instrumentation |
| Room | User isolation | User A cannot read user B local rows through scoped DAO query | Android instrumentation |
| Outbox | Pending counter | Counts PENDING/FAILED/CONFLICT, excludes SYNCED | Android instrumentation |
| Conflict | Keep-local retry | Resets conflict to PENDING with latest server version | Android instrumentation |
| Build | Kotlin/Compose/Room compile | Debug APK builds | GitHub Actions |
| Build | Instrumentation APK compile | Android test APK builds | GitHub Actions |
| Quality | Android lint | No blocking lint error | GitHub Actions |

## Manual device matrix before production release

| Mode | Scenario | Pass criteria |
|---|---|---|
| Offline | Launch after a previously valid login | Local data opens without network |
| Offline | Create Focus/Task/Activity/Habit/Review | UI updates immediately and outbox pending count increases |
| Offline | Edit and delete existing data | Changes remain after app restart |
| Offline → Online | Restore network | Pending mutations reach Supabase and local state becomes synced |
| Online → Offline → Online | Repeated network changes | No duplicate records; retries are idempotent |
| Multi-device | Device A creates, Device B is online | Device B receives Realtime invalidation and pulls the change |
| Multi-device conflict | A and B edit same entity offline | Version conflict appears instead of silent overwrite |
| Conflict resolution | Keep server | Server entity replaces local copy and conflict clears |
| Conflict resolution | Keep local | Local mutation retries against latest server version |
| Delete propagation | Delete on A while B is offline | B receives tombstone after reconnect and hides row |
| Token refresh | Access token expires while refresh token is valid | Sync renews session and continues |
| Session expiry | Refresh token invalid/expired | Local data remains readable; cloud sync waits for re-authentication |
| Process restart | Kill app during pending sync | Outbox survives and later sync resumes |
| Reboot | Device reboot with pending data | Room data survives; WorkManager resumes when network is available |
| Slow/unstable network | Requests timeout/retry | No data loss or duplicate mutation |

## Release blocker rules

Production APK/AAB must not be marked stable until:

1. JVM unit tests pass.
2. Android lint passes without blocking errors.
3. Android instrumentation tests pass on emulator.
4. Debug APK builds successfully from clean GitHub Actions runner.
5. Offline → online and multi-device conflict scenarios are verified on real devices.
6. Supabase Security Advisor has no unresolved security lint.
7. Release signing key is configured outside Git source.

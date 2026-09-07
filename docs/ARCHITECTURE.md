# Architecture

```text
Compose UI
   ↓
ViewModel / Repository
   ↓
Room / SQLite  ← local source of truth
   ↓
Outbox mutations
   ↓ WorkManager when online
Supabase Edge Function `sync`
   ↓
Supabase PostgreSQL + Auth + Realtime
```

Rules:
1. UI reads from Room, not directly from the network.
2. Writes commit locally first, then enqueue an outbox mutation.
3. Background sync pushes pending mutations, then pulls changes by cursor.
4. Deletes use tombstones (`deleted_at`) rather than destructive local deletes.
5. Server versions are authoritative for conflict detection.

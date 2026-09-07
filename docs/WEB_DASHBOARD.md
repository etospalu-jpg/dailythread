# Phase 7 — Web dashboard and automation

The `web/` application is a Next.js dashboard connected directly to Daily Thread Supabase using the publishable key and authenticated user session.

Current dashboard features:
- Supabase email/password login
- Daily Score summary
- Focus, task, productive-minute, habit, and device metrics
- Today's focus list
- Today's task list
- Night Review snapshot
- Device last-seen information
- Sync observability from `sync_events`

Automation foundation:
- Android test + APK build in GitHub Actions
- Web build validation in GitHub Actions
- Supabase migrations versioned in GitHub
- Supabase Edge Function source versioned in GitHub
- Vercel-ready Next.js app under `web/`

Security rule: the web app never contains a service-role key. All user data reads depend on Supabase Auth + RLS.

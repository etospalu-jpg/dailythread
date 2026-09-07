# Daily Thread Web Dashboard

Next.js dashboard for the same Supabase project used by the Android APK.

## Security

The browser only receives the public Supabase publishable key. Every data query is protected by Supabase Row Level Security, so a signed-in user can read only their own data.

## Local development

```bash
npm install
npm run dev
```

Environment variables are optional because the repository includes the public project URL and publishable key as safe defaults. `.env.example` is provided for deployment configuration.

## Vercel

Root directory: `web`
Framework: Next.js

No service-role key is required or permitted in the frontend.

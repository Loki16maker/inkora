# Inkora cloud sync

Inkora uses Supabase for optional account-backed sync on Windows and Android.
The local SQLDelight database remains the source of truth while a signed-in
account is offline-first: local snapshots are uploaded during **Sync now**, and
new notebook, whiteboard, text-document, and quick-note snapshots are pulled to
the device. PDF bytes stay local until the storage upload phase is enabled, so a
PDF is never recreated without its original file.

## Project

- Supabase project: `inkora-cloud`
- Project URL: `https://gfhjwencfqhxfsxwscyv.supabase.co`
- Dashboard: https://supabase.com/dashboard/project/gfhjwencfqhxfsxwscyv
- Migration: `supabase/migrations/202609130001_inkora_cloud.sql`

The project is on Supabase's Free plan. Anonymous requests are denied by RLS;
authenticated users can only read or change documents they own or documents
shared with them. Original files are stored in the private `documents` bucket.

## Build configuration

Never commit a Supabase secret. Release builds receive the public URL and
publishable client key from these GitHub Actions secrets:

- `INKORA_SUPABASE_URL`
- `INKORA_SUPABASE_PUBLISHABLE_KEY`

The publishable key is safe to ship in a client application; database access is
protected by the policies in the migration. Windows reads the values from the
generated resource file, and Android reads them from `BuildConfig`.

## Using it

Open **Create → Account** (or **Account** in the wide toolbar), create an
account or sign in, then choose **Sync now**. Supabase email confirmation may
require clicking the confirmation link before the first sign-in.

Google sign-in is enabled through Supabase Auth. Inkora uses the system browser
with a PKCE verifier, then receives the one-time callback on Windows through a
loopback listener (`127.0.0.1:54321`) and on Android through the `inkora://`
deep link. The Google client secret remains in Supabase and is never shipped in
the app. Shared links, PDF storage transfer, conflict resolution, and live
collaboration are represented by the database tables and realtime publication
and remain the next cloud phase.

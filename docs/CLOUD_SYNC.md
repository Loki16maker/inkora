# Inkora cloud sync

Inkora uses Supabase for optional account-backed sync on Windows and Android.
The local SQLDelight database remains the source of truth while a signed-in
account is offline-first: local snapshots are uploaded during **Sync now**, and
new notebook, whiteboard, text-document, quick-note, and PDF snapshots are
pulled to the device. PDF bytes are transferred through the private storage
bucket, so a PDF is never recreated without its original file.

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
the app.

From a document card, **Create share link** creates a role-scoped, revocable
link (viewer, commenter, or editor). **Invite collaborator** adds an existing
Inkora account by email and updates its role idempotently. Share tokens are
generated locally and only their SHA-256 digest is stored in Supabase. The
`resolve_share_link` SQL function rejects revoked or expired links before
returning the shared snapshot. Sync now also applies a remote snapshot when it
has a newer local modification timestamp and transfers PDF bytes through the
private storage bucket.

On Android, opening an `inkora://share/<token>` link imports the shared
non-PDF snapshot into the recipient's local library. PDF share links still
require the owner to provide a storage-backed download in a future web route;
the app deliberately avoids creating a broken local PDF from metadata alone.

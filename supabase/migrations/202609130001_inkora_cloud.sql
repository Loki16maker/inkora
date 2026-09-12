-- Inkora cloud foundation: private document sync, revisions, membership, and share links.
-- The local SQLDelight database remains the offline source of truth; these tables
-- receive encrypted-in-transit document snapshots and incremental revisions.

create extension if not exists pgcrypto;

create table if not exists public.documents (
    id uuid primary key,
    owner_id uuid not null references auth.users(id) on delete cascade,
    title text not null,
    kind text not null,
    payload jsonb not null,
    source_file_path text,
    content_hash text,
    version bigint not null default 1,
    deleted_at timestamptz,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.document_members (
    document_id uuid not null references public.documents(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null default 'viewer' check (role in ('viewer', 'commenter', 'editor')),
    created_at timestamptz not null default timezone('utc', now()),
    primary key (document_id, user_id)
);

create table if not exists public.document_revisions (
    id uuid primary key default gen_random_uuid(),
    document_id uuid not null references public.documents(id) on delete cascade,
    author_id uuid not null references auth.users(id) on delete cascade,
    version bigint not null,
    payload jsonb not null,
    created_at timestamptz not null default timezone('utc', now()),
    unique (document_id, version)
);

create table if not exists public.share_links (
    id uuid primary key default gen_random_uuid(),
    document_id uuid not null references public.documents(id) on delete cascade,
    created_by uuid not null references auth.users(id) on delete cascade,
    token_hash text not null unique,
    role text not null default 'viewer' check (role in ('viewer', 'commenter', 'editor')),
    expires_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz not null default timezone('utc', now())
);

create index if not exists documents_owner_updated_idx
    on public.documents(owner_id, updated_at desc);
create index if not exists document_members_user_idx
    on public.document_members(user_id, document_id);
create index if not exists document_revisions_document_version_idx
    on public.document_revisions(document_id, version desc);
create index if not exists share_links_document_idx
    on public.share_links(document_id, created_at desc);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = timezone('utc', now());
    return new;
end;
$$;

drop trigger if exists documents_set_updated_at on public.documents;
create trigger documents_set_updated_at
    before update on public.documents
    for each row execute function public.set_updated_at();

create or replace function public.is_document_member(target_document_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.documents d
        where d.id = target_document_id and d.owner_id = auth.uid()
    ) or exists (
        select 1 from public.document_members m
        where m.document_id = target_document_id and m.user_id = auth.uid()
    );
$$;

create or replace function public.can_edit_document(target_document_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.documents d
        where d.id = target_document_id and d.owner_id = auth.uid()
    ) or exists (
        select 1 from public.document_members m
        where m.document_id = target_document_id
          and m.user_id = auth.uid()
          and m.role = 'editor'
    );
$$;

alter table public.documents enable row level security;
alter table public.document_members enable row level security;
alter table public.document_revisions enable row level security;
alter table public.share_links enable row level security;

grant usage on schema public to authenticated;
grant select, insert, update, delete on public.documents to authenticated;
grant select, insert, update, delete on public.document_members to authenticated;
grant select, insert on public.document_revisions to authenticated;
grant select, insert, update, delete on public.share_links to authenticated;

drop policy if exists documents_select on public.documents;
create policy documents_select on public.documents
    for select to authenticated
    using (owner_id = auth.uid() or public.is_document_member(id));

drop policy if exists documents_insert on public.documents;
create policy documents_insert on public.documents
    for insert to authenticated
    with check (owner_id = auth.uid());

drop policy if exists documents_update on public.documents;
create policy documents_update on public.documents
    for update to authenticated
    using (public.can_edit_document(id))
    with check (owner_id = auth.uid() or public.can_edit_document(id));

drop policy if exists documents_delete on public.documents;
create policy documents_delete on public.documents
    for delete to authenticated
    using (owner_id = auth.uid());

drop policy if exists document_members_select on public.document_members;
create policy document_members_select on public.document_members
    for select to authenticated
    using (public.is_document_member(document_id));

drop policy if exists document_members_insert on public.document_members;
create policy document_members_insert on public.document_members
    for insert to authenticated
    with check (exists (
        select 1 from public.documents d
        where d.id = document_id and d.owner_id = auth.uid()
    ));

drop policy if exists document_members_update on public.document_members;
create policy document_members_update on public.document_members
    for update to authenticated
    using (exists (
        select 1 from public.documents d
        where d.id = document_id and d.owner_id = auth.uid()
    ));

drop policy if exists document_members_delete on public.document_members;
create policy document_members_delete on public.document_members
    for delete to authenticated
    using (exists (
        select 1 from public.documents d
        where d.id = document_id and d.owner_id = auth.uid()
    ));

drop policy if exists document_revisions_select on public.document_revisions;
create policy document_revisions_select on public.document_revisions
    for select to authenticated
    using (public.is_document_member(document_id));

drop policy if exists document_revisions_insert on public.document_revisions;
create policy document_revisions_insert on public.document_revisions
    for insert to authenticated
    with check (author_id = auth.uid() and public.can_edit_document(document_id));

drop policy if exists share_links_select on public.share_links;
create policy share_links_select on public.share_links
    for select to authenticated
    using (created_by = auth.uid());

drop policy if exists share_links_insert on public.share_links;
create policy share_links_insert on public.share_links
    for insert to authenticated
    with check (created_by = auth.uid() and exists (
        select 1 from public.documents d
        where d.id = document_id and d.owner_id = auth.uid()
    ));

drop policy if exists share_links_update on public.share_links;
create policy share_links_update on public.share_links
    for update to authenticated
    using (created_by = auth.uid())
    with check (created_by = auth.uid());

drop policy if exists share_links_delete on public.share_links;
create policy share_links_delete on public.share_links
    for delete to authenticated
    using (created_by = auth.uid());

-- Private object storage for original PDFs and attachments. Files are kept in
-- per-user folders until shared-download policies are added with the link API.
insert into storage.buckets (id, name, public)
values ('documents', 'documents', false)
on conflict (id) do nothing;

drop policy if exists documents_storage_select on storage.objects;
create policy documents_storage_select on storage.objects
    for select to authenticated
    using (bucket_id = 'documents' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists documents_storage_insert on storage.objects;
create policy documents_storage_insert on storage.objects
    for insert to authenticated
    with check (bucket_id = 'documents' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists documents_storage_update on storage.objects;
create policy documents_storage_update on storage.objects
    for update to authenticated
    using (bucket_id = 'documents' and (storage.foldername(name))[1] = auth.uid()::text)
    with check (bucket_id = 'documents' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists documents_storage_delete on storage.objects;
create policy documents_storage_delete on storage.objects
    for delete to authenticated
    using (bucket_id = 'documents' and (storage.foldername(name))[1] = auth.uid()::text);

-- Enable realtime document snapshots and revisions for the collaboration phase.
alter publication supabase_realtime add table public.documents;
alter publication supabase_realtime add table public.document_revisions;
alter publication supabase_realtime add table public.document_members;

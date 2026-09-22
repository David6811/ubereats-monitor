-- One row per offer the phone saw: the whole job object the board keeps,
-- keyed by the driver and the moment the card appeared. The phone upserts
-- its board after every change; deleting on the phone deletes here too.

create table if not exists public.jobs (
  user_id    uuid not null references auth.users (id) on delete cascade,
  at         bigint not null,
  job        jsonb not null,
  updated_at timestamptz not null default now(),
  primary key (user_id, at)
);

alter table public.jobs enable row level security;

create policy "own jobs: read"   on public.jobs for select using (auth.uid() = user_id);
create policy "own jobs: insert" on public.jobs for insert with check (auth.uid() = user_id);
create policy "own jobs: update" on public.jobs for update using (auth.uid() = user_id);
create policy "own jobs: delete" on public.jobs for delete using (auth.uid() = user_id);

create trigger jobs_touch before update on public.jobs
  for each row execute function public.touch_updated_at();

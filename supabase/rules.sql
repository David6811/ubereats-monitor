-- One row per driver: the whole rules.json, as the laptop editor writes it.
-- Run once in the Supabase SQL editor (or `supabase db push`).

create table if not exists public.rules (
  user_id    uuid primary key references auth.users (id) on delete cascade,
  rules      jsonb not null,
  updated_at timestamptz not null default now()
);

alter table public.rules enable row level security;

-- Only the signed-in driver sees or changes their own row.
create policy "own rules: read"   on public.rules for select using (auth.uid() = user_id);
create policy "own rules: insert" on public.rules for insert with check (auth.uid() = user_id);
create policy "own rules: update" on public.rules for update using (auth.uid() = user_id);

-- updated_at moves on every write, so the phone can tell a new version from the one it has.
create or replace function public.touch_updated_at() returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

create trigger rules_touch before update on public.rules
  for each row execute function public.touch_updated_at();

-- Lets the phone subscribe to changes on its own row.
alter publication supabase_realtime add table public.rules;

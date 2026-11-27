-- =====================================================================
-- WakeUp — UPGRADE HABIT (jalankan SETELAH supabase-schema.sql, supabase-migration.sql, supabase-backend.sql)
-- Cara pakai: Supabase Dashboard → SQL Editor → New query → paste → Run
-- Aman dijalankan berulang (idempotent) dan tidak menghapus data.
-- =====================================================================

-- ---------- 1. HARI PENGINGAT HABIT ----------
-- Nomor hari mengikuti Calendar Android: Minggu=1, Senin=2, ... Sabtu=7. Kosong = setiap hari.
alter table public.habits add column if not exists reminder_days smallint[] not null default '{}';

-- ---------- 2. TANTANGAN BARENG DI GRUP ----------
create table if not exists public.group_challenges (
  id           uuid primary key default gen_random_uuid(),
  group_id     uuid not null references public.groups(id) on delete cascade,
  created_by   uuid not null references public.profiles(id) on delete cascade,
  name         text not null,
  icon         text,
  target_count int not null default 1 check (target_count > 0),
  unit         text,
  created_at   timestamptz not null default now()
);

create table if not exists public.challenge_logs (
  challenge_id uuid not null references public.group_challenges(id) on delete cascade,
  user_id      uuid not null references public.profiles(id) on delete cascade,
  log_date     date not null default current_date,
  count        int not null default 0 check (count >= 0),
  primary key (challenge_id, user_id, log_date)   -- 1 baris per orang per hari
);

create index if not exists group_challenges_group_idx on public.group_challenges (group_id);
create index if not exists challenge_logs_date_idx    on public.challenge_logs (challenge_id, log_date);

-- Fungsi bantu RLS (security definer agar tidak rekursif)
create or replace function public.can_see_challenge(cid uuid)
returns boolean language sql security definer stable set search_path = public as $$
  select exists (
    select 1 from public.group_challenges c
    join public.group_members gm on gm.group_id = c.group_id
    where c.id = cid and gm.user_id = auth.uid());
$$;

alter table public.group_challenges enable row level security;
alter table public.challenge_logs   enable row level security;

-- Tantangan: semua anggota grup boleh melihat dan membuat; hanya pembuat (atau pemilik grup) yang boleh menghapus
drop policy if exists "challenge read"   on public.group_challenges;
drop policy if exists "challenge create" on public.group_challenges;
drop policy if exists "challenge update" on public.group_challenges;
drop policy if exists "challenge delete" on public.group_challenges;
create policy "challenge read"   on public.group_challenges for select to authenticated using (public.is_group_member(group_id));
create policy "challenge create" on public.group_challenges for insert to authenticated
  with check (created_by = auth.uid() and public.is_group_member(group_id));
create policy "challenge update" on public.group_challenges for update to authenticated using (created_by = auth.uid());
create policy "challenge delete" on public.group_challenges for delete to authenticated
  using (created_by = auth.uid() or public.is_group_owner(group_id));

-- Catatan: semua anggota grup boleh melihat catatan anggota lain; hanya pemilik catatan yang boleh menulis
drop policy if exists "clogs read"   on public.challenge_logs;
drop policy if exists "clogs insert" on public.challenge_logs;
drop policy if exists "clogs update" on public.challenge_logs;
drop policy if exists "clogs delete" on public.challenge_logs;
create policy "clogs read"   on public.challenge_logs for select to authenticated using (public.can_see_challenge(challenge_id));
create policy "clogs insert" on public.challenge_logs for insert to authenticated
  with check (user_id = auth.uid() and public.can_see_challenge(challenge_id));
create policy "clogs update" on public.challenge_logs for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "clogs delete" on public.challenge_logs for delete to authenticated using (user_id = auth.uid());

-- Selesai. Cek hasilnya:
--   select column_name from information_schema.columns where table_name = 'habits' and column_name = 'reminder_days';
--   select count(*) from public.group_challenges;

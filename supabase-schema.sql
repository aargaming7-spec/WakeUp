-- =====================================================================
-- WakeUp — skema database Supabase (PostgreSQL)
-- Cara pakai: Supabase Dashboard → SQL Editor → New query → paste → Run
-- Login/registrasi memakai Supabase Auth bawaan (tabel auth.users).
-- =====================================================================

-- ---------- 1. PROFIL PENGGUNA ----------
create table public.profiles (
  id          uuid primary key references auth.users(id) on delete cascade,
  username    text unique not null,
  full_name   text,
  avatar_url  text,
  created_at  timestamptz not null default now()
);

-- Otomatis buat profil saat user baru registrasi
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, username, full_name)
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'username', split_part(new.email, '@', 1)),
    new.raw_user_meta_data->>'full_name'
  );
  return new;
end $$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------- 2. ALARM + RIWAYAT ----------
create table public.alarms (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.profiles(id) on delete cascade,
  hour       smallint not null check (hour between 0 and 23),
  minute     smallint not null check (minute between 0 and 59),
  level      smallint not null default 0 check (level between 0 and 2), -- 0 Easy, 1 Medium, 2 Hard
  days       smallint[] not null default '{}',  -- Calendar.SUNDAY=1 .. SATURDAY=7; kosong = setiap hari
  enabled    boolean not null default true,
  created_at timestamptz not null default now()
);

create table public.alarm_logs (            -- dipakai untuk streak & statistik
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references public.profiles(id) on delete cascade,
  alarm_id     uuid references public.alarms(id) on delete set null,
  challenge_id int not null,
  completed_at timestamptz not null default now()
);

-- ---------- 3. HABIT TRACKER ----------
create table public.habits (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references public.profiles(id) on delete cascade,
  name          text not null,                  -- minum air, membaca, olahraga, belajar
  icon          text,
  target_count  int not null default 1 check (target_count > 0),  -- target harian
  unit          text,                           -- gelas, halaman, menit, ...
  reminder_time time,
  archived      boolean not null default false,
  created_at    timestamptz not null default now()
);

create table public.habit_logs (
  id        uuid primary key default gen_random_uuid(),
  habit_id  uuid not null references public.habits(id) on delete cascade,
  user_id   uuid not null references public.profiles(id) on delete cascade,
  log_date  date not null default current_date,
  count     int not null default 0 check (count >= 0),
  unique (habit_id, log_date)                   -- 1 baris per habit per hari
);

-- ---------- 4. REMINDER PRIBADI ----------
create table public.reminders (
  id                uuid primary key default gen_random_uuid(),
  user_id           uuid not null references public.profiles(id) on delete cascade,
  title             text not null,
  category          text not null default 'lainnya',  -- kuliah, rapat, tugas, olahraga, belajar
  notes             text,
  starts_at         timestamptz not null,
  remind_before_min int not null default 10,
  done              boolean not null default false,
  created_at        timestamptz not null default now()
);

-- ---------- 5. FRIENDS ----------
create table public.friendships (
  id           uuid primary key default gen_random_uuid(),
  requester_id uuid not null references public.profiles(id) on delete cascade,
  addressee_id uuid not null references public.profiles(id) on delete cascade,
  status       text not null default 'pending' check (status in ('pending','accepted','declined')),
  created_at   timestamptz not null default now(),
  check (requester_id <> addressee_id)
);
-- cegah pasangan duplikat (A→B dan B→A dianggap sama)
create unique index friendships_pair_idx on public.friendships
  (least(requester_id, addressee_id), greatest(requester_id, addressee_id));

-- ---------- 6. GROUPS ----------
create table public.groups (
  id          uuid primary key default gen_random_uuid(),
  name        text not null,                    -- kelompok TA, kelompok belajar, grup kelas
  description text,
  owner_id    uuid not null references public.profiles(id) on delete cascade,
  created_at  timestamptz not null default now()
);

create table public.group_members (
  group_id  uuid not null references public.groups(id) on delete cascade,
  user_id   uuid not null references public.profiles(id) on delete cascade,
  role      text not null default 'member' check (role in ('owner','member')),
  joined_at timestamptz not null default now(),
  primary key (group_id, user_id)
);

-- Pemilik otomatis jadi anggota saat grup dibuat
create or replace function public.add_owner_as_member()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.group_members (group_id, user_id, role) values (new.id, new.owner_id, 'owner');
  return new;
end $$;
create trigger on_group_created after insert on public.groups
  for each row execute function public.add_owner_as_member();

-- ---------- 7. SHARED SCHEDULE ----------
create table public.shared_schedules (
  id          uuid primary key default gen_random_uuid(),
  group_id    uuid not null references public.groups(id) on delete cascade,
  created_by  uuid not null references public.profiles(id) on delete cascade,
  title       text not null,                    -- meeting, kelas, diskusi, belajar bareng
  description text,
  starts_at   timestamptz not null,
  ends_at     timestamptz,
  created_at  timestamptz not null default now(),
  check (ends_at is null or ends_at > starts_at)
);

create table public.schedule_participants (
  schedule_id uuid not null references public.shared_schedules(id) on delete cascade,
  user_id     uuid not null references public.profiles(id) on delete cascade,
  status      text not null default 'invited' check (status in ('invited','going','declined')),
  primary key (schedule_id, user_id)
);

-- Saat jadwal dibuat, semua anggota grup otomatis diundang (pembuat langsung 'going')
create or replace function public.invite_group_members()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.schedule_participants (schedule_id, user_id, status)
  select new.id, gm.user_id, case when gm.user_id = new.created_by then 'going' else 'invited' end
  from public.group_members gm where gm.group_id = new.group_id;
  return new;
end $$;
create trigger on_schedule_created after insert on public.shared_schedules
  for each row execute function public.invite_group_members();

-- ---------- FUNGSI BANTU UNTUK RLS (security definer → hindari rekursi policy) ----------
create or replace function public.is_group_member(gid uuid)
returns boolean language sql security definer stable set search_path = public as $$
  select exists (select 1 from public.group_members where group_id = gid and user_id = auth.uid());
$$;

create or replace function public.is_group_owner(gid uuid)
returns boolean language sql security definer stable set search_path = public as $$
  select exists (select 1 from public.groups where id = gid and owner_id = auth.uid());
$$;

create or replace function public.can_see_schedule(sid uuid)
returns boolean language sql security definer stable set search_path = public as $$
  select exists (
    select 1 from public.shared_schedules s
    join public.group_members gm on gm.group_id = s.group_id
    where s.id = sid and gm.user_id = auth.uid());
$$;

-- ---------- ROW LEVEL SECURITY ----------
alter table public.profiles              enable row level security;
alter table public.alarms                enable row level security;
alter table public.alarm_logs            enable row level security;
alter table public.habits                enable row level security;
alter table public.habit_logs            enable row level security;
alter table public.reminders             enable row level security;
alter table public.friendships           enable row level security;
alter table public.groups                enable row level security;
alter table public.group_members         enable row level security;
alter table public.shared_schedules      enable row level security;
alter table public.schedule_participants enable row level security;

-- Profil: semua user login boleh melihat (untuk cari teman), hanya diri sendiri yang boleh ubah
create policy "profiles read"   on public.profiles for select to authenticated using (true);
create policy "profiles update" on public.profiles for update to authenticated using (id = auth.uid());

-- Data pribadi: hanya pemilik
create policy "alarms own"      on public.alarms      for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "alarm_logs own"  on public.alarm_logs  for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "habits own"      on public.habits      for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "habit_logs own"  on public.habit_logs  for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "reminders own"   on public.reminders   for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Friendships: kedua pihak bisa lihat; hanya pengirim yang membuat; hanya penerima yang menerima/menolak
create policy "friends read"   on public.friendships for select to authenticated
  using (requester_id = auth.uid() or addressee_id = auth.uid());
create policy "friends send"   on public.friendships for insert to authenticated
  with check (requester_id = auth.uid() and status = 'pending');
create policy "friends answer" on public.friendships for update to authenticated
  using (addressee_id = auth.uid());
create policy "friends delete" on public.friendships for delete to authenticated
  using (requester_id = auth.uid() or addressee_id = auth.uid());

-- Groups
create policy "groups read"   on public.groups for select to authenticated using (public.is_group_member(id));
create policy "groups create" on public.groups for insert to authenticated with check (owner_id = auth.uid());
create policy "groups update" on public.groups for update to authenticated using (owner_id = auth.uid());
create policy "groups delete" on public.groups for delete to authenticated using (owner_id = auth.uid());

create policy "members read"   on public.group_members for select to authenticated using (public.is_group_member(group_id));
create policy "members add"    on public.group_members for insert to authenticated with check (public.is_group_owner(group_id));
create policy "members remove" on public.group_members for delete to authenticated
  using (public.is_group_owner(group_id) or user_id = auth.uid());   -- owner mengeluarkan / anggota keluar sendiri

-- Shared schedule
create policy "sched read"   on public.shared_schedules for select to authenticated using (public.is_group_member(group_id));
create policy "sched create" on public.shared_schedules for insert to authenticated
  with check (created_by = auth.uid() and public.is_group_member(group_id));
create policy "sched update" on public.shared_schedules for update to authenticated using (created_by = auth.uid());
create policy "sched delete" on public.shared_schedules for delete to authenticated using (created_by = auth.uid());

create policy "part read"   on public.schedule_participants for select to authenticated using (public.can_see_schedule(schedule_id));
create policy "part answer" on public.schedule_participants for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());   -- ikut / tidak bisa

-- ---------- INDEX ----------
create index on public.alarms (user_id);
create index on public.alarm_logs (user_id, completed_at desc);
create index on public.habit_logs (user_id, log_date);
create index on public.reminders (user_id, starts_at);
create index on public.shared_schedules (group_id, starts_at);
create index on public.schedule_participants (user_id);

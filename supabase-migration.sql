-- =====================================================================
-- WakeUp — MIGRASI v2 (jalankan SETELAH supabase-schema.sql)
-- Cara pakai: Supabase Dashboard → SQL Editor → New query → paste → Run
-- Aman dijalankan berulang (idempotent) dan tidak menghapus data.
-- WAJIB dijalankan SEBELUM memakai versi aplikasi terbaru (alarm sync memakai kolom local_id).
-- =====================================================================

-- ---------- 1. SINKRON ALARM AMAN: kolom local_id + unique (user_id, local_id) ----------
-- Aplikasi sekarang melakukan upsert, bukan "hapus semua lalu isi ulang".
alter table public.alarms add column if not exists local_id int;
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'alarms_user_local_key') then
    alter table public.alarms add constraint alarms_user_local_key unique (user_id, local_id);
  end if;
end $$;

-- ---------- 2. KOLOM updated_at OTOMATIS ----------
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end $$;

alter table public.alarms           add column if not exists updated_at timestamptz not null default now();
alter table public.habits           add column if not exists updated_at timestamptz not null default now();
alter table public.reminders        add column if not exists updated_at timestamptz not null default now();
alter table public.shared_schedules add column if not exists updated_at timestamptz not null default now();

drop trigger if exists alarms_set_updated_at on public.alarms;
create trigger alarms_set_updated_at before update on public.alarms
  for each row execute function public.set_updated_at();
drop trigger if exists habits_set_updated_at on public.habits;
create trigger habits_set_updated_at before update on public.habits
  for each row execute function public.set_updated_at();
drop trigger if exists reminders_set_updated_at on public.reminders;
create trigger reminders_set_updated_at before update on public.reminders
  for each row execute function public.set_updated_at();
drop trigger if exists shared_schedules_set_updated_at on public.shared_schedules;
create trigger shared_schedules_set_updated_at before update on public.shared_schedules
  for each row execute function public.set_updated_at();

-- ---------- 3. ANGGOTA GRUP BARU / KELUAR → PESERTA JADWAL IKUT DISESUAIKAN ----------
-- Hanya jadwal yang belum dimulai; riwayat jadwal lama tidak diubah.
create or replace function public.invite_new_member()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.schedule_participants (schedule_id, user_id, status)
  select s.id, new.user_id, 'invited'
  from public.shared_schedules s
  where s.group_id = new.group_id and s.starts_at > now()
  on conflict do nothing;
  return new;
end $$;

drop trigger if exists on_member_added on public.group_members;
create trigger on_member_added after insert on public.group_members
  for each row execute function public.invite_new_member();

create or replace function public.uninvite_removed_member()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  delete from public.schedule_participants sp
  using public.shared_schedules s
  where sp.schedule_id = s.id and s.group_id = old.group_id
    and sp.user_id = old.user_id and s.starts_at > now();
  return old;
end $$;

drop trigger if exists on_member_removed on public.group_members;
create trigger on_member_removed after delete on public.group_members
  for each row execute function public.uninvite_removed_member();

-- ---------- 4. FRIENDSHIPS: penerima hanya boleh mengubah status ----------
create or replace function public.guard_friendship_update()
returns trigger language plpgsql as $$
begin
  if new.requester_id <> old.requester_id
     or new.addressee_id <> old.addressee_id
     or new.created_at   <> old.created_at then
    raise exception 'Hanya kolom status yang boleh diubah';
  end if;
  return new;
end $$;

drop trigger if exists friendships_guard on public.friendships;
create trigger friendships_guard before update on public.friendships
  for each row execute function public.guard_friendship_update();

drop policy if exists "friends answer" on public.friendships;
create policy "friends answer" on public.friendships for update to authenticated
  using (addressee_id = auth.uid() and status = 'pending')
  with check (addressee_id = auth.uid() and status in ('accepted','declined'));

-- ---------- 5. GRUP: owner hanya boleh menambah anggota yang sudah berteman ----------
create or replace function public.are_friends(a uuid, b uuid)
returns boolean language sql security definer stable set search_path = public as $$
  select exists (
    select 1 from public.friendships
    where status = 'accepted'
      and ((requester_id = a and addressee_id = b) or (requester_id = b and addressee_id = a)));
$$;

drop policy if exists "members add" on public.group_members;
create policy "members add" on public.group_members for insert to authenticated
  with check (public.is_group_owner(group_id) and public.are_friends(auth.uid(), user_id));

-- ---------- 6. REGISTRASI LEBIH TAHAN ERROR (username duplikat / karakter aneh) ----------
-- Username dinormalisasi (huruf kecil, hanya a-z 0-9 _ .), min 3 & maks 20 karakter.
-- Jika sudah dipakai, otomatis diberi angka di belakang (mis. dik → dik1) agar registrasi tidak gagal.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  base  text;
  uname text;
  n     int := 0;
begin
  base := lower(regexp_replace(
            coalesce(nullif(new.raw_user_meta_data->>'username', ''), split_part(new.email, '@', 1)),
            '[^a-zA-Z0-9_.]', '', 'g'));
  if length(base) < 3 then
    base := 'user' || substr(replace(new.id::text, '-', ''), 1, 6);
  end if;
  base  := left(base, 20);
  uname := base;
  while exists (select 1 from public.profiles where username = uname) loop
    n := n + 1;
    uname := left(base, 16) || n::text;
  end loop;

  insert into public.profiles (id, username, full_name)
  values (new.id, uname, new.raw_user_meta_data->>'full_name');
  return new;
end $$;

-- ---------- 7. INDEX TAMBAHAN ----------
create index if not exists friendships_addressee_idx on public.friendships (addressee_id);
create index if not exists group_members_user_idx    on public.group_members (user_id);
create index if not exists alarm_logs_alarm_idx      on public.alarm_logs (alarm_id);

-- Selesai. Cek hasilnya:
--   select conname from pg_constraint where conname = 'alarms_user_local_key';
--   select tgname  from pg_trigger   where tgname in ('on_member_added','on_member_removed','friendships_guard');

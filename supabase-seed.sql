-- =====================================================================
-- WakeUp — DATA DEMO untuk presentasi (opsional)
-- Cara pakai:
--   1. Daftar dulu satu akun lewat aplikasi (registrasi biasa).
--   2. Ganti email di baris "demo_email" di bawah dengan email akun itu.
--   3. Supabase Dashboard → SQL Editor → paste → Run.
-- Aman dijalankan ulang: tidak akan menimpa jika akun sudah punya habit.
-- Untuk demo Teman, daftarkan akun kedua lewat aplikasi, lalu kirim/terima request di tab Teman.
-- =====================================================================
do $$
declare
  demo_email text := 'GANTI_DENGAN_EMAIL_AKUN_KAMU';
  me   uuid;
  h1   uuid; h2 uuid; h3 uuid;
  grp  uuid;
  i    int;
begin
  select id into me from auth.users where email = demo_email;
  if me is null then
    raise exception 'Akun dengan email % tidak ditemukan. Daftar dulu lewat aplikasi.', demo_email;
  end if;

  insert into public.profiles (id, username) values (me, split_part(demo_email, '@', 1))
    on conflict (id) do nothing;

  if exists (select 1 from public.habits where user_id = me) then
    raise notice 'Data demo sudah ada, dilewati.';
    return;
  end if;

  -- Habit
  insert into public.habits (user_id, name, icon, target_count, unit)
    values (me, 'Minum air', '💧', 8, 'gelas') returning id into h1;
  insert into public.habits (user_id, name, icon, target_count, unit)
    values (me, 'Membaca', '📖', 10, 'halaman') returning id into h2;
  insert into public.habits (user_id, name, icon, target_count, unit, reminder_time)
    values (me, 'Olahraga', '🏃', 30, 'menit', '17:00') returning id into h3;

  -- Progres 6 hari terakhir (streak habit) + hari ini sebagian
  for i in 1..6 loop
    insert into public.habit_logs (habit_id, user_id, log_date, count)
      values (h1, me, current_date - i, 8);
    insert into public.habit_logs (habit_id, user_id, log_date, count)
      values (h2, me, current_date - i, 10);
  end loop;
  insert into public.habit_logs (habit_id, user_id, log_date, count) values (h1, me, current_date, 5);

  -- Riwayat bangun 5 hari berturut-turut (untuk streak & statistik)
  for i in 1..5 loop
    insert into public.alarm_logs (user_id, challenge_id, completed_at)
      values (me, (array[1,2,3,4,6])[i], now() - make_interval(days => i - 1));
  end loop;

  -- Reminder pribadi
  insert into public.reminders (user_id, title, category, notes, starts_at, remind_before_min) values
    (me, 'Kuliah Sistem Embedded', 'kuliah',  'Ruang Lab Elektro',      date_trunc('day', now()) + interval '1 day 8 hours', 30),
    (me, 'Kumpul laporan TA',      'tugas',   'Upload ke LMS',          date_trunc('day', now()) + interval '2 days 23 hours', 120),
    (me, 'Lari sore',              'olahraga', null,                    date_trunc('day', now()) + interval '1 day 17 hours', 10);

  -- Grup + jadwal bersama (trigger otomatis menambah owner & mengundang anggota)
  insert into public.groups (name, description, owner_id)
    values ('Kelompok TA', 'Diskusi progres Tugas Akhir', me) returning id into grp;
  insert into public.shared_schedules (group_id, created_by, title, description, starts_at, ends_at) values
    (grp, me, 'Meeting progres TA', 'Bahas hasil uji sensor', date_trunc('day', now()) + interval '2 days 10 hours', date_trunc('day', now()) + interval '2 days 11 hours');

  raise notice 'Data demo berhasil dibuat untuk %', demo_email;
end $$;

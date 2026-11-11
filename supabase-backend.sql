-- =====================================================================
-- WakeUp — BACKEND v3 (jalankan SETELAH supabase-schema.sql dan supabase-migration.sql)
-- Cara pakai: Supabase Dashboard → SQL Editor → New query → paste → Run
-- Aman dijalankan berulang (idempotent) dan tidak menghapus data.
-- =====================================================================

-- ---------- 1. alarm_logs TERHUBUNG KE alarms ----------
-- Aplikasi mengirim alarm_local_id (ID alarm di HP). Trigger mengisi alarm_id (uuid) otomatis
-- dengan mencocokkan alarms(user_id, local_id), sehingga riwayat bisa dikaitkan ke alarm-nya.
alter table public.alarm_logs add column if not exists alarm_local_id int;

create or replace function public.link_alarm_log()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.alarm_id is null and new.alarm_local_id is not null then
    select a.id into new.alarm_id
    from public.alarms a
    where a.user_id = new.user_id and a.local_id = new.alarm_local_id;
  end if;
  return new;
end $$;

drop trigger if exists alarm_logs_link on public.alarm_logs;
create trigger alarm_logs_link before insert on public.alarm_logs
  for each row execute function public.link_alarm_log();

-- ---------- 2. CEK USERNAME SEBELUM REGISTRASI ----------
-- Dipanggil aplikasi lewat /rest/v1/rpc/username_available. Perlu bisa diakses anon (belum login).
-- Hanya mengembalikan true/false, tidak membocorkan data profil.
create or replace function public.username_available(uname text)
returns boolean language sql security definer stable set search_path = public as $$
  select not exists (select 1 from public.profiles where lower(username) = lower(trim(uname)));
$$;

revoke all on function public.username_available(text) from public;
grant execute on function public.username_available(text) to anon, authenticated;

-- ---------- 3. KEAMANAN: fungsi trigger tidak boleh dipanggil langsung lewat API ----------
revoke all on function public.link_alarm_log() from public, anon, authenticated;

-- Cek hasilnya:
--   select public.username_available('nama_yang_belum_ada');   -- true
--   select tgname from pg_trigger where tgname = 'alarm_logs_link';

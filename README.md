# WakeUp ⏰

Aplikasi alarm Android yang **tidak bisa dimatikan dengan tombol biasa**. Untuk menghentikan alarm, pengguna harus menyelesaikan sebuah *challenge* memakai kamera dan sensor HP. Selain alarm, WakeUp juga menjadi aplikasi rutinitas: habit tracker, reminder & jadwal, serta fitur sosial (teman, grup, jadwal bersama).

Dibuat sebagai proyek kelompok mata kuliah pemrograman mobile, Teknik Elektro, Universitas Padjadjaran.

## Fitur

**Alarm Challenge (inti aplikasi)**
- Alarm tanpa tombol snooze; berhenti hanya setelah challenge selesai.
- Pengguna memilih **tingkat kesulitan** (Easy / Medium / Hard), challenge dipilih **acak** dari tingkat itu saat alarm bunyi.
- Challenge memakai kamera (deteksi objek dan wajah/senyum, ML Kit), accelerometer (shake, hitung langkah), dan gyroscope (putar HP).
- Streak bangun, riwayat, dan ringkasan pagi (*Morning Brief*).

**Rutinitas & sosial** (terhubung ke Supabase)
- Registrasi / login, profil pengguna.
- Habit tracker (target harian, progres, streak) dengan tab **Statistik**: persentase tercapai 7/30 hari, streak terbaik, grafik 7 hari, kalender 30 hari, tren, dan hari paling konsisten.
- Pengingat habit yang pintar: pilih hari, waktu tepat (exact alarm), dilewati otomatis jika habit hari itu sudah selesai, dan tombol **+1** langsung di notifikasi. Habit bisa **diedit** tanpa menghilangkan riwayat.
- **Streak bareng teman**: tantangan habit di dalam grup dengan leaderboard (streak, hari tercapai, progres hari ini).
- **Morning Brief** menampilkan habit pagi dengan tombol + untuk langsung dicatat.
- **Weekly Insight**: ringkasan mingguan di tab Statistik dan notifikasi Minggu 19.00 (bisa dimatikan di Pengaturan).
- **Notification Center** (ikon lonceng di beranda): friend request, undangan jadwal grup, bentrok, dan habit yang belum selesai.
- **Widget layar utama**: lihat progres habit dan catat +1 tanpa membuka aplikasi.
- Reminder & jadwal pribadi, dengan deteksi jadwal bentrok.
- Teman (cari username, kirim/terima request), grup, dan jadwal bersama (ikut / tidak bisa).
- Dashboard beranda: alarm berikutnya, habit hari ini, jadwal hari ini, kegiatan grup.

## Challenge

| Level | Contoh challenge |
|---|---|
| Easy | Tunjukkan botol · Tunjukkan wajah · Shake 10 kali · Putar HP 180° |
| Medium | Tersenyum ke kamera · Botol + senyum · Jalan 10 langkah · Putar HP + wajah |
| Hard | Jalan 10 langkah + tunjukkan sepatu · Buku + senyum + shake 5 kali |

## Teknologi

- Kotlin, Jetpack Compose (Material 3), minSdk 26 / targetSdk 34
- CameraX + ML Kit (Image Labeling, Face Detection)
- Supabase: PostgreSQL, Auth, Row Level Security, diakses lewat REST (OkHttp)
- AlarmManager + Foreground Service untuk alarm

## Cara menjalankan

1. Clone repo terbaru, checkout branch `main`, lalu buka folder `WakeUp` di Android Studio. Gunakan Gradle JDK 17; project menyediakan Gradle Wrapper 8.7 (`gradlew` / `gradlew.bat`).
2. **Siapkan Supabase** (lihat [`docs/DATABASE.md`](docs/DATABASE.md)):
   1. Buat project di [supabase.com](https://supabase.com).
   2. SQL Editor → jalankan berurutan: `supabase-schema.sql`, `supabase-migration.sql`, lalu `supabase-backend.sql`, dan terakhir `supabase-habit-upgrade.sql` (kolom hari pengingat habit + tabel tantangan grup). Skrip ini aman dijalankan ulang.
   3. (Opsional) daftar satu akun lewat aplikasi, lalu jalankan `supabase-seed.sql` untuk data demo.
   4. Authentication → Providers → Email: matikan *Confirm email* jika ingin langsung bisa login saat demo.
3. Isi `app/src/main/java/com/wakeup/Config.kt` dengan **Project URL** dan **publishable/anon key** dari Project Settings → API.
4. Tunggu Gradle Sync selesai, lalu pilih **Run** untuk menjalankan aplikasi. Untuk build APK debug dari terminal: `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`). HP fisik disarankan karena aplikasi memakai kamera dan sensor.

## Struktur

```
app/src/main/java/com/wakeup/
├── MainActivity.kt        Login, beranda, alarm, navigasi
├── ChallengeActivity.kt   Layar challenge (kamera + sensor)
├── Challenges.kt          Daftar challenge per level
├── Alarm*.kt, Boot*.kt    Penjadwalan alarm, service, receiver
├── Supa.kt                Klien Supabase (auth + REST + refresh token)
├── Repo.kt                Seluruh akses data (alarm, habit, reminder, teman, grup, jadwal, statistik)
├── SyncQueue.kt           Antrian offline untuk riwayat bangun
├── NotificationSync.kt    Pasang ulang pengingat habit & reminder (juga setelah HP restart)
├── Habit/Schedule/Social/SettingsScreen.kt
└── Ui.kt, MorningBrief.kt, Notifier.kt
docs/DATABASE.md           ERD, RLS, trigger
docs/BACKEND.md            Arsitektur backend, alur data, strategi offline
supabase-schema.sql        Skema awal
supabase-migration.sql     Perbaikan v2
supabase-backend.sql       Backend v3 (trigger alarm_logs, fungsi username_available)
supabase-habit-upgrade.sql Hari pengingat habit, tantangan grup (group_challenges, challenge_logs)
supabase-seed.sql          Data demo
```

## Database

ERD, tabel, kebijakan keamanan, dan trigger dijelaskan di [`docs/DATABASE.md`](docs/DATABASE.md). Arsitektur backend dan alur datanya ada di [`docs/BACKEND.md`](docs/BACKEND.md).

## Rencana pengembangan

- Beberapa jam pengingat per habit
- Undangan aktivitas langsung ke teman (di luar grup)

## Tim

Proyek kelompok, Teknik Elektro, Universitas Padjadjaran.

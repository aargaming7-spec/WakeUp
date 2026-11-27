package com.wakeup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Ringkasan satu minggu (7 hari terakhir) dibanding minggu sebelumnya. */
data class WeeklySummary(
    val thisRate: Float?,        // 0..1, null bila belum ada hari yang bisa dinilai
    val lastRate: Float?,
    val best: Habit?,            // habit paling konsisten minggu ini
    val weakest: Habit?,         // habit yang paling tertinggal (hanya bila < 50%)
    val longestStreak: Int
)

object WeeklyInsight {
    private const val KEY = 91001

    // ---------- perhitungan ----------
    /** Pasangan (hari tercapai, hari yang dinilai) untuk satu habit pada rentang [from, to]. */
    private fun counts(h: Habit, log: Map<LocalDate, Int>, from: LocalDate, to: LocalDate): Pair<Int, Int> {
        var hit = 0
        var active = 0
        var d = from
        val c = h.created
        while (!d.isAfter(to)) {
            if (c == null || !c.isAfter(d)) {
                active++
                if ((log[d] ?: 0) >= h.target) hit++
            }
            d = d.plusDays(1)
        }
        return Pair(hit, active)
    }

    fun compute(habits: List<Habit>, logs: Map<String, Map<LocalDate, Int>>, today: LocalDate = LocalDate.now()): WeeklySummary {
        val from = today.minusDays(6)
        val lastTo = today.minusDays(7)
        val lastFrom = today.minusDays(13)
        var thisHit = 0; var thisActive = 0; var lastHit = 0; var lastActive = 0
        val perHabit = mutableListOf<Pair<Habit, Float>>()
        habits.forEach { h ->
            val log = logs[h.id].orEmpty()
            val t = counts(h, log, from, today)
            val l = counts(h, log, lastFrom, lastTo)
            thisHit += t.first; thisActive += t.second
            lastHit += l.first; lastActive += l.second
            if (t.second > 0) perHabit.add(Pair(h, t.first.toFloat() / t.second))
        }
        val best = perHabit.filter { it.second > 0f }.maxByOrNull { it.second }?.first
        val weakest = perHabit.minByOrNull { it.second }?.takeIf { it.second < 0.5f && it.first != best }?.first
        val longest = habits.maxOfOrNull { HabitMath.currentStreak(HabitMath.doneDays(it, logs[it.id].orEmpty()), today) } ?: 0
        return WeeklySummary(
            thisRate = if (thisActive == 0) null else thisHit.toFloat() / thisActive,
            lastRate = if (lastActive == 0) null else lastHit.toFloat() / lastActive,
            best = best, weakest = weakest, longestStreak = longest)
    }

    private fun pct(f: Float): String = "${(f * 100).roundToInt()}%"

    fun lines(s: WeeklySummary): List<String> {
        val t = s.thisRate ?: return listOf("Belum ada data minggu ini.")
        val out = mutableListOf<String>()
        val l = s.lastRate
        var head = "Minggu ini ${pct(t)} habit tercapai"
        if (l != null) {
            val diff = ((t - l) * 100).roundToInt()
            head += when {
                diff > 0 -> ", naik $diff poin dari minggu lalu."
                diff < 0 -> ", turun ${-diff} poin dari minggu lalu."
                else -> ", sama seperti minggu lalu."
            }
        } else head += "."
        out.add(head)
        s.best?.let { out.add("Paling konsisten: ${it.icon} ${it.name}.") }
        s.weakest?.let { out.add("Perlu perhatian: ${it.icon} ${it.name}.") }
        if (s.longestStreak > 1) out.add("Streak terpanjang saat ini ${s.longestStreak} hari.")
        return out
    }

    fun text(s: WeeklySummary): String = lines(s).joinToString(" ")

    // ---------- penjadwalan: Minggu 19.00 ----------
    fun enabled(c: Context): Boolean = c.getSharedPreferences("wakeup", Context.MODE_PRIVATE).getBoolean("weekly_insight", true)

    private fun pi(c: Context) = PendingIntent.getBroadcast(c, KEY, Intent(c, WeeklyInsightReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Memasang (atau membatalkan, bila dimatikan di Pengaturan) notifikasi Minggu berikutnya pukul 19.00. */
    fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        if (!enabled(c)) { am.cancel(pi(c)); return }
        var d = LocalDate.now()
        var at = 0L
        for (n in 0..8) {
            at = d.atTime(19, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (d.dayOfWeek == DayOfWeek.SUNDAY && at > System.currentTimeMillis()) break
            d = d.plusDays(1)
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c))   // tidak perlu tepat waktu
    }
}

class WeeklyInsightReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        WeeklyInsight.schedule(c)   // minggu depan dulu
        if (!WeeklyInsight.enabled(c) || !Supa.loggedIn) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val habits = withTimeoutOrNull(8000) { Repo.habits() }
                if (habits != null && habits.isEmpty()) return@launch   // belum punya habit: tidak ada yang diringkas
                val text = if (habits == null) null else withTimeoutOrNull(6000) {
                    WeeklyInsight.text(WeeklyInsight.compute(habits, Repo.habitLogs(60)))
                }
                Notifier.show(c, 91001, "Ringkasan mingguanmu", text ?: "Lihat progres habit minggu ini di tab Habit > Statistik.")
            } catch (e: Exception) {
                // jaringan bermasalah: lewati minggu ini
            } finally {
                pending.finish()
            }
        }
    }
}

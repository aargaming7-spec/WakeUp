package com.wakeup

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Statistik satu habit. Semua angka dihitung dari habit_logs; "tercapai" berarti jumlah >= target harian. */
data class HabitInsight(
    val habit: Habit,
    val streak: Int,              // hari beruntun sampai hari ini (hari ini belum selesai tidak memutus streak)
    val best: Int,                // streak terbaik dalam data yang tersedia
    val rate7: Float?,            // persentase hari tercapai dalam 7 hari terakhir; null bila belum ada hari yang bisa dinilai
    val rate30: Float?,           // sama, 30 hari
    val done30: Int,              // jumlah hari tercapai dalam 30 hari
    val total30: Int,             // total jumlah (mis. gelas) dalam 30 hari
    val last30: List<Int>,        // jumlah per hari, terlama -> hari ini (selalu 30 item)
    val bestWeekday: DayOfWeek?   // hari yang paling sering tercapai (90 hari), null bila datanya belum cukup
)

/** Ringkasan semua habit untuk 7 hari terakhir. */
data class HabitOverview(
    val weekDates: List<LocalDate>,   // 7 tanggal, terlama -> hari ini
    val weekFractions: List<Float>,   // 0..1: bagian habit yang tercapai pada tiap tanggal
    val doneToday: Int,
    val totalHabits: Int,
    val longestStreak: Int,           // streak berjalan terpanjang di antara semua habit
    val rate7: Float?                 // rata-rata ketercapaian 7 hari
)

object HabitMath {
    private fun isActive(h: Habit, d: LocalDate): Boolean {
        val c = h.created
        return c == null || !c.isAfter(d)
    }

    fun doneDays(h: Habit, log: Map<LocalDate, Int>): Set<LocalDate> =
        log.filter { it.value >= h.target }.keys

    fun currentStreak(done: Set<LocalDate>, today: LocalDate): Int {
        var d = today
        if (d !in done) d = d.minusDays(1)
        var n = 0
        while (d in done) { n++; d = d.minusDays(1) }
        return n
    }

    private fun bestStreak(done: Set<LocalDate>): Int {
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (x in done.sorted()) {
            run = if (prev != null && prev.plusDays(1) == x) run + 1 else 1
            if (run > best) best = run
            prev = x
        }
        return best
    }

    /**
     * Persentase hari tercapai pada [n] hari terakhir. Hari ini baru dihitung bila sudah tercapai
     * (supaya pagi hari tidak langsung menurunkan angka), dan hari sebelum habit dibuat tidak dihitung.
     */
    private fun rate(done: Set<LocalDate>, h: Habit, today: LocalDate, n: Int): Float? {
        val end = if (today in done) today else today.minusDays(1)
        var start = end.minusDays(n - 1L)
        val c = h.created
        if (c != null && c.isAfter(start)) start = c
        if (end.isBefore(start)) return null
        val span = ChronoUnit.DAYS.between(start, end).toInt() + 1
        var hit = 0
        var d = start
        while (!d.isAfter(end)) { if (d in done) hit++; d = d.plusDays(1) }
        return hit.toFloat() / span
    }

    private fun bestWeekday(done: Set<LocalDate>, h: Habit, today: LocalDate): DayOfWeek? {
        var start = today.minusDays(89)
        val c = h.created
        if (c != null && c.isAfter(start)) start = c
        val occ = IntArray(8)
        val hit = IntArray(8)
        var d = start
        while (!d.isAfter(today)) {
            val k = d.dayOfWeek.value
            occ[k]++
            if (d in done) hit[k]++
            d = d.plusDays(1)
        }
        var best: DayOfWeek? = null
        var bestRate = 0f
        for (k in 1..7) {
            if (occ[k] >= 3 && hit[k] > 0) {
                val r = hit[k].toFloat() / occ[k]
                if (r > bestRate) { bestRate = r; best = DayOfWeek.of(k) }
            }
        }
        return best
    }

    fun insight(h: Habit, log: Map<LocalDate, Int>, today: LocalDate = LocalDate.now()): HabitInsight {
        val done = doneDays(h, log)
        val last30 = (29 downTo 0).map { log[today.minusDays(it.toLong())] ?: 0 }
        return HabitInsight(
            habit = h,
            streak = currentStreak(done, today),
            best = bestStreak(done),
            rate7 = rate(done, h, today, 7),
            rate30 = rate(done, h, today, 30),
            done30 = (0..29).count { today.minusDays(it.toLong()) in done },
            total30 = last30.sum(),
            last30 = last30,
            bestWeekday = bestWeekday(done, h, today))
    }

    fun overview(habits: List<Habit>, logs: Map<String, Map<LocalDate, Int>>, today: LocalDate = LocalDate.now()): HabitOverview {
        val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
        var activeSum = 0
        var doneSum = 0
        val fractions = dates.map { d ->
            val active = habits.filter { isActive(it, d) }
            val ok = active.count { h -> (logs[h.id]?.get(d) ?: 0) >= h.target }
            activeSum += active.size
            doneSum += ok
            if (active.isEmpty()) 0f else ok.toFloat() / active.size
        }
        val longest = habits.maxOfOrNull { currentStreak(doneDays(it, logs[it.id].orEmpty()), today) } ?: 0
        return HabitOverview(
            weekDates = dates,
            weekFractions = fractions,
            doneToday = habits.count { it.today >= it.target },
            totalHabits = habits.size,
            longestStreak = longest,
            rate7 = if (activeSum == 0) null else doneSum.toFloat() / activeSum)
    }
}

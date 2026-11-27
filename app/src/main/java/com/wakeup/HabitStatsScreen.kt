package com.wakeup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

private val DAY_SHORT = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
private val DAY_LONG = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

private fun pct(f: Float?): String = if (f == null) "–" else "${(f * 100).roundToInt()}%"

/** Tab "Statistik" di halaman Habit: ringkasan, grafik 7 hari, dan detail tiap habit. */
@Composable
fun HabitStatsPane() {
    var habits by remember { mutableStateOf<List<Habit>>(emptyList()) }
    var logs by remember { mutableStateOf<Map<String, Map<LocalDate, Int>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val exec = rememberRunner { error = it; loading = false }
    LaunchedEffect(Unit) {
        exec {
            habits = Repo.habits()
            logs = Repo.habitLogs()
            error = null
            loading = false
        }
    }

    val today = LocalDate.now()
    val insights = remember(habits, logs) { habits.map { HabitMath.insight(it, logs[it.id].orEmpty(), today) } }
    val overview = remember(habits, logs) { HabitMath.overview(habits, logs, today) }
    val weekly = remember(habits, logs) { WeeklyInsight.lines(WeeklyInsight.compute(habits, logs, today)) }
    val w = MaterialTheme.wake

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ErrorText(error)
        if (loading) Text("Memuat statistik...", color = Muted)
        else if (habits.isEmpty()) EmptyState("Belum ada data", "Tambahkan habit dan catat progresmu beberapa hari, statistiknya akan muncul di sini.")
        else {
            // Ringkasan
            Card2 {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    SummaryCell("${overview.doneToday}/${overview.totalHabits}", "tercapai hari ini", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    SummaryCell(pct(overview.rate7), "rata-rata 7 hari", Modifier.weight(1f).padding(start = 16.dp))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    SummaryCell("${overview.longestStreak}", "streak terpanjang", Modifier.weight(1f).padding(start = 16.dp))
                }
            }

            // Ringkasan mingguan (sama dengan notifikasi Minggu malam)
            Card2(color = w.dawn) {
                Text("Weekly Insight", style = MaterialTheme.typography.titleMedium, color = w.sunText)
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    weekly.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink) }
                }
            }

            // Grafik 7 hari
            Card2 {
                Text("7 hari terakhir", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text("Bagian habit yang tercapai tiap hari", style = MaterialTheme.typography.labelMedium, color = Muted)
                Row(Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    overview.weekDates.forEachIndexed { i, d ->
                        val f = overview.weekFractions[i]
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.width(22.dp).fillMaxHeight(f.coerceAtLeast(0.04f)).clip(RoundedCornerShape(6.dp))
                                    .background(if (f >= 1f) w.moss else if (f > 0f) w.sun else MaterialTheme.colorScheme.surfaceVariant))
                            }
                            Text(DAY_SHORT[d.dayOfWeek.value - 1], Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                                color = if (d == today) Ink else Muted)
                        }
                    }
                }
            }

            // Detail per habit
            SectionLabel("Per habit")
            insights.forEach { i -> HabitInsightCard(i) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SummaryCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Ink)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
    }
}

@Composable
private fun HabitInsightCard(i: HabitInsight) {
    val w = MaterialTheme.wake
    val h = i.habit
    Card2 {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(w.dawn), contentAlignment = Alignment.Center) {
                Text(h.icon, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(h.name, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text("Target ${h.target} ${h.unit} per hari", style = MaterialTheme.typography.labelMedium, color = Muted)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCell("${i.streak}", "hari beruntun", Modifier.weight(1f))
            SummaryCell("${i.best}", "streak terbaik", Modifier.weight(1f))
            SummaryCell("${i.total30}", if (h.unit.isBlank()) "total 30 hari" else "${h.unit} / 30 hari", Modifier.weight(1f))
        }

        RateRow("7 hari", i.rate7, Modifier.padding(top = 14.dp))
        RateRow("30 hari", i.rate30, Modifier.padding(top = 8.dp))

        Text("30 hari terakhir (kiri = lama, kanan = hari ini)", Modifier.padding(top = 14.dp, bottom = 6.dp),
            style = MaterialTheme.typography.labelSmall, color = Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            i.last30.forEach { c ->
                Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(3.dp)).background(
                    if (c >= h.target) w.moss else if (c > 0) w.sun.copy(alpha = .5f) else MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        Text("${i.done30} dari 30 hari tercapai", Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelMedium, color = Muted)

        val notes = insightNotes(i)
        if (notes.isNotEmpty()) Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Ink) }
        }
    }
}

@Composable
private fun RateRow(label: String, rate: Float?, modifier: Modifier = Modifier) {
    val w = MaterialTheme.wake
    Column(modifier) {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Muted)
            Text(pct(rate), style = MaterialTheme.typography.labelLarge, color = Ink)
        }
        Bar(rate ?: 0f, if ((rate ?: 0f) >= 0.8f) w.moss else w.sun, Modifier.padding(top = 4.dp), 6.dp)
    }
}

/** Kalimat singkat hasil pembacaan data: tren dan hari paling konsisten. */
private fun insightNotes(i: HabitInsight): List<String> {
    val notes = mutableListOf<String>()
    val r7 = i.rate7
    val r30 = i.rate30
    if (r7 != null && r30 != null) {
        notes += when {
            r7 >= r30 + 0.1f -> "Tren naik: 7 hari terakhir lebih baik dari rata-rata 30 hari."
            r7 <= r30 - 0.1f -> "Tren turun: 7 hari terakhir di bawah rata-rata 30 hari."
            else -> "Tren stabil dibanding rata-rata 30 hari."
        }
    }
    val bw: DayOfWeek? = i.bestWeekday
    if (bw != null) notes += "Paling konsisten di hari ${DAY_LONG[bw.value - 1]}."
    if (i.streak == 0 && i.best > 0) notes += "Streak sedang putus. Streak terbaikmu ${i.best} hari, ayo mulai lagi."
    return notes
}

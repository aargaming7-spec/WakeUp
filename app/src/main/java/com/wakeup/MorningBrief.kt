package com.wakeup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate

/** Ringkasan pagi yang muncul setelah challenge selesai dan alarm berhenti. */
@Composable
fun MorningBrief(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var habits by remember { mutableStateOf<List<Habit>>(emptyList()) }
    var agenda by remember { mutableStateOf<List<AgendaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(Supa.loggedIn) }
    val exec = rememberRunner { }
    LaunchedEffect(Unit) {
        if (Supa.loggedIn) {
            try { habits = Repo.habits(); agenda = Repo.agenda() } catch (e: Exception) { } finally { loading = false }
        }
    }
    val today = LocalDate.now()
    val items = agenda.filter { it.start.local().toLocalDate() == today && !it.done && it.status != "declined" }
    val w = MaterialTheme.wake

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(w.night)) {
            SunMark(Modifier.matchParentSize(), cx = 1f, cy = 1f, scale = .5f)
            Column(Modifier.padding(24.dp)) {
                Text(fmtDate(Instant.now()).uppercase(), style = MaterialTheme.typography.labelSmall, color = w.onNightSoft)
                Text("Kamu sudah bangun", style = MaterialTheme.typography.headlineMedium, color = w.onNight)
                Spacer(Modifier.height(14.dp))
                Text("${History.streak(ctx)}", style = MaterialTheme.typography.displayMedium, color = w.onNight)
                Text("hari berturut-turut", color = w.onNightSoft)
            }
        }
        Card2 {
            Text("Jadwal hari ini", style = MaterialTheme.typography.titleMedium, color = Ink)
            when {
                loading -> Text("Memuat...", color = Muted)
                !Supa.loggedIn -> Text("Masuk ke akunmu untuk melihat jadwal dan habit di sini.", color = Muted)
                items.isEmpty() -> Text("Tidak ada jadwal hari ini.", color = Muted)
            }
            Column(Modifier.padding(top = 6.dp)) {
                items.forEach { TimelineRow(fmtTime(it.start), it.title, if (it.conflict) "Bentrok dengan jadwal lain" else null,
                    if (it.conflict) w.amber else if (it.kind == "group") w.group else w.sun) }
            }
        }
        if (habits.isNotEmpty()) Card2 {
            Text("Mulai harimu dengan habit", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text("Ketuk + untuk mencatat habit yang sudah kamu lakukan pagi ini.", style = MaterialTheme.typography.labelMedium, color = Muted)
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                habits.forEach { h ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${h.icon} ${h.name}", Modifier.weight(1f), color = Ink)
                            Text("${h.today}/${h.target} ${h.unit}", style = MaterialTheme.typography.labelMedium, color = Muted)
                            if (h.today < h.target) {
                                Spacer(Modifier.width(8.dp))
                                RoundButton("+", "Tambah ${h.name}", {
                                    exec { Repo.setHabitCount(h.id, h.today + 1); habits = Repo.habits() }
                                }, filled = true)
                            }
                        }
                        Bar(h.today.toFloat() / h.target, if (h.today >= h.target) w.moss else w.sun, Modifier.padding(top = 5.dp), 5.dp)
                    }
                }
            }
        }
        SunButton("Mulai hari ini", onClose)
    }
}

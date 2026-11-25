package com.wakeup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
            Text("Habit hari ini", style = MaterialTheme.typography.titleMedium, color = Ink)
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                habits.forEach { h ->
                    Column {
                        Row {
                            Text(h.name, Modifier.weight(1f), color = Ink)
                            Text("${h.today}/${h.target} ${h.unit}", style = MaterialTheme.typography.labelMedium, color = Muted)
                        }
                        Bar(h.today.toFloat() / h.target, if (h.today >= h.target) w.moss else w.sun, Modifier.padding(top = 5.dp), 5.dp)
                    }
                }
            }
        }
        SunButton("Mulai hari ini", onClose)
    }
}

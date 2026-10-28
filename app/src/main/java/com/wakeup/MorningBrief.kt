package com.wakeup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    val soft = Color(0xFF9AA3B8)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Selamat pagi", style = MaterialTheme.typography.headlineMedium)
        Text(fmtDate(Instant.now()), color = soft)
        Card2(color = Color(0xFF171D2E)) {
            Text("Streak bangun", color = soft)
            Text("${History.streak(ctx)} hari berturut-turut", style = MaterialTheme.typography.titleLarge)
        }
        Text("Jadwal hari ini", style = MaterialTheme.typography.titleSmall)
        when {
            loading -> Text("Memuat...", color = soft)
            !Supa.loggedIn -> Text("Masuk ke akunmu untuk melihat jadwal dan habit di sini.", color = soft)
            items.isEmpty() -> Text("Tidak ada jadwal hari ini.", color = soft)
        }
        items.forEach { Text("${fmtTime(it.start)}  ${it.title}" + if (it.conflict) "  (bentrok)" else "") }
        if (habits.isNotEmpty()) {
            Text("Habit hari ini", style = MaterialTheme.typography.titleSmall)
            habits.forEach { Text("${it.name}: ${it.today}/${it.target} ${it.unit}", color = soft) }
        }
        Button(onClose, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Mulai hari ini") }
    }
}

package com.wakeup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

private val CATEGORIES = listOf("kuliah", "rapat", "tugas", "olahraga", "belajar", "lainnya")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen() {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<AgendaItem>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var weekly by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val exec = rememberRunner { error = it }
    LaunchedEffect(tick) { exec { items = Repo.agenda(); error = null } }

    val today = LocalDate.now()
    val shown = items.filter {
        val d = it.start.local().toLocalDate()
        if (weekly) !d.isBefore(today) && d.isBefore(today.plusDays(7)) else d == today
    }
    val conflicts = shown.count { it.conflict } / 2

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Jadwal", style = MaterialTheme.typography.headlineMedium, color = Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            FilterChip(!weekly, { weekly = false }, { Text("Hari ini") })
            FilterChip(weekly, { weekly = true }, { Text("Minggu ini") })
        }
        if (conflicts > 0) Text("⚠ $conflicts jadwal saling bertabrakan", color = Color(0xFFD9822B), style = MaterialTheme.typography.labelLarge)
        ErrorText(error)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (shown.isEmpty()) Text("Tidak ada jadwal.", Modifier.padding(8.dp))
            shown.groupBy { it.start.local().toLocalDate() }.forEach { (_, day) ->
                Text(fmtDate(day.first().start), style = MaterialTheme.typography.titleSmall, color = Ink, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                day.forEach { it2 ->
                    Card2(Modifier.padding(vertical = 4.dp), if (it2.conflict) Color(0xFFFFF4E0) else Color.White) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (it2.kind == "reminder") Checkbox(it2.done, { c -> exec { Repo.setReminderDone(it2.refId, c); tick++ } })
                            Column(Modifier.weight(1f)) {
                                Text("${fmtTime(it2.start)}–${fmtTime(it2.end)}  ${it2.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    textDecoration = if (it2.done || it2.status == "declined") androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                                Text((if (it2.kind == "group") "👥 " else "🔔 ") + it2.sub + if (it2.conflict) "  ⚠ bentrok" else "",
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            if (it2.kind == "reminder") TextButton({
                                exec { Notifier.cancel(ctx, it2.refId.hashCode()); Repo.deleteReminder(it2.refId); tick++ }
                            }) { Text("Hapus") }
                        }
                        if (it2.kind == "group") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(it2.status == "going", { exec { Repo.answerSchedule(it2.refId, true); tick++ } }, { Text("Ikut") })
                            FilterChip(it2.status == "declined", { exec { Repo.answerSchedule(it2.refId, false); tick++ } }, { Text("Tidak bisa") })
                        }
                    }
                }
            }
        }
        Button({ adding = true }, Modifier.fillMaxWidth()) { Text("+ Tambah Reminder") }
    }

    if (adding) {
        var title by remember { mutableStateOf("") }
        var cat by remember { mutableStateOf("kuliah") }
        var notes by remember { mutableStateOf("") }
        var before by remember { mutableIntStateOf(10) }
        var whenAt by remember { mutableStateOf<Instant?>(null) }
        var picking by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { adding = false },
            confirmButton = {
                TextButton({
                    val at = whenAt!!
                    exec {
                        val id = Repo.addReminder(title.trim(), cat, notes.trim(), at, before)
                        Notifier.schedule(ctx, id.hashCode(), at.toEpochMilli() - before * 60_000L, "🔔 ${title.trim()}",
                            "Mulai pukul ${fmtTime(at)} (${cat})")
                        tick++
                    }
                    adding = false
                }, enabled = title.isNotBlank() && whenAt != null) { Text("Simpan") }
            },
            dismissButton = { TextButton({ adding = false }) { Text("Batal") } },
            title = { Text("Reminder Baru") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Judul") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CATEGORIES.forEach { c -> FilterChip(cat == c, { cat = c }, { Text(c) }) }
                    }
                    OutlinedTextField(notes, { notes = it }, label = { Text("Catatan (opsional)") })
                    OutlinedButton({ picking = true }) { Text(whenAt?.let { fmtDateTime(it) } ?: "Pilih tanggal & jam") }
                    Text("Ingatkan sebelum:", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 30, 60).forEach { m -> FilterChip(before == m, { before = m }, { Text("$m mnt") }) }
                    }
                }
            })
        if (picking) DateTimePickerDialog({ picking = false }) { whenAt = it; picking = false }
    }
}

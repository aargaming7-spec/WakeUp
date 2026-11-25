package com.wakeup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

    val w = MaterialTheme.wake
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader("Jadwal", overline = if (weekly) "7 hari ke depan" else fmtDate(Instant.now()))
        Segmented(listOf("Hari ini", "Minggu ini"), if (weekly) 1 else 0, { weekly = it == 1 }, Modifier.padding(bottom = 10.dp))
        if (conflicts > 0) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(w.amberBg).padding(12.dp)) {
            Text("$conflicts jadwal saling bertabrakan", color = w.amber, style = MaterialTheme.typography.labelLarge)
        }
        ErrorText(error)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (shown.isEmpty()) EmptyState("Tidak ada jadwal", "Tambahkan reminder, atau tunggu undangan dari grupmu.")
            shown.groupBy { it.start.local().toLocalDate() }.forEach { (_, day) ->
                SectionLabel(fmtDate(day.first().start), Modifier.padding(top = 14.dp, bottom = 6.dp))
                day.forEach { it2 ->
                    val accent = if (it2.conflict) w.amber else if (it2.kind == "group") w.group else w.sun
                    Card2(Modifier.padding(vertical = 4.dp), if (it2.conflict) w.amberBg else Color.Unspecified) {
                        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(52.dp)) {
                                Text(fmtTime(it2.start), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = Ink)
                                Text(fmtTime(it2.end), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Muted)
                            }
                            Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(accent))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(it2.title, style = MaterialTheme.typography.titleSmall, color = Ink,
                                    textDecoration = if (it2.done || it2.status == "declined") androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                                Text((if (it2.kind == "group") "Grup · " else "Reminder · ") + it2.sub + if (it2.conflict) " · bentrok" else "",
                                    style = MaterialTheme.typography.labelMedium, color = if (it2.conflict) w.amber else Muted)
                            }
                            if (it2.kind == "reminder") {
                                Checkbox(it2.done, { c -> exec { Repo.setReminderDone(it2.refId, c); tick++ } },
                                    colors = CheckboxDefaults.colors(checkedColor = w.sun, checkmarkColor = w.onSun))
                                TextButton({
                                    exec { Notifier.cancel(ctx, it2.refId.hashCode()); Repo.deleteReminder(it2.refId); tick++ }
                                }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                    Text("Hapus", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        if (it2.kind == "group") Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(it2.status == "going", { exec { Repo.answerSchedule(it2.refId, true); tick++ } }, { Text("Ikut") })
                            FilterChip(it2.status == "declined", { exec { Repo.answerSchedule(it2.refId, false); tick++ } }, { Text("Tidak bisa") })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SunButton("Tambah reminder", { adding = true })
        Spacer(Modifier.height(12.dp))
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
                        Notifier.schedule(ctx, id.hashCode(), at.toEpochMilli() - before * 60_000L, "Reminder: ${title.trim()}",
                            "Mulai pukul ${fmtTime(at)} (${cat})")
                        tick++
                    }
                    adding = false
                }, enabled = title.isNotBlank() && whenAt != null) { Text("Simpan") }
            },
            dismissButton = { TextButton({ adding = false }) { Text("Batal") } },
            title = { Text("Reminder baru") },
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

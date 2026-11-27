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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitScreen() {
    val ctx = LocalContext.current
    var habits by remember { mutableStateOf<List<Habit>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    val exec = rememberRunner { error = it }
    var pane by remember { mutableIntStateOf(0) }   // 0 = Hari ini, 1 = Statistik

    LaunchedEffect(tick) {
        exec {
            habits = Repo.habits(); error = null
            habits.forEach { h ->   // pasang ulang pengingat (aman dipanggil berulang); batalkan bila pengingat dimatikan
                val key = ("h" + h.id).hashCode()
                val r = h.reminder
                if (r != null) Notifier.scheduleHabit(ctx, key, h.id, r, h.reminderDays, "${h.icon} ${h.name}", "Waktunya ${h.name}!")
                else Notifier.cancel(ctx, key)
            }
        }
    }

    val w = MaterialTheme.wake
    val done = habits.count { it.today >= it.target }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader("Habit", overline = "$done dari ${habits.size} tercapai hari ini")
        Segmented(listOf("Hari ini", "Statistik"), pane, { pane = it }, Modifier.padding(bottom = 10.dp))
        if (pane == 1) Box(Modifier.weight(1f)) { HabitStatsPane() } else {
            ErrorText(error)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (habits.isEmpty()) EmptyState("Belum ada habit", "Mulai dari satu kebiasaan kecil, misalnya minum air.")
                habits.forEach { h ->
                    val full = h.today >= h.target
                    Card2 {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(w.dawn), contentAlignment = Alignment.Center) {
                                Text(h.icon, fontSize = 22.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.name, style = MaterialTheme.typography.titleMedium, color = Ink)
                                Text("${h.today}/${h.target} ${h.unit}" + (h.reminder?.let { " · ingatkan ${it.take(5)}" } ?: "") + daysLabel(h.reminderDays),
                                    style = MaterialTheme.typography.labelMedium, color = Muted)
                            }
                            if (h.streak > 0) Tag("${h.streak} hari", w.dawn, w.sunText)
                        }
                        Bar(h.today.toFloat() / h.target, if (full) w.moss else w.sun, Modifier.padding(vertical = 14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RoundButton("−", "Kurangi ${h.name}", { exec { Repo.setHabitCount(h.id, (h.today - 1).coerceAtLeast(0)); tick++ } })
                            Spacer(Modifier.width(8.dp))
                            RoundButton("+", "Tambah ${h.name}", { exec { Repo.setHabitCount(h.id, h.today + 1); tick++ } }, filled = true)
                            Spacer(Modifier.weight(1f))
                            h.week.forEach { on ->
                                Box(Modifier.padding(start = 4.dp).size(9.dp).clip(CircleShape)
                                    .background(if (on) w.sun else MaterialTheme.colorScheme.surfaceVariant))
                            }
                            TextButton({ editing = h }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("Ubah", color = Ink, style = MaterialTheme.typography.labelLarge)
                            }
                            TextButton({ exec { Notifier.cancel(ctx, ("h" + h.id).hashCode()); Repo.deleteHabit(h.id); tick++ } },
                                contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("Hapus", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SunButton("Tambah habit", { adding = true })
            Spacer(Modifier.height(12.dp))
        }
    }

    if (adding) HabitDialog(null,
        onSave = { n, ic, t, u, time, ds -> exec { Repo.addHabit(n, ic, t, u, time, ds); tick++ }; adding = false },
        onDismiss = { adding = false })
    editing?.let { cur ->
        HabitDialog(cur,
            onSave = { n, ic, t, u, time, ds ->
                exec { Notifier.cancel(ctx, ("h" + cur.id).hashCode()); Repo.updateHabit(cur.id, n, ic, t, u, time, ds); tick++ }
                editing = null
            },
            onDismiss = { editing = null })
    }
}

/** " · Sen Rab Jum" bila pengingat hanya di hari tertentu; kosong bila setiap hari. */
private fun daysLabel(days: List<Int>): String =
    if (days.isEmpty() || days.size == 7) "" else " · " + DAYS.filter { it.second in days }.joinToString(" ") { it.first }

/** Dialog tambah (initial = null) atau ubah habit. onSave(nama, ikon, target, satuan, jamPengingat?, hariPengingat). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitDialog(initial: Habit?, onSave: (String, String, Int, String, String?, List<Int>) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "💧") }
    var target by remember { mutableStateOf((initial?.target ?: 8).toString()) }
    var unit by remember { mutableStateOf(initial?.unit ?: "gelas") }
    var remind by remember { mutableStateOf(initial?.reminder != null) }
    val parts = initial?.reminder?.take(5)?.split(":")
    val ts = rememberTimePickerState(parts?.getOrNull(0)?.toIntOrNull() ?: 8, parts?.getOrNull(1)?.toIntOrNull() ?: 0, true)
    var days by remember {
        mutableStateOf(if (initial == null || initial.reminderDays.isEmpty()) DAYS.map { it.second }.toSet() else initial.reminderDays.toSet())
    }
    val icons = remember { (listOf("💧", "📚", "🏃", "✏️", "🧘", "🍎", "😴") + listOfNotNull(initial?.icon)).distinct() }

    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                val t = target.toIntOrNull() ?: 1
                val time = if (remind) "%02d:%02d".format(ts.hour, ts.minute) else null
                onSave(name.trim(), icon, t.coerceAtLeast(1), unit.trim(), time,
                    if (days.size == 7) emptyList() else days.toList().sorted())
            }, enabled = name.isNotBlank() && (!remind || days.isNotEmpty())) { Text("Simpan") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Batal") } },
        title = { Text(if (initial == null) "Habit baru" else "Ubah habit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama (mis. Minum air)") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    icons.forEach { e -> FilterChip(icon == e, { icon = e }, { Text(e) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Target/hari") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(unit, { unit = it }, label = { Text("Satuan") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pengingat", Modifier.weight(1f)); Switch(remind, { remind = it }, colors = wakeSwitchColors())
                }
                if (remind) {
                    TimePicker(ts)
                    Text("Ulangi di hari:", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DAYS.forEach { (n, d) -> FilterChip(d in days, { days = if (d in days) days - d else days + d }, { Text(n) }) }
                    }
                    Text("Notifikasi dilewati bila habit hari itu sudah tercapai.", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        })
}

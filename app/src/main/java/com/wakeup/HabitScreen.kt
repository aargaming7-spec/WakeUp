package com.wakeup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    val exec = rememberRunner { error = it }

    LaunchedEffect(tick) {
        exec {
            habits = Repo.habits(); error = null
            habits.forEach { h ->   // pasang ulang pengingat harian (aman dipanggil berulang)
                h.reminder?.let { Notifier.scheduleDaily(ctx, ("h" + h.id).hashCode(), it, "${h.icon} ${h.name}", "Waktunya ${h.name}!") }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Habit Tracker", style = MaterialTheme.typography.headlineMedium, color = Ink)
        Text("${habits.count { it.today >= it.target }} dari ${habits.size} tercapai hari ini", style = MaterialTheme.typography.labelLarge)
        ErrorText(error)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            if (habits.isEmpty()) Text("Belum ada habit. Tambahkan yang pertama!")
            habits.forEach { h ->
                Card2(Modifier.padding(vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(h.icon, fontSize = 28.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.name, style = MaterialTheme.typography.titleMedium)
                            Text("${h.today}/${h.target} ${h.unit}" + (h.reminder?.let { " • ⏰ ${it.take(5)}" } ?: ""),
                                style = MaterialTheme.typography.labelMedium)
                        }
                        Text("🔥 ${h.streak}")
                    }
                    LinearProgressIndicator(progress = { (h.today.toFloat() / h.target).coerceIn(0f, 1f) },
                        Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton({ exec { Repo.setHabitCount(h.id, (h.today - 1).coerceAtLeast(0)); tick++ } }) { Text("−1") }
                        Spacer(Modifier.width(8.dp))
                        Button({ exec { Repo.setHabitCount(h.id, h.today + 1); tick++ } }) { Text("+1") }
                        Spacer(Modifier.weight(1f))
                        Text("7 hari: " + h.week.joinToString("") { if (it) "●" else "○" }, style = MaterialTheme.typography.labelMedium)
                        TextButton({ exec { Notifier.cancel(ctx, ("h" + h.id).hashCode()); Repo.deleteHabit(h.id); tick++ } }) { Text("Hapus") }
                    }
                }
            }
        }
        Button({ adding = true }, Modifier.fillMaxWidth()) { Text("+ Tambah Habit") }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var icon by remember { mutableStateOf("💧") }
        var target by remember { mutableStateOf("8") }
        var unit by remember { mutableStateOf("gelas") }
        var remind by remember { mutableStateOf(false) }
        val ts = rememberTimePickerState(8, 0, true)
        AlertDialog(onDismissRequest = { adding = false },
            confirmButton = {
                TextButton({
                    val t = target.toIntOrNull() ?: 1
                    val time = if (remind) "%02d:%02d".format(ts.hour, ts.minute) else null
                    exec { Repo.addHabit(name.trim(), icon, t.coerceAtLeast(1), unit.trim(), time); tick++ }
                    adding = false
                }, enabled = name.isNotBlank()) { Text("Simpan") }
            },
            dismissButton = { TextButton({ adding = false }) { Text("Batal") } },
            title = { Text("Habit Baru") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nama (mis. Minum air)") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("💧", "📚", "🏃", "✏️", "🧘", "🍎", "😴").forEach { e -> FilterChip(icon == e, { icon = e }, { Text(e) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Target/hari") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(unit, { unit = it }, label = { Text("Satuan") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pengingat harian", Modifier.weight(1f)); Switch(remind, { remind = it })
                    }
                    if (remind) TimePicker(ts)
                }
            })
    }
}

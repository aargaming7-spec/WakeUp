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
    val exec = rememberRunner { error = it }

    LaunchedEffect(tick) {
        exec {
            habits = Repo.habits(); error = null
            habits.forEach { h ->   // pasang ulang pengingat harian (aman dipanggil berulang)
                h.reminder?.let { Notifier.scheduleDaily(ctx, ("h" + h.id).hashCode(), it, "${h.icon} ${h.name}", "Waktunya ${h.name}!") }
            }
        }
    }

    val w = MaterialTheme.wake
    val done = habits.count { it.today >= it.target }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader("Habit", overline = "$done dari ${habits.size} tercapai hari ini")
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
                            Text("${h.today}/${h.target} ${h.unit}" + (h.reminder?.let { " · ingatkan ${it.take(5)}" } ?: ""),
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
                        TextButton({ exec { Notifier.cancel(ctx, ("h" + h.id).hashCode()); Repo.deleteHabit(h.id); tick++ } },
                            contentPadding = PaddingValues(horizontal = 10.dp)) {
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
            title = { Text("Habit baru") },
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
                        Text("Pengingat harian", Modifier.weight(1f)); Switch(remind, { remind = it }, colors = wakeSwitchColors())
                    }
                    if (remind) TimePicker(ts)
                }
            })
    }
}

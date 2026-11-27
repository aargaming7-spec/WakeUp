package com.wakeup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private fun Profile.shortName(): String = fullName?.takeIf { it.isNotBlank() } ?: "@$username"

/** Satu anggota di papan peringkat tantangan. */
private data class Standing(val member: Member, val today: Int, val streak: Int, val daysDone: Int)

/**
 * Bagian "Tantangan bareng" di halaman grup: semua anggota mengerjakan habit yang sama dan saling melihat
 * progres hari ini, streak, dan jumlah hari tercapai (60 hari terakhir).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChallengeSection(group: Group, members: List<Member>) {
    val me = Supa.userId
    var challenges by remember { mutableStateOf<List<GroupChallenge>>(emptyList()) }
    var logs by remember { mutableStateOf<List<ChallengeLog>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val exec = rememberRunner { error = it }
    LaunchedEffect(tick, group.id) {
        exec {
            challenges = Repo.challenges(group.id)
            logs = Repo.challengeLogs(challenges.map { it.id })
            error = null
        }
    }
    val today = LocalDate.now()

    SectionLabel("Tantangan bareng")
    ErrorText(error)
    if (challenges.isEmpty()) Text("Belum ada tantangan. Buat satu supaya anggota grup saling memantau streak.",
        style = MaterialTheme.typography.bodyMedium, color = Muted)
    challenges.forEach { ch ->
        val mine = logs.filter { it.challengeId == ch.id }
        val standings = members.map { m ->
            val byDate = mine.filter { it.userId == m.profile.id }.associate { it.date to it.count }
            val done = byDate.filter { it.value >= ch.target }.keys
            Standing(m, byDate[today] ?: 0, HabitMath.currentStreak(done, today), done.size)
        }.sortedWith(compareByDescending<Standing> { it.streak }.thenByDescending { it.daysDone }.thenByDescending { it.today })
        val myToday = standings.firstOrNull { it.member.profile.id == me }?.today ?: 0

        Card2 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ch.icon, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ch.name, style = MaterialTheme.typography.titleMedium, color = Ink)
                    Text("Target ${ch.target} ${ch.unit} per hari", style = MaterialTheme.typography.labelMedium, color = Muted)
                }
                if (ch.createdBy == me || group.ownerId == me)
                    TextButton({ exec { Repo.deleteChallenge(ch.id); tick++ } }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Hapus", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
            }
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                standings.forEachIndexed { i, s ->
                    val mineRow = s.member.profile.id == me
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", Modifier.width(22.dp), style = MaterialTheme.typography.titleSmall, color = Muted)
                        Text(s.member.profile.shortName() + if (mineRow) " (kamu)" else "", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Text("${s.today}/${ch.target}", style = MaterialTheme.typography.labelMedium,
                            color = if (s.today >= ch.target) MaterialTheme.wake.moss else Muted)
                        Spacer(Modifier.width(8.dp))
                        Tag("${s.streak} hari", MaterialTheme.wake.dawn, MaterialTheme.wake.sunText)
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Progresmu hari ini: $myToday/${ch.target}", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, color = Ink)
                RoundButton("−", "Kurangi ${ch.name}", { exec { Repo.setChallengeCount(ch.id, (myToday - 1).coerceAtLeast(0)); tick++ } })
                Spacer(Modifier.width(8.dp))
                RoundButton("+", "Tambah ${ch.name}", { exec { Repo.setChallengeCount(ch.id, myToday + 1); tick++ } }, filled = true)
            }
        }
    }
    OutlinedButton({ creating = true }) { Text("Buat tantangan") }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var icon by remember { mutableStateOf("🔥") }
        var target by remember { mutableStateOf("1") }
        var unit by remember { mutableStateOf("kali") }
        AlertDialog(onDismissRequest = { creating = false },
            confirmButton = {
                TextButton({
                    exec { Repo.createChallenge(group.id, name.trim(), icon, (target.toIntOrNull() ?: 1).coerceAtLeast(1), unit.trim()); tick++ }
                    creating = false
                }, enabled = name.isNotBlank()) { Text("Buat") }
            },
            dismissButton = { TextButton({ creating = false }) { Text("Batal") } },
            title = { Text("Tantangan baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nama (mis. Lari pagi)") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("🔥", "💧", "🏃", "📚", "🧘", "😴").forEach { e -> FilterChip(icon == e, { icon = e }, { Text(e) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text("Target/hari") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(unit, { unit = it }, label = { Text("Satuan") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Text("Semua anggota grup bisa ikut dan melihat streak satu sama lain.", style = MaterialTheme.typography.labelSmall)
                }
            })
    }
}

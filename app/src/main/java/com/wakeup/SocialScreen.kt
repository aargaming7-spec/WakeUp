package com.wakeup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.Instant

private fun Profile.label() = if (fullName.isNullOrBlank()) "@$username" else "$fullName (@$username)"

@Composable
fun SocialScreen() {
    var sub by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Teman & Grup", style = MaterialTheme.typography.headlineMedium, color = Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            FilterChip(sub == 0, { sub = 0 }, { Text("Teman") })
            FilterChip(sub == 1, { sub = 1 }, { Text("Grup") })
        }
        if (sub == 0) FriendsPane() else GroupsPane()
    }
}

// ======================= TEMAN =======================
@Composable
fun FriendsPane() {
    var friends by remember { mutableStateOf<List<Friendship>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val exec = rememberRunner { error = it }
    LaunchedEffect(tick) { exec { friends = Repo.friendships(); error = null } }

    val incoming = friends.filter { it.status == "pending" && it.incoming }
    val sent = friends.filter { it.status == "pending" && !it.incoming }
    val accepted = friends.filter { it.status == "accepted" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ErrorText(error)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("Cari username") }, singleLine = true, modifier = Modifier.weight(1f))
            Button({ exec { results = Repo.searchUsers(query.trim()) } }, enabled = query.isNotBlank()) { Text("Cari") }
        }
        results.forEach { p ->
            val known = friends.any { it.other.id == p.id && it.status != "declined" }
            Card2(color = Color(0xFFF1F2F5)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.label(), Modifier.weight(1f))
                    Button({ exec { Repo.sendRequest(p.id); results = results.filter { it.id != p.id }; tick++ } }, enabled = !known) {
                        Text(if (known) "Sudah" else "Tambah")
                    }
                }
            }
        }
        if (incoming.isNotEmpty()) Text("Permintaan masuk", style = MaterialTheme.typography.titleSmall)
        incoming.forEach { f ->
            Card2 {
                Text(f.other.label())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ exec { Repo.answerRequest(f.id, true); tick++ } }) { Text("Terima") }
                    OutlinedButton({ exec { Repo.answerRequest(f.id, false); tick++ } }) { Text("Tolak") }
                }
            }
        }
        Text("Teman (${accepted.size})", style = MaterialTheme.typography.titleSmall)
        if (accepted.isEmpty()) Text("Belum ada teman. Cari username temanmu di atas.", style = MaterialTheme.typography.bodySmall)
        accepted.forEach { f ->
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.other.label(), Modifier.weight(1f))
                    TextButton({ exec { Repo.removeFriendship(f.id); tick++ } }) { Text("Hapus") }
                }
            }
        }
        if (sent.isNotEmpty()) Text("Menunggu jawaban", style = MaterialTheme.typography.titleSmall)
        sent.forEach { f ->
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.other.label(), Modifier.weight(1f))
                    TextButton({ exec { Repo.removeFriendship(f.id); tick++ } }) { Text("Batalkan") }
                }
            }
        }
    }
}

// ======================= GRUP =======================
@Composable
fun GroupsPane() {
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var open by remember { mutableStateOf<Group?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val exec = rememberRunner { error = it }
    LaunchedEffect(tick) { exec { groups = Repo.groups(); error = null } }

    val g = open
    if (g != null) { GroupDetail(g, onBack = { open = null; tick++ }); return }

    Column(Modifier.fillMaxSize()) {
        ErrorText(error)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (groups.isEmpty()) Text("Belum ada grup. Buat grup untuk kelompok tugas, belajar, atau kelas.")
            groups.forEach { gr ->
                Card2 {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("👥 ${gr.name}", style = MaterialTheme.typography.titleMedium)
                            if (!gr.description.isNullOrBlank()) Text(gr.description, style = MaterialTheme.typography.bodySmall)
                        }
                        Button({ open = gr }) { Text("Buka") }
                    }
                }
            }
        }
        Button({ creating = true }, Modifier.fillMaxWidth()) { Text("+ Buat Grup") }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { creating = false },
            confirmButton = { TextButton({
                exec { Repo.createGroup(name.trim(), desc.trim()); tick++ }; creating = false
            }, enabled = name.isNotBlank()) { Text("Buat") } },
            dismissButton = { TextButton({ creating = false }) { Text("Batal") } },
            title = { Text("Grup Baru") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama grup") }, singleLine = true)
                OutlinedTextField(desc, { desc = it }, label = { Text("Deskripsi (opsional)") })
            } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupDetail(group: Group, onBack: () -> Unit) {
    val me = Supa.userId
    val isOwner = group.ownerId == me
    var members by remember { mutableStateOf<List<Member>>(emptyList()) }
    var friends by remember { mutableStateOf<List<Friendship>>(emptyList()) }
    var schedules by remember { mutableStateOf<List<SharedSchedule>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var addingMember by remember { mutableStateOf(false) }
    var addingSchedule by remember { mutableStateOf(false) }
    val exec = rememberRunner { error = it }
    LaunchedEffect(tick) {
        exec {
            members = Repo.groupMembers(group.id)
            schedules = Repo.sharedSchedules().filter { it.groupId == group.id }
            if (isOwner) friends = Repo.friendships().filter { it.status == "accepted" }
            error = null
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("← Kembali") }
            Text(group.name, style = MaterialTheme.typography.titleLarge)
        }
        if (!group.description.isNullOrBlank()) Text(group.description)
        ErrorText(error)

        Text("Anggota (${members.size})", style = MaterialTheme.typography.titleSmall)
        members.forEach { m ->
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.profile.label() + if (m.role == "owner") "  👑" else "", Modifier.weight(1f))
                    if (isOwner && m.role != "owner")
                        TextButton({ exec { Repo.removeMember(group.id, m.profile.id); tick++ } }) { Text("Keluarkan") }
                }
            }
        }
        if (isOwner) OutlinedButton({ addingMember = true }) { Text("+ Tambah anggota dari teman") }

        Text("Jadwal Bersama", style = MaterialTheme.typography.titleSmall)
        if (schedules.isEmpty()) Text("Belum ada jadwal.", style = MaterialTheme.typography.bodySmall)
        schedules.forEach { s ->
            Card2(color = Color(0xFFF1F2F5)) {
                Text(s.title, style = MaterialTheme.typography.titleSmall)
                Text(fmtDateTime(s.startsAt) + (s.endsAt?.let { " – " + fmtTime(it) } ?: ""), style = MaterialTheme.typography.labelMedium)
                if (!s.description.isNullOrBlank()) Text(s.description, style = MaterialTheme.typography.bodySmall)
                Text("✅ ${s.going} ikut  •  ⏳ ${s.invited} diundang  •  ❌ ${s.declined} tidak bisa", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(s.myStatus == "going", { exec { Repo.answerSchedule(s.id, true); tick++ } }, { Text("Ikut") })
                    FilterChip(s.myStatus == "declined", { exec { Repo.answerSchedule(s.id, false); tick++ } }, { Text("Tidak bisa") })
                    if (s.createdBy == me) TextButton({ exec { Repo.deleteSchedule(s.id); tick++ } }) { Text("Hapus") }
                }
            }
        }
        Button({ addingSchedule = true }) { Text("+ Jadwal Bersama") }

        Spacer(Modifier.height(8.dp))
        if (isOwner) TextButton({ exec { Repo.deleteGroup(group.id); onBack() } }) {
            Text("Hapus grup", color = MaterialTheme.colorScheme.error)
        } else TextButton({ exec { Repo.removeMember(group.id, me ?: ""); onBack() } }) {
            Text("Keluar dari grup", color = MaterialTheme.colorScheme.error)
        }
    }

    if (addingMember) {
        val candidates = friends.map { it.other }.filter { f -> members.none { it.profile.id == f.id } }
        AlertDialog(onDismissRequest = { addingMember = false },
            confirmButton = { TextButton({ addingMember = false }) { Text("Selesai") } },
            title = { Text("Tambah Anggota") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                if (candidates.isEmpty()) Text("Semua temanmu sudah di grup, atau kamu belum punya teman.")
                candidates.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.label(), Modifier.weight(1f))
                        TextButton({ exec { Repo.addMember(group.id, p.id); tick++ } }) { Text("Tambah") }
                    }
                }
            } })
    }

    if (addingSchedule) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var start by remember { mutableStateOf<Instant?>(null) }
        var minutes by remember { mutableIntStateOf(60) }
        var picking by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { addingSchedule = false },
            confirmButton = { TextButton({
                val st = start!!
                exec { Repo.createSchedule(group.id, title.trim(), desc.trim(), st, st.plusSeconds(minutes * 60L)); tick++ }
                addingSchedule = false
            }, enabled = title.isNotBlank() && start != null) { Text("Buat") } },
            dismissButton = { TextButton({ addingSchedule = false }) { Text("Batal") } },
            title = { Text("Jadwal Bersama") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Judul (meeting, kelas, belajar bareng)") })
                OutlinedTextField(desc, { desc = it }, label = { Text("Deskripsi (opsional)") })
                OutlinedButton({ picking = true }) { Text(start?.let { fmtDateTime(it) } ?: "Pilih tanggal & jam") }
                Text("Durasi:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 60, 90, 120).forEach { m -> FilterChip(minutes == m, { minutes = m }, { Text("$m mnt") }) }
                }
                Text("Semua anggota grup otomatis diundang.", style = MaterialTheme.typography.labelSmall)
            } })
        if (picking) DateTimePickerDialog({ picking = false }) { start = it; picking = false }
    }
}

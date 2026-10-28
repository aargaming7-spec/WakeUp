package com.wakeup

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

val Muted = Color(0xFF6B7280)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { WakeTheme { Surface(Modifier.fillMaxSize()) { Root() } } }
    }
}

@Composable
fun Root() {
    var showAuth by remember { mutableStateOf(!Supa.loggedIn) }
    if (showAuth) AuthScreen { showAuth = false }
    else Shell(onOpenAuth = { showAuth = true }, onLogout = { Supa.signOut(); showAuth = true })
}

@Composable
fun Shell(onOpenAuth: () -> Unit, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        perms.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACTIVITY_RECOGNITION))
    }
    val tabs = listOf("Beranda" to R.drawable.ic_nav_home, "Alarm" to R.drawable.ic_nav_alarm,
        "Habit" to R.drawable.ic_nav_habit, "Jadwal" to R.drawable.ic_nav_jadwal, "Teman" to R.drawable.ic_nav_teman)
    Scaffold(bottomBar = {
        NavigationBar(containerColor = Color.White) {
            tabs.forEachIndexed { i, (name, icon) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i },
                    icon = { Icon(painterResource(icon), contentDescription = name) },
                    label = { Text(name, fontSize = 11.sp) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(onGo = { tab = it })
                1 -> AlarmScreen()
                5 -> SettingsScreen(onBack = { tab = 0 }, onLogout = onLogout, onOpenAuth = onOpenAuth)
                else -> if (!Supa.loggedIn) NeedLogin(onOpenAuth) else when (tab) {
                    2 -> HabitScreen()
                    3 -> ScheduleScreen()
                    else -> SocialScreen()
                }
            }
        }
    }
}

// ======================= LOGIN / REGISTRASI =======================
@Composable
fun AuthScreen(onDone: () -> Unit) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val exec = rememberRunner { msg = it }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Ink)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(16.dp))
        Text("WakeUp", style = MaterialTheme.typography.headlineMedium)
        Text("Bangun dengan tujuan, setiap hari.", color = Muted)
        Spacer(Modifier.height(20.dp))
        if (!Config.configured) {
            Text("Isi SUPABASE_URL dan SUPABASE_ANON_KEY di Config.kt dulu.", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        if (register) {
            OutlinedTextField(fullName, { fullName = it }, label = { Text("Nama lengkap") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it.trim().lowercase() }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("Password (min. 6 karakter)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        ErrorText(msg)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            busy = true; msg = null
            exec {
                try {
                    if (register) {
                        if (username.length < 3) throw ApiException("Username minimal 3 karakter")
                        val ok = Supa.signUp(email, pass, username, fullName)
                        if (ok) onDone() else { msg = "Akun dibuat. Cek email untuk konfirmasi, lalu masuk."; register = false }
                    } else { Supa.signIn(email, pass); onDone() }
                } finally { busy = false }
            }
        }, enabled = !busy && email.isNotEmpty() && pass.length >= 6, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Memproses..." else if (register) "Daftar" else "Masuk")
        }
        TextButton({ register = !register; msg = null }, Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (register) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar")
        }
        TextButton(onDone, Modifier.align(Alignment.CenterHorizontally)) { Text("Lewati dulu (alarm saja)") }
    }
}

// ======================= BERANDA =======================
@Composable
fun HomeScreen(onGo: (Int) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf<String?>(null) }
    var habits by remember { mutableStateOf<List<Habit>>(emptyList()) }
    var agenda by remember { mutableStateOf<List<AgendaItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val exec = rememberRunner { error = it }
    LaunchedEffect(Unit) {
        if (Supa.loggedIn) exec { name = Repo.myProfile()?.username; habits = Repo.habits(); agenda = Repo.agenda() }
    }

    val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
    val next = AlarmStore.load(ctx).filter { it.enabled }
        .minByOrNull { ((it.hour * 60 + it.minute) - nowMin + 1440) % 1440 }
    val today = LocalDate.now()
    val todayItems = agenda.filter { it.start.local().toLocalDate() == today && !it.done && it.status != "declined" }
    val upcomingGroup = agenda.filter { it.kind == "group" && it.start.isAfter(Instant.now()) && it.status != "declined" }.take(3)
    val doneHabits = habits.count { it.today >= it.target }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Selamat pagi", color = Muted)
                Text(name ?: "WakeUp", style = MaterialTheme.typography.headlineMedium)
            }
            TextButton({ onGo(5) }) { Text("Pengaturan") }
        }
        ErrorText(error)
        Card2 {
            Text("Alarm berikutnya", color = Muted, style = MaterialTheme.typography.labelLarge)
            Text(next?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "--:--", style = MaterialTheme.typography.displayMedium)
            Text(next?.let { LEVELS[it.level] + ", tantangan acak, tanpa snooze" } ?: "Belum ada alarm aktif. Buat di tab Alarm.", color = Muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card2(Modifier.weight(1f)) { Text("Streak bangun", color = Muted); Text("${History.streak(ctx)} hari", style = MaterialTheme.typography.titleLarge) }
            Card2(Modifier.weight(1f)) { Text("Total bangun", color = Muted); Text("${History.total(ctx)} kali", style = MaterialTheme.typography.titleLarge) }
        }
        if (!Supa.loggedIn) {
            Card2 {
                Text("Masuk untuk memakai habit tracker, jadwal, teman, dan grup.")
                TextButton({ onGo(5) }) { Text("Buka pengaturan akun") }
            }
        } else {
            Card2 {
                Text("Habit hari ini", style = MaterialTheme.typography.titleSmall)
                if (habits.isEmpty()) Text("Belum ada habit. Tambahkan di tab Habit.", color = Muted) else {
                    Text("$doneHabits dari ${habits.size} tercapai", color = Muted)
                    LinearProgressIndicator(progress = { doneHabits.toFloat() / habits.size }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    habits.forEach { Text("${it.name}: ${it.today}/${it.target} ${it.unit}", style = MaterialTheme.typography.bodySmall) }
                }
            }
            Card2 {
                Text("Jadwal hari ini", style = MaterialTheme.typography.titleSmall)
                if (todayItems.isEmpty()) Text("Tidak ada jadwal hari ini.", color = Muted)
                todayItems.forEach { Text("${fmtTime(it.start)}  ${it.title}${if (it.conflict) "  (bentrok)" else ""}") }
            }
            Card2 {
                Text("Kegiatan grup mendatang", style = MaterialTheme.typography.titleSmall)
                if (upcomingGroup.isEmpty()) Text("Belum ada kegiatan grup.", color = Muted)
                upcomingGroup.forEach { Text("${it.title}, ${it.sub}\n${fmtDateTime(it.start)}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

// ======================= ALARM =======================
private val DAYS = listOf("Sen" to 2, "Sel" to 3, "Rab" to 4, "Kam" to 5, "Jum" to 6, "Sab" to 7, "Min" to 1)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmDialog(initial: Alarm?, onSave: (Alarm) -> Unit, onDismiss: () -> Unit) {
    val ts = rememberTimePickerState(initial?.hour ?: 6, initial?.minute ?: 0, true)
    var level by remember { mutableIntStateOf(initial?.level ?: 0) }
    var days by remember {
        mutableStateOf(if (initial == null || initial.days.isEmpty()) DAYS.map { it.second }.toSet() else initial.days.toSet())
    }
    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton({
            onSave(Alarm(initial?.id ?: (System.currentTimeMillis() % 1_000_000).toInt(), ts.hour, ts.minute, level,
                initial?.enabled ?: true, if (days.size == 7) emptyList() else days.toList()))
        }, enabled = days.isNotEmpty()) { Text("Simpan") } },
        dismissButton = { TextButton(onDismiss) { Text("Batal") } },
        title = { Text(if (initial == null) "Tambah alarm" else "Ubah alarm") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            TimePicker(ts)
            Text("Ulangi")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DAYS.forEach { (n, d) -> FilterChip(d in days, { days = if (d in days) days - d else days + d }, { Text(n) }) } }
            Spacer(Modifier.height(8.dp))
            Text("Tingkat kesulitan")
            Text("Saat alarm bunyi, satu challenge dipilih acak dari tingkat ini.", style = MaterialTheme.typography.labelSmall, color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LEVELS.forEachIndexed { i, n -> FilterChip(level == i, { level = i }, { Text("$n (${poolFor(i).size})") }) } }
            Spacer(Modifier.height(8.dp))
            Text("Kemungkinan challenge:", style = MaterialTheme.typography.labelLarge)
            poolFor(level).forEach { c -> Text("• ${c.title}", style = MaterialTheme.typography.bodySmall) }
        } })
}

@Composable
fun AlarmScreen() {
    val ctx = LocalContext.current
    var alarms by remember { mutableStateOf(AlarmStore.load(ctx)) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val exec = rememberRunner { error = "Sinkron cloud gagal: $it" }

    fun update(l: List<Alarm>) {
        alarms = l; AlarmStore.save(ctx, l)
        if (Supa.loggedIn) exec { Repo.pushAlarms(l); error = null }
    }
    LaunchedEffect(Unit) {
        if (Supa.loggedIn && alarms.isEmpty()) exec {
            val remote = Repo.pullAlarms()
            if (remote.isNotEmpty()) { alarms = remote; AlarmStore.save(ctx, remote) }
        }
    }
    val week = History.week(ctx)

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Alarm", style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            DAYS.forEachIndexed { i, (n, _) ->
                Text(n + if (week[i]) " ✓" else "", style = MaterialTheme.typography.labelSmall, color = if (week[i]) Ink else Muted)
            }
        }
        ErrorText(error)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            alarms.forEach { a ->
                Card2(Modifier.padding(vertical = 5.dp).clickable { editing = a }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("%02d:%02d".format(a.hour, a.minute), style = MaterialTheme.typography.headlineMedium)
                            Text("${LEVELS[a.level]}, challenge acak (${poolFor(a.level).size} pilihan)", color = Muted)
                            Text(if (a.days.isEmpty() || a.days.size == 7) "Setiap hari"
                                else DAYS.filter { it.second in a.days }.joinToString(" ") { it.first },
                                style = MaterialTheme.typography.labelMedium, color = Muted)
                        }
                        TextButton({ AlarmStore.cancel(ctx, a); update(alarms.filter { it.id != a.id }) }) { Text("Hapus") }
                        Switch(a.enabled, { on -> update(alarms.map { if (it.id == a.id) it.copy(enabled = on) else it }) })
                    }
                }
            }
            if (alarms.isEmpty()) Text("Belum ada alarm. Tekan Tambah alarm untuk membuat yang pertama.", Modifier.padding(8.dp), color = Muted)
            else Text("Ketuk alarm untuk mengubahnya.", Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        Button({ adding = true }, Modifier.fillMaxWidth()) { Text("Tambah alarm") }
        TextButton({ testing = true }, Modifier.align(Alignment.CenterHorizontally)) { Text("Coba challenge") }
    }

    if (adding) AlarmDialog(null, onSave = { update(alarms + it); adding = false }, onDismiss = { adding = false })
    editing?.let { cur ->
        AlarmDialog(cur, onSave = { n -> update(alarms.map { if (it.id == cur.id) n else it }); editing = null }, onDismiss = { editing = null })
    }

    if (testing) {
        AlertDialog(onDismissRequest = { testing = false },
            confirmButton = { TextButton({ testing = false }) { Text("Tutup") } },
            title = { Text("Coba challenge") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Pilih challenge. Alarm tidak berbunyi dan tidak masuk riwayat.", style = MaterialTheme.typography.labelSmall, color = Muted)
                LEVELS.forEachIndexed { lv, name ->
                    Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    CHALLENGES.filter { it.level == lv }.forEach { c ->
                        TextButton({
                            testing = false
                            ctx.startActivity(Intent(ctx, ChallengeActivity::class.java).putExtra("cid", c.id).putExtra("test", true))
                        }) { Text(c.title) }
                    }
                }
            } })
    }
}

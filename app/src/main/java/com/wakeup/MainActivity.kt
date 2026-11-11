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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { WakeTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root() } } }
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
    val w = MaterialTheme.wake
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, (name, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(painterResource(icon), contentDescription = name) },
                        label = { Text(name, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Ink, selectedTextColor = Ink, indicatorColor = w.dawn,
                            unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
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
    val w = MaterialTheme.wake

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Hero malam dengan matahari terbit di sudut
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)).background(w.night)) {
            SunMark(Modifier.matchParentSize(), cx = 1f, cy = 1f, scale = .55f)
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 64.dp, bottom = 36.dp)) {
                Image(painterResource(R.drawable.ic_moon), null, Modifier.size(52.dp))
                Spacer(Modifier.height(14.dp))
                Text("WakeUp", style = MaterialTheme.typography.displaySmall, color = w.onNight)
                Text("Bangun dengan tujuan, setiap hari.", color = w.onNightSoft)
            }
        }
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (register) "Buat akunmu" else "Selamat datang kembali", style = MaterialTheme.typography.headlineSmall, color = Ink)
            if (!Config.configured) {
                Text("Isi SUPABASE_URL dan SUPABASE_ANON_KEY di Config.kt dulu.", color = MaterialTheme.colorScheme.error)
            }
            if (register) {
                OutlinedTextField(fullName, { fullName = it }, label = { Text("Nama lengkap") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(username, { username = it.trim().lowercase() }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pass, { pass = it }, label = { Text("Password (min. 6 karakter)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            ErrorText(msg)
            Spacer(Modifier.height(4.dp))
            SunButton(if (busy) "Memproses..." else if (register) "Daftar" else "Masuk", {
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
            }, enabled = !busy && email.isNotEmpty() && pass.length >= 6)
            TextButton({ register = !register; msg = null }, Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (register) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar", color = Ink)
            }
            TextButton(onDone, Modifier.align(Alignment.CenterHorizontally)) { Text("Lewati dulu, pakai alarm saja", color = Muted) }
        }
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
    val w = MaterialTheme.wake

    val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
    val next = AlarmStore.load(ctx).filter { it.enabled }
        .minByOrNull { ((it.hour * 60 + it.minute) - nowMin + 1440) % 1440 }
    val diff = next?.let { ((it.hour * 60 + it.minute) - nowMin + 1440) % 1440 }
    val today = LocalDate.now()
    val todayItems = agenda.filter { it.start.local().toLocalDate() == today && !it.done && it.status != "declined" }
    val upcomingGroup = agenda.filter { it.kind == "group" && it.start.isAfter(Instant.now()) && it.status != "declined" }.take(3)
    val doneHabits = habits.count { it.today >= it.target }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader(name ?: "WakeUp", overline = greeting() + "  ·  " + fmtDate(Instant.now()), trailing = {
            Box(Modifier.size(44.dp).clip(CircleShape).background(w.dawn).clickable { onGo(5) }
                .semantics { contentDescription = "Pengaturan" }, contentAlignment = Alignment.Center) {
                Text((name ?: "W").take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = w.sunText)
            }
        })
        ErrorText(error)

        // Hero: alarm berikutnya
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(w.night)) {
            SunMark(Modifier.matchParentSize(), cx = 1f, cy = 1f, scale = .5f)
            Column(Modifier.padding(24.dp)) {
                Text("ALARM BERIKUTNYA", style = MaterialTheme.typography.labelSmall, color = w.onNightSoft)
                Text(next?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "--:--",
                    style = MaterialTheme.typography.displayLarge, color = w.onNight)
                Text(when {
                    next == null -> "Belum ada alarm aktif"
                    diff == 0 -> "Berbunyi sebentar lagi"
                    else -> "Berbunyi dalam ${diff!! / 60} j ${diff % 60} m"
                }, color = w.onNightSoft, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(14.dp))
                if (next != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(LEVELS[next.level], w.dawn, w.sunText)
                    Tag("Tanpa snooze", Color.White.copy(alpha = .12f), w.onNight)
                } else Box(Modifier.clip(RoundedCornerShape(50)).background(w.sun).clickable { onGo(1) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)) {
                    Text("Buat alarm", style = MaterialTheme.typography.labelLarge, color = w.onSun)
                }
            }
        }

        // Statistik: dua angka besar, dipisah garis tipis
        Card2 {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Column(Modifier.weight(1f)) {
                    Text("${History.streak(ctx)}", style = MaterialTheme.typography.displaySmall, color = Ink)
                    Text("hari beruntun", color = Muted, style = MaterialTheme.typography.labelLarge)
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                Column(Modifier.weight(1f).padding(start = 20.dp)) {
                    Text("${History.total(ctx)}", style = MaterialTheme.typography.displaySmall, color = Ink)
                    Text("total bangun", color = Muted, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (!Supa.loggedIn) {
            Card2(color = w.dawn) {
                Text("Buka semua fitur", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text("Habit tracker, jadwal, teman, dan grup butuh akun.", color = Muted)
                TextButton({ onGo(5) }, contentPadding = PaddingValues(0.dp)) { Text("Buka pengaturan akun", color = w.sunText) }
            }
        } else {
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(if (habits.isEmpty()) 0f else doneHabits.toFloat() / habits.size, 64.dp, 7.dp, w.sun) {
                        Text("$doneHabits/${habits.size}", style = MaterialTheme.typography.titleSmall, color = Ink)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Habit hari ini", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text(if (habits.isEmpty()) "Belum ada habit. Tambahkan di tab Habit." else "$doneHabits dari ${habits.size} tercapai",
                            color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (habits.isNotEmpty()) Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    habits.take(4).forEach { h ->
                        Column {
                            Row {
                                Text(h.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Ink)
                                Text("${h.today}/${h.target} ${h.unit}", style = MaterialTheme.typography.labelMedium, color = Muted)
                            }
                            Bar(h.today.toFloat() / h.target, if (h.today >= h.target) w.moss else w.sun,
                                Modifier.padding(top = 5.dp), 5.dp)
                        }
                    }
                }
            }
            Card2 {
                Text("Jadwal hari ini", style = MaterialTheme.typography.titleMedium, color = Ink)
                if (todayItems.isEmpty()) Text("Tidak ada jadwal. Hari yang lapang.", color = Muted, modifier = Modifier.padding(top = 4.dp))
                Column(Modifier.padding(top = 6.dp)) {
                    todayItems.forEach { TimelineRow(fmtTime(it.start), it.title, if (it.conflict) "Bentrok dengan jadwal lain" else null,
                        if (it.conflict) w.amber else if (it.kind == "group") w.group else w.sun) }
                }
            }
            if (upcomingGroup.isNotEmpty()) Card2 {
                Text("Kegiatan grup mendatang", style = MaterialTheme.typography.titleMedium, color = Ink)
                Column(Modifier.padding(top = 6.dp)) {
                    upcomingGroup.forEach { TimelineRow(fmtTime(it.start), it.title, "${it.sub} · ${fmtDate(it.start)}", w.group) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Satu baris linimasa: jam, penanda warna, judul. */
@Composable
fun TimelineRow(time: String, title: String, sub: String?, accent: Color) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 6.dp)) {
        Text(time, Modifier.width(52.dp), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = Ink)
        Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(accent))
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Ink)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = Muted)
        }
    }
}

// ======================= ALARM =======================
val DAYS = listOf("Sen" to 2, "Sel" to 3, "Rab" to 4, "Kam" to 5, "Jum" to 6, "Sab" to 7, "Min" to 1)

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
        dismissButton = { TextButton(onDismiss) { Text("Batal", color = Muted) } },
        title = { Text(if (initial == null) "Alarm baru" else "Ubah alarm") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TimePicker(ts)
            SectionLabel("Ulangi")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DAYS.forEach { (n, d) -> FilterChip(d in days, { days = if (d in days) days - d else days + d }, { Text(n) }) } }
            SectionLabel("Tingkat kesulitan")
            Text("Saat alarm bunyi, satu challenge dipilih acak dari tingkat ini.", style = MaterialTheme.typography.labelMedium, color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LEVELS.forEachIndexed { i, n -> FilterChip(level == i, { level = i }, { Text("$n (${poolFor(i).size})") }) } }
            SectionLabel("Kemungkinan challenge")
            poolFor(level).forEach { c -> Text("• ${c.title}", style = MaterialTheme.typography.bodyMedium) }
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
    val w = MaterialTheme.wake

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

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader("Alarm", overline = "${alarms.count { it.enabled }} aktif")
        // Riwayat minggu ini
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            DAYS.forEachIndexed { i, (n, _) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(30.dp).clip(CircleShape)
                        .background(if (week[i]) w.sun else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center) {
                        Text(n.take(1), style = MaterialTheme.typography.labelLarge, color = if (week[i]) w.onSun else Muted)
                    }
                    Text(n, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = Muted)
                }
            }
        }
        ErrorText(error)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            alarms.forEach { a ->
                Card2(Modifier.clickable { editing = a }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("%02d:%02d".format(a.hour, a.minute), Modifier.weight(1f),
                            style = MaterialTheme.typography.displaySmall, color = if (a.enabled) Ink else Muted)
                        Switch(a.enabled, { on -> update(alarms.map { if (it.id == a.id) it.copy(enabled = on) else it }) },
                            colors = wakeSwitchColors())
                    }
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LevelTag(a.level)
                        Text(if (a.days.isEmpty() || a.days.size == 7) "Setiap hari"
                            else DAYS.filter { it.second in a.days }.joinToString(" ") { it.first },
                            Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Muted)
                        TextButton({ AlarmStore.cancel(ctx, a); update(alarms.filter { it.id != a.id }) },
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Hapus", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (alarms.isEmpty()) EmptyState("Belum ada alarm", "Buat alarm pertamamu. Untuk mematikannya, kamu harus bergerak.")
            else Text("Ketuk alarm untuk mengubahnya.", Modifier.padding(4.dp), style = MaterialTheme.typography.labelMedium, color = Muted)
        }
        Spacer(Modifier.height(10.dp))
        SunButton("Tambah alarm", { adding = true })
        TextButton({ testing = true }, Modifier.align(Alignment.CenterHorizontally)) { Text("Coba challenge", color = Muted) }
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
                Text("Alarm tidak berbunyi dan tidak masuk riwayat.", style = MaterialTheme.typography.labelMedium, color = Muted)
                LEVELS.forEachIndexed { lv, _ ->
                    Row(Modifier.padding(top = 12.dp)) { LevelTag(lv) }
                    CHALLENGES.filter { it.level == lv }.forEach { c ->
                        TextButton({
                            testing = false
                            ctx.startActivity(Intent(ctx, ChallengeActivity::class.java).putExtra("cid", c.id).putExtra("test", true))
                        }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(c.title, color = Ink) }
                    }
                }
            } })
    }
}

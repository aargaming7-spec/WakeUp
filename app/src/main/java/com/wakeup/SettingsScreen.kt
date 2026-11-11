package com.wakeup

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit, onOpenAuth: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("wakeup", Context.MODE_PRIVATE) }
    var profile by remember { mutableStateOf<Profile?>(null) }
    var fullName by remember { mutableStateOf("") }
    var forceVolume by remember { mutableStateOf(prefs.getBoolean("force_volume", true)) }
    var msg by remember { mutableStateOf<String?>(null) }
    val exec = rememberRunner { msg = it }
    LaunchedEffect(Unit) {
        if (Supa.loggedIn) exec { profile = Repo.myProfile(); fullName = profile?.fullName ?: "" }
    }

    val w = MaterialTheme.wake
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onBack, contentPadding = PaddingValues(0.dp)) { Text("← Kembali", color = Muted) }
        ScreenHeader("Pengaturan")

        Card2 {
            SectionLabel("Akun")
            if (Supa.loggedIn) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(profile?.username ?: "W", 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("@${profile?.username ?: ""}", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text(Supa.email ?: "", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(fullName, { fullName = it }, label = { Text("Nama lengkap") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                Spacer(Modifier.height(10.dp))
                SunButton("Simpan nama", { exec { Repo.updateFullName(fullName.trim()); msg = "Nama tersimpan." } })
                if (msg != null) Text(msg!!, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = Muted)
            } else {
                Text("Kamu belum masuk.", Modifier.padding(vertical = 8.dp), color = Muted)
                SunButton("Masuk atau daftar", onOpenAuth)
            }
        }

        Card2 {
            SectionLabel("Alarm")
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Volume alarm selalu maksimal", style = MaterialTheme.typography.titleSmall, color = Ink)
                    Text("Volume dikembalikan ke maksimal tiap detik selama alarm bunyi.", style = MaterialTheme.typography.labelMedium, color = Muted)
                }
                Spacer(Modifier.width(12.dp))
                Switch(forceVolume, { forceVolume = it; prefs.edit().putBoolean("force_volume", it).apply() }, colors = wakeSwitchColors())
            }
        }

        if (Supa.loggedIn) OutlinedButton(onLogout, Modifier.fillMaxWidth().height(52.dp)) {
            Text("Keluar", color = MaterialTheme.colorScheme.error)
        }
        Text("WakeUp 1.0", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

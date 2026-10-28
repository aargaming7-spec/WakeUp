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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onBack) { Text("← Kembali") }
        Text("Pengaturan", style = MaterialTheme.typography.headlineMedium)

        Card2 {
            Text("Akun", style = MaterialTheme.typography.titleSmall)
            if (Supa.loggedIn) {
                Text("@${profile?.username ?: ""}")
                Text(Supa.email ?: "", color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(fullName, { fullName = it }, label = { Text("Nama lengkap") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Button({ exec { Repo.updateFullName(fullName.trim()); msg = "Nama tersimpan." } }, Modifier.padding(top = 8.dp)) { Text("Simpan nama") }
                if (msg != null) Text(msg!!, style = MaterialTheme.typography.labelMedium, color = Muted)
            } else {
                Text("Kamu belum masuk.", color = Muted)
                Button(onOpenAuth, Modifier.padding(top = 8.dp)) { Text("Masuk atau daftar") }
            }
        }

        Card2 {
            Text("Alarm", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Volume alarm selalu maksimal")
                    Text("Volume dikembalikan ke maksimal tiap detik selama alarm bunyi.", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                Switch(forceVolume, { forceVolume = it; prefs.edit().putBoolean("force_volume", it).apply() })
            }
        }

        if (Supa.loggedIn) OutlinedButton(onLogout, Modifier.fillMaxWidth()) { Text("Keluar") }
        Text("WakeUp 1.0", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

package com.wakeup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Notification Center: daftar hal yang menunggu perhatian (permintaan teman, undangan jadwal, jadwal bentrok,
 * reminder hari ini, habit belum tercapai). Diketuk = pindah ke tab terkait.
 */
@Composable
fun NotificationCenterScreen(onBack: () -> Unit, onGo: (Int) -> Unit) {
    var items by remember { mutableStateOf<List<NotifItem>>(emptyList()) }
    var loading by remember { mutableStateOf(Supa.loggedIn) }
    var error by remember { mutableStateOf<String?>(null) }
    val exec = rememberRunner { error = it; loading = false }
    LaunchedEffect(Unit) {
        if (Supa.loggedIn) exec { items = Repo.notificationItems(); error = null; loading = false }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onBack, contentPadding = PaddingValues(0.dp)) { Text("← Kembali", color = Muted) }
        ScreenHeader("Notifikasi", overline = if (items.isEmpty()) null else "${items.size} perlu perhatian")
        ErrorText(error)
        if (!Supa.loggedIn) Text("Masuk ke akunmu untuk melihat notifikasi.", color = Muted)
        else if (loading) Text("Memuat...", color = Muted)
        else if (items.isEmpty()) EmptyState("Semua beres", "Tidak ada permintaan teman, undangan jadwal, atau habit yang menunggu.")
        items.forEach { n ->
            Card2(Modifier.clickable { onGo(n.tab) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.icon, fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(n.title, style = MaterialTheme.typography.titleSmall, color = Ink)
                        Text(n.body, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = Muted)
                }
            }
        }
    }
}

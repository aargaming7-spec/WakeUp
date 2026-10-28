package com.wakeup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val Ink = Color(0xFF111827)
val Accent = Color(0xFF3B5BFD)
val Line = Color(0xFFE9EAEE)
val Moon = Color(0xFFFFD166)
private val ID_LOCALE = Locale("id", "ID")

fun Instant.local(): ZonedDateTime = atZone(ZoneId.systemDefault())
fun fmtTime(i: Instant): String = DateTimeFormatter.ofPattern("HH:mm").format(i.local())
fun fmtDate(i: Instant): String = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", ID_LOCALE).format(i.local())
fun fmtDateTime(i: Instant): String = fmtDate(i) + " • " + fmtTime(i)

private val WakeFont = FontFamily(
    Font(R.font.plusjakartasans_regular, FontWeight.Normal),
    Font(R.font.plusjakartasans_medium, FontWeight.Medium),
    Font(R.font.plusjakartasans_semibold, FontWeight.SemiBold),
    Font(R.font.plusjakartasans_bold, FontWeight.Bold))

private fun TextStyle.f(w: FontWeight? = null) = copy(fontFamily = WakeFont, fontWeight = w ?: fontWeight)

private val WakeType: Typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.f(FontWeight.Bold), displayMedium = t.displayMedium.f(FontWeight.Bold),
        displaySmall = t.displaySmall.f(FontWeight.Bold),
        headlineLarge = t.headlineLarge.f(FontWeight.Bold), headlineMedium = t.headlineMedium.f(FontWeight.Bold),
        headlineSmall = t.headlineSmall.f(FontWeight.SemiBold),
        titleLarge = t.titleLarge.f(FontWeight.SemiBold), titleMedium = t.titleMedium.f(FontWeight.SemiBold),
        titleSmall = t.titleSmall.f(FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(FontWeight.Medium), labelMedium = t.labelMedium.f(FontWeight.Medium),
        labelSmall = t.labelSmall.f(FontWeight.Medium))
}

@Composable
fun WakeTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(primary = Color.White, onPrimary = Ink,
        background = Color(0xFF0B0F1A), surface = Color(0xFF0B0F1A), onBackground = Color.White, onSurface = Color.White,
        secondaryContainer = Color(0xFF262D44), onSecondaryContainer = Color.White)
    else lightColorScheme(primary = Ink, onPrimary = Color.White,
        primaryContainer = Color(0xFFF1F2F5), secondaryContainer = Color(0xFFEDEEF2), onSecondaryContainer = Ink,
        background = Color(0xFFFAFAFB), surface = Color(0xFFFAFAFB), onBackground = Ink, onSurface = Ink,
        outline = Line)
    MaterialTheme(colorScheme = scheme, typography = WakeType,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp)),
        content = content)
}

/** Jalankan blok suspend; kalau gagal, pesan error dikirim ke onError. */
@Composable
fun rememberRunner(onError: (String) -> Unit): (suspend () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return { block ->
        scope.launch { try { block() } catch (e: Exception) { onError(e.message ?: "Terjadi kesalahan") } }
        Unit
    }
}

@Composable
fun Card2(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun ErrorText(msg: String?) {
    if (msg != null) Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
}

@Composable
fun NeedLogin(onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Masuk dulu untuk memakai fitur ini.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onLogin) { Text("Masuk / Daftar") }
    }
}

/** Pilih tanggal lalu jam; hasilnya Instant di zona waktu perangkat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(onDismiss: () -> Unit, onPicked: (Instant) -> Unit) {
    val ds = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    val ts = rememberTimePickerState(8, 0, true)
    var step by remember { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                if (step == 0) step = 1 else {
                    val d = Instant.ofEpochMilli(ds.selectedDateMillis ?: System.currentTimeMillis())
                        .atZone(ZoneOffset.UTC).toLocalDate()
                    onPicked(d.atTime(ts.hour, ts.minute).atZone(ZoneId.systemDefault()).toInstant())
                }
            }) { Text(if (step == 0) "Lanjut" else "Pilih") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Batal") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (step == 0) DatePicker(ds, showModeToggle = false) else TimePicker(ts)
            }
        })
}

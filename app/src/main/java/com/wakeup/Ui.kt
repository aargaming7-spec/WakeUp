package com.wakeup

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// =====================================================================
//  DESIGN SYSTEM "FAJAR"
//  Kertas hangat + tinta malam + satu aksen matahari terbit.
//  Hierarki dibangun dari tipografi dan ruang, bukan dari kotak di mana-mana.
// =====================================================================

private val ID_LOCALE = Locale("id", "ID")

fun Instant.local(): ZonedDateTime = atZone(ZoneId.systemDefault())
fun fmtTime(i: Instant): String = DateTimeFormatter.ofPattern("HH:mm").format(i.local())
fun fmtDate(i: Instant): String = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", ID_LOCALE).format(i.local())
fun fmtDateTime(i: Instant): String = fmtDate(i) + " • " + fmtTime(i)

fun greeting(): String = when (LocalTime.now().hour) {
    in 4..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}

/** Warna tambahan di luar skema Material. */
@Immutable
class WakeColors(
    val card: Color, val sun: Color, val onSun: Color, val sunText: Color,
    val dawn: Color, val moss: Color, val mossBg: Color, val amber: Color, val amberBg: Color,
    val group: Color, val night: Color, val onNight: Color, val onNightSoft: Color)

private val LightWake = WakeColors(
    card = Color(0xFFFFFDF9), sun = Color(0xFFE8591C), onSun = Color(0xFF1D1B2F), sunText = Color(0xFFB4410E),
    dawn = Color(0xFFFFE2CF), moss = Color(0xFF2E7D5B), mossBg = Color(0xFFDDEFE5),
    amber = Color(0xFF9A5B00), amberBg = Color(0xFFFFEFCC), group = Color(0xFF4A55A8),
    night = Color(0xFF14122A), onNight = Color(0xFFFFF4E8), onNightSoft = Color(0xFFB9B3D6))
private val DarkWake = WakeColors(
    card = Color(0xFF1C1A31), sun = Color(0xFFFF8A4C), onSun = Color(0xFF1D1B2F), sunText = Color(0xFFFFA574),
    dawn = Color(0xFF3A2A35), moss = Color(0xFF6FCFA0), mossBg = Color(0xFF1F3A30),
    amber = Color(0xFFFFC266), amberBg = Color(0xFF3A2F18), group = Color(0xFFA3ADFF),
    night = Color(0xFF0E0C20), onNight = Color(0xFFFFF4E8), onNightSoft = Color(0xFFA9A3CB))

val LocalWake = staticCompositionLocalOf { LightWake }
val MaterialTheme.wake: WakeColors @Composable @ReadOnlyComposable get() = LocalWake.current

/** Teks utama dan teks redup, mengikuti tema terang/gelap. */
val Muted: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Ink: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onBackground

private val WakeFont = FontFamily(
    Font(R.font.plusjakartasans_regular, FontWeight.Normal),
    Font(R.font.plusjakartasans_medium, FontWeight.Medium),
    Font(R.font.plusjakartasans_semibold, FontWeight.SemiBold),
    Font(R.font.plusjakartasans_bold, FontWeight.Bold))

private fun TextStyle.f(w: FontWeight? = null, size: Int? = null, ls: Double? = null, tnum: Boolean = false) = copy(
    fontFamily = WakeFont, fontWeight = w ?: fontWeight,
    fontSize = size?.sp ?: fontSize, letterSpacing = ls?.sp ?: letterSpacing,
    fontFeatureSettings = if (tnum) "tnum" else fontFeatureSettings)

private val WakeType: Typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.f(FontWeight.Bold, 56, -2.0, true),
        displayMedium = t.displayMedium.f(FontWeight.Bold, 44, -1.5, true),
        displaySmall = t.displaySmall.f(FontWeight.Bold, 34, -1.0, true),
        headlineLarge = t.headlineLarge.f(FontWeight.Bold, 30, -0.8),
        headlineMedium = t.headlineMedium.f(FontWeight.Bold, 26, -0.6),
        headlineSmall = t.headlineSmall.f(FontWeight.SemiBold, 22, -0.3),
        titleLarge = t.titleLarge.f(FontWeight.SemiBold, 20, -0.2),
        titleMedium = t.titleMedium.f(FontWeight.SemiBold, 16, -0.1),
        titleSmall = t.titleSmall.f(FontWeight.SemiBold, 14),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(FontWeight.SemiBold), labelMedium = t.labelMedium.f(FontWeight.Medium),
        labelSmall = t.labelSmall.f(FontWeight.SemiBold, 11, 0.8))
}

@Composable
fun WakeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val w = if (dark) DarkWake else LightWake
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFFFF4E8), onPrimary = Color(0xFF1D1B2F),
        primaryContainer = w.dawn, onPrimaryContainer = Color(0xFFFFF4E8),
        secondaryContainer = w.dawn, onSecondaryContainer = Color(0xFFFFF4E8),
        background = Color(0xFF131126), onBackground = Color(0xFFF3EDE3),
        surface = Color(0xFF131126), onSurface = Color(0xFFF3EDE3), onSurfaceVariant = Color(0xFFA9A3C0),
        surfaceVariant = Color(0xFF26233F), outline = Color(0xFF3A3658), outlineVariant = Color(0xFF2E2B47),
        surfaceContainerLowest = Color(0xFF0E0C20), surfaceContainerLow = w.card, surfaceContainer = w.card,
        surfaceContainerHigh = Color(0xFF24213B), surfaceContainerHighest = Color(0xFF2E2B47),
        error = Color(0xFFFF8A80))
    else lightColorScheme(
        primary = Color(0xFF1D1B2F), onPrimary = Color(0xFFFFFDF9),
        primaryContainer = w.dawn, onPrimaryContainer = Color(0xFF1D1B2F),
        secondaryContainer = w.dawn, onSecondaryContainer = Color(0xFF1D1B2F),
        background = Color(0xFFF6F1E9), onBackground = Color(0xFF1D1B2F),
        surface = Color(0xFFF6F1E9), onSurface = Color(0xFF1D1B2F), onSurfaceVariant = Color(0xFF6B665E),
        surfaceVariant = Color(0xFFECE4D6), outline = Color(0xFFD9CFBD), outlineVariant = Color(0xFFE6DECF),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = w.card, surfaceContainer = w.card,
        surfaceContainerHigh = Color(0xFFFFFAF2), surfaceContainerHighest = Color(0xFFF1E9DB),
        error = Color(0xFFB3261E))

    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { win ->
            win.statusBarColor = scheme.background.toArgb()
            win.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(win, view).apply {
                isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalWake provides w) {
        MaterialTheme(colorScheme = scheme, typography = WakeType,
            shapes = Shapes(extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)),
            content = content)
    }
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

// ======================= KOMPONEN =======================

/** Permukaan datar dengan garis tipis; tanpa bayangan. */
@Composable
fun Card2(modifier: Modifier = Modifier, color: Color = Color.Unspecified, content: @Composable ColumnScope.() -> Unit) {
    val bg = if (color == Color.Unspecified) MaterialTheme.wake.card else color
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)).padding(16.dp), content = content)
}

/** Judul layar: overline kecil berwarna matahari, judul besar, aksi opsional di kanan. */
@Composable
fun ScreenHeader(title: String, overline: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (overline != null) Text(overline.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.wake.sunText)
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Ink)
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
}

@Composable
fun Tag(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp), color = fg)
    }
}

/** Warna tag per level kesulitan: 0 mudah, 1 sedang, 2 sulit. */
@Composable
fun LevelTag(level: Int, suffix: String = "") {
    val w = MaterialTheme.wake
    val (bg, fg) = when (level) { 0 -> w.mossBg to w.moss; 1 -> w.amberBg to w.amber; else -> w.dawn to w.sunText }
    Tag(LEVELS[level] + suffix, bg, fg)
}

/** Kontrol segmen: pengganti deretan chip untuk beralih tampilan. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                .background(if (on) MaterialTheme.wake.card else Color.Transparent)
                .clickable { onSelect(i) }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                Text(o, style = MaterialTheme.typography.labelLarge, color = if (on) Ink else Muted)
            }
        }
    }
}

@Composable
fun Bar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

@Composable
fun Ring(progress: Float, dia: Dp, stroke: Dp, color: Color, modifier: Modifier = Modifier,
         content: @Composable BoxScope.() -> Unit = {}) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(dia), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val box = Size(size.width - s, size.height - s)
            drawArc(track, 0f, 360f, false, Offset(s / 2, s / 2), box, style = Stroke(s))
            if (progress > 0f) drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(s / 2, s / 2), box,
                style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Matahari terbit: lingkaran konsentris. Dipakai di hero, splash, dan layar masuk. */
@Composable
fun SunMark(modifier: Modifier = Modifier, cx: Float = 1f, cy: Float = 1f, scale: Float = .5f, rise: Float = 1f) {
    val sun = MaterialTheme.wake.sun
    Canvas(modifier) {
        val c = Offset(size.width * cx, size.height * (cy + (1f - rise) * .35f))
        val base = size.width * scale
        drawCircle(sun.copy(alpha = .07f * rise), base, c)
        drawCircle(sun.copy(alpha = .15f * rise), base * .72f, c)
        drawCircle(sun.copy(alpha = .35f * rise), base * .52f, c)
        drawCircle(sun, base * .34f, c)
    }
}

/** Tombol bulat kecil untuk aksi cepat (+/−). */
@Composable
fun RoundButton(label: String, description: String, onClick: () -> Unit, filled: Boolean = false) {
    val w = MaterialTheme.wake
    Box(Modifier.size(44.dp).clip(CircleShape)
        .background(if (filled) w.sun else MaterialTheme.colorScheme.surfaceVariant)
        .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = if (filled) w.onSun else Ink)
    }
}

/** Tombol utama beraksen matahari. */
@Composable
fun SunButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val w = MaterialTheme.wake
    Button(onClick, modifier.fillMaxWidth().height(52.dp), enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = w.sun, contentColor = w.onSun,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, disabledContentColor = Muted)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Lingkaran berisi inisial; warna ditentukan dari nama agar konsisten. */
@Composable
fun Avatar(name: String, dia: Dp = 40.dp, square: Boolean = false) {
    val w = MaterialTheme.wake
    val tones = listOf(w.dawn to w.sunText, w.mossBg to w.moss, w.amberBg to w.amber,
        MaterialTheme.colorScheme.surfaceVariant to w.group)
    val (bg, fg) = tones[(name.firstOrNull()?.code ?: 0) % tones.size]
    Box(Modifier.size(dia).clip(if (square) RoundedCornerShape(12.dp) else CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text((name.trimStart('@').firstOrNull()?.uppercase() ?: "?"), style = MaterialTheme.typography.titleMedium, color = fg)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun wakeSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = MaterialTheme.wake.sun, checkedThumbColor = Color.White, checkedBorderColor = MaterialTheme.wake.sun,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant, uncheckedThumbColor = Muted,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline)

@Composable
fun ErrorText(msg: String?) {
    if (msg != null) Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
fun NeedLogin(onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Fitur ini butuh akun", style = MaterialTheme.typography.headlineSmall, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("Masuk agar habit, jadwal, dan teman tersimpan dan tersinkron.", color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        SunButton("Masuk atau daftar", onLogin)
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

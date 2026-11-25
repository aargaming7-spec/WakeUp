package com.wakeup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            WakeTheme(dark = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.wake.night) { SplashScreen { startMain() } }
            }
        }
    }
    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java)); finish()
    }
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var go by remember { mutableStateOf(false) }
    val rise by animateFloatAsState(if (go) 1f else 0f, tween(1300, easing = FastOutSlowInEasing), label = "rise")
    LaunchedEffect(Unit) { go = true; delay(1700); onFinished() }
    val w = MaterialTheme.wake
    Box(Modifier.fillMaxSize().background(w.night)) {
        SunMark(Modifier.fillMaxSize(), cx = .5f, cy = .4f, scale = .75f, rise = rise)
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp).alpha(rise),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("WakeUp", style = MaterialTheme.typography.displayMedium, color = w.onNight)
            Spacer(Modifier.height(6.dp))
            Text("Real actions. A better you.", color = w.onNightSoft, style = MaterialTheme.typography.titleSmall)
        }
    }
}

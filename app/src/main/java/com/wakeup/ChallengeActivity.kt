package com.wakeup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt

class ChallengeActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val test = intent.getBooleanExtra("test", false)   // mode coba: tanpa alarm, tidak masuk riwayat
        val cid = intent.getIntExtra("cid", -1)
        val ch = if (cid > 0) challengeOf(cid) else randomChallenge(intent.getIntExtra("level", 0))
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
            .launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACTIVITY_RECOGNITION))
        setContent { WakeTheme(dark = true) {
            Surface(Modifier.fillMaxSize()) {
                var finished by remember { mutableStateOf(false) }
                BackHandler(!finished && !test) {}   // alarm sungguhan: tombol back diblokir
                if (!finished) Flow(ch) {
                    if (test) finish() else {
                        History.record(this); Repo.logAlarmAsync(ch.id); AlarmService.stop(this); finished = true
                    }
                } else MorningBrief { finish() }
            } } }
    }
}

@Composable
fun Flow(ch: Challenge, onDone: () -> Unit) {
    var stage by remember { mutableIntStateOf(0) }
    val stages = remember { buildList {
        if (ch.steps > 0) add("steps"); if (ch.shakes > 0) add("shake")
        if (ch.rotateDeg > 0) add("rotate")
        if (ch.obj != null || ch.face != FaceMode.NONE) add("cam") } }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Selesaikan challenge untuk mematikan alarm", style = MaterialTheme.typography.titleMedium)
        Text("Langkah ${minOf(stage + 1, stages.size)}/${stages.size}: ${ch.title}")
        Spacer(Modifier.height(16.dp))
        key(stage) {
            when (stages.getOrNull(stage)) {
                "steps" -> CountStage("Jalan ${ch.steps} langkah", ch.steps, true) { stage++ }
                "shake" -> CountStage("Goyangkan HP ${ch.shakes} kali", ch.shakes, false) { stage++ }
                "rotate" -> RotateStage(ch.rotateDeg) { stage++ }
                "cam" -> CameraStage(ch) { stage++ }
                else -> LaunchedEffect(Unit) { onDone() }
            }
        }
    }
}

@Composable
fun CountStage(label: String, goal: Int, walking: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var count by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepOk = walking && ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val sensor = (if (stepOk) sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) else null)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var last = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val hit = if (e.sensor.type == Sensor.TYPE_STEP_DETECTOR) true else {
                    val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                    val now = System.currentTimeMillis()
                    if (g > (if (walking) 11.5f else 24.5f) && now - last > 350) { last = now; true } else false
                }
                if (hit) { count++; if (count == goal) onDone() }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(l) }
    }
    Text(label, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(48.dp))
    Text("$count/$goal", style = MaterialTheme.typography.displayLarge)
    if (walking) Spacer(Modifier.height(24.dp))
    if (walking) Text("Kalau sensor langkah tidak tersedia di HP-mu, goyangkan HP seperti berjalan.",
        style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

/** Gyroscope: integrasikan kecepatan sudut untuk mengukur total putaran HP. */
@Composable
fun RotateStage(goalDeg: Int, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var deg by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        var lastT = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (lastT != 0L) {
                    val dt = (e.timestamp - lastT) / 1_000_000_000f
                    val speed = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                    deg += Math.toDegrees((speed * dt).toDouble()).toFloat()
                    if (deg >= goalDeg) onDone()
                }
                lastT = e.timestamp
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(l) }
    }
    // Fallback kalau HP tidak punya gyroscope: tombol manual muncul setelah 6 detik.
    var noSensor by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(6000)
        if (ctx.getSystemService(Context.SENSOR_SERVICE).let { (it as SensorManager).getDefaultSensor(Sensor.TYPE_GYROSCOPE) } == null) noSensor = true
    }
    Text("Putar HP $goalDeg°, misalnya seperti membuka pintu", style = MaterialTheme.typography.headlineSmall,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Spacer(Modifier.height(48.dp))
    Text("${deg.toInt()}°/$goalDeg°", style = MaterialTheme.typography.displayLarge)
    if (noSensor) {
        Spacer(Modifier.height(24.dp))
        Text("Gyroscope tidak terdeteksi di HP ini.", style = MaterialTheme.typography.labelSmall)
        Button(onDone, Modifier.padding(top = 8.dp)) { Text("Lanjutkan manual") }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraStage(ch: Challenge, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var elapsed by remember { mutableIntStateOf(0) }
    var camError by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(1000); elapsed++ } }
    val hint = when {
        ch.obj != null && ch.face == FaceMode.SMILE -> "Tunjukkan botol sambil tersenyum"
        ch.obj != null -> "Arahkan kamera ke objek: ${ch.obj}"
        ch.face == FaceMode.SMILE -> "Selfie sambil tersenyum"
        else -> "Tunjukkan wajahmu ke kamera" }
    Text(hint, style = MaterialTheme.typography.headlineSmall)
    if (elapsed > 12 && !camError) Text("Belum terdeteksi? Pastikan pencahayaan cukup terang.",
        style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFC066))
    if (camError) {
        Text("Kamera bermasalah di HP ini.", color = Color(0xFFFF6B6B))
        Button(onDone, Modifier.padding(top = 8.dp)) { Text("Lewati langkah ini") }
        return
    }
    AndroidView(modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).padding(top = 12.dp), factory = { c ->
        val pv = PreviewView(c)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build())
        val detector = FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build())
        val exec = Executors.newSingleThreadExecutor()
        var finished = false
        fun win() { if (!finished) { finished = true; ContextCompat.getMainExecutor(c).execute(onDone) } }
        try {
            ProcessCameraProvider.getInstance(c).addListener({
                try {
                    val provider = ProcessCameraProvider.getInstance(c).get()
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(exec) { proxy ->
                        val media = proxy.image
                        if (media == null || finished) { proxy.close(); return@setAnalyzer }
                        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        fun check(objOk: Boolean) {
                            if (ch.face == FaceMode.NONE) { if (objOk) win(); proxy.close(); return }
                            detector.process(img).addOnSuccessListener { faces ->
                                val faceOk = if (ch.face == FaceMode.ANY) faces.isNotEmpty()
                                    else faces.any { (it.smilingProbability ?: 0f) > 0.7f }
                                if (objOk && faceOk) win()
                            }.addOnFailureListener { }.addOnCompleteListener { proxy.close() }
                        }
                        if (ch.obj != null) labeler.process(img)
                            .addOnSuccessListener { ls -> check(ls.any { it.text.equals(ch.obj, true) }) }
                            .addOnFailureListener { proxy.close() }
                        else check(true)
                    }
                    provider.unbindAll()
                    val sel = if (ch.face != FaceMode.NONE) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    val prev = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                    provider.bindToLifecycle(owner, sel, prev, analysis)
                } catch (ex: Exception) { ContextCompat.getMainExecutor(c).execute { camError = true } }
            }, ContextCompat.getMainExecutor(c))
        } catch (ex: Exception) { camError = true }
        pv
    })
}

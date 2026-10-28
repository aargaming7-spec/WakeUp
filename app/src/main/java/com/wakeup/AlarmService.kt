package com.wakeup

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.*

/** Bunyi + getar terus sampai ChallengeActivity memanggil stop(). */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val keepLoud = object : Runnable {
        override fun run() {
            if (getSharedPreferences("wakeup", MODE_PRIVATE).getBoolean("force_volume", true)) {
                val am = getSystemService(AudioManager::class.java)
                am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            }
            handler.postDelayed(this, 1000)
        }
    }
    override fun onBind(i: Intent?) = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        val level = i?.getIntExtra("level", 0) ?: 0
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("alarm", "Alarm", NotificationManager.IMPORTANCE_HIGH))
        val open = Intent(this, ChallengeActivity::class.java).putExtra("level", level)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "alarm").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("WakeUp").setContentText("Selesaikan challenge untuk mematikan alarm")
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true)
            .setContentIntent(pi).setFullScreenIntent(pi, true).build()
        startForeground(1, n)
        startActivity(open) // fallback bila full-screen intent ditolak
        handler.post(keepLoud)
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            setDataSource(this@AlarmService, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            isLooping = true; prepare(); start()
        }
        val v = getSystemService(Vibrator::class.java)
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 500), 0))
        return START_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacks(keepLoud); player?.release(); getSystemService(Vibrator::class.java).cancel(); super.onDestroy()
    }
    companion object { fun stop(c: Context) { c.stopService(Intent(c, AlarmService::class.java)) } }
}

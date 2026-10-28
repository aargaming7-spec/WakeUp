package com.wakeup

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Notifikasi lokal untuk reminder dan habit (tidak butuh internet). */
object Notifier {
    private const val CH = "reminders"

    private fun pi(c: Context, key: Int, extras: Intent.() -> Unit = {}) = PendingIntent.getBroadcast(c, key,
        Intent(c, NotifyReceiver::class.java).putExtra("key", key).apply(extras),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(c: Context, key: Int, atMillis: Long, title: String, text: String, repeatDaily: Boolean = false) {
        if (atMillis <= System.currentTimeMillis() && !repeatDaily) return
        c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis,
            pi(c, key) { putExtra("title", title); putExtra("text", text); putExtra("repeat", repeatDaily) })
    }

    /** time = "HH:mm" atau "HH:mm:ss"; berulang setiap hari. */
    fun scheduleDaily(c: Context, key: Int, time: String, title: String, text: String) {
        val t = LocalTime.parse(time.take(5))
        var at = LocalDate.now().atTime(t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (at <= System.currentTimeMillis()) at += 24 * 3600 * 1000L
        schedule(c, key, at, title, text, true)
    }

    fun cancel(c: Context, key: Int) { c.getSystemService(AlarmManager::class.java).cancel(pi(c, key)) }

    fun show(c: Context, key: Int, title: String, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Reminder & Habit", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(key, Notification.Builder(c, CH).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(open).build())
    }
}

class NotifyReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val key = i.getIntExtra("key", 0)
        val title = i.getStringExtra("title") ?: "WakeUp"
        val text = i.getStringExtra("text") ?: ""
        Notifier.show(c, key, title, text)
        if (i.getBooleanExtra("repeat", false))
            Notifier.schedule(c, key, System.currentTimeMillis() + 24 * 3600 * 1000L, title, text, true)
    }
}

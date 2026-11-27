package com.wakeup

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Notifikasi lokal untuk reminder dan habit (tidak butuh internet). */
object Notifier {
    private const val CH = "reminders"

    private fun pi(c: Context, key: Int, extras: Intent.() -> Unit = {}) = PendingIntent.getBroadcast(c, key,
        Intent(c, NotifyReceiver::class.java).putExtra("key", key).apply(extras),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Tepat waktu bila izin exact alarm ada; kalau tidak, memakai jadwal biasa (bisa meleset beberapa menit). */
    private fun setAt(c: Context, atMillis: Long, p: PendingIntent) {
        val am = c.getSystemService(AlarmManager::class.java)
        if (AlarmStore.canScheduleExactAlarms(c)) {
            try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, p); return }
            catch (e: SecurityException) { /* izin dicabut: jatuh ke jadwal biasa */ }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, p)
    }

    /** Notifikasi sekali jalan (reminder). */
    fun schedule(c: Context, key: Int, atMillis: Long, title: String, text: String) {
        if (atMillis <= System.currentTimeMillis()) return
        setAt(c, atMillis, pi(c, key) { putExtra("title", title); putExtra("text", text) })
    }

    /**
     * Waktu pengingat habit berikutnya (setelah [from]) yang jatuh pada hari terpilih.
     * time = "HH:mm" atau "HH:mm:ss". days memakai nomor Calendar (Min=1, Sen=2, ... Sab=7); kosong = setiap hari.
     */
    fun nextHabitTime(time: String, days: List<Int>, from: Long = System.currentTimeMillis()): Long {
        val t = LocalTime.parse(time.take(5))
        var d = LocalDate.now()
        for (n in 0..8) {
            val at = d.atTime(t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dow = (d.dayOfWeek.value % 7) + 1
            if (at > from && (days.isEmpty() || dow in days)) return at
            d = d.plusDays(1)
        }
        return from + 24 * 3600 * 1000L
    }

    /** Pengingat harian habit; setelah berbunyi, NotifyReceiver memasang jadwal berikutnya sendiri. */
    fun scheduleHabit(c: Context, key: Int, habitId: String, time: String, days: List<Int>, title: String, text: String) {
        val at = nextHabitTime(time, days)
        setAt(c, at, pi(c, key) {
            putExtra("title", title); putExtra("text", text); putExtra("habitId", habitId)
            putExtra("time", time); putExtra("days", days.joinToString(","))
        })
    }

    fun cancel(c: Context, key: Int) { c.getSystemService(AlarmManager::class.java).cancel(pi(c, key)) }

    private fun manager(c: Context): NotificationManager {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Reminder & Habit", NotificationManager.IMPORTANCE_DEFAULT))
        return nm
    }

    private fun openApp(c: Context) =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    fun show(c: Context, key: Int, title: String, text: String) {
        manager(c).notify(key, Notification.Builder(c, CH).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(openApp(c)).build())
    }

    /** Notifikasi habit dengan tombol "+1". [quiet] = pembaruan tanpa bunyi ulang. */
    fun showHabit(c: Context, key: Int, habitId: String, title: String, text: String, quiet: Boolean = false) {
        val plus = Notification.Action.Builder(Icon.createWithResource(c, android.R.drawable.ic_input_add), "+1",
            HabitActions.plusOnePending(c, habitId)).build()
        manager(c).notify(key, Notification.Builder(c, CH).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(text).setAutoCancel(true).setOnlyAlertOnce(quiet)
            .setContentIntent(openApp(c)).addAction(plus).build())
    }
}

class NotifyReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val key = i.getIntExtra("key", 0)
        val title = i.getStringExtra("title") ?: "WakeUp"
        val text = i.getStringExtra("text") ?: ""
        val habitId = i.getStringExtra("habitId")
        if (habitId == null) { Notifier.show(c, key, title, text); return }
        // Habit: pasang jadwal berikutnya dulu, lalu tampilkan hanya bila hari ini belum tercapai.
        val time = i.getStringExtra("time")
        if (time != null) {
            val days = (i.getStringExtra("days") ?: "").split(",").filter { it.isNotEmpty() }.map { it.toInt() }
            Notifier.scheduleHabit(c, key, habitId, time, days, title, text)
        }
        if (!HabitLocal.isDone(habitId)) Notifier.showHabit(c, key, habitId, title, text)
    }
}

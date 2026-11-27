package com.wakeup

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object HabitActions {
    const val ACTION_PLUS_ONE = "com.wakeup.HABIT_PLUS_ONE"

    /** PendingIntent untuk tombol "+1" (notifikasi dan widget). Request code berbeda per habit. */
    fun plusOnePending(c: Context, habitId: String): PendingIntent = PendingIntent.getBroadcast(
        c, ("plus" + habitId).hashCode(),
        Intent(c, HabitActionReceiver::class.java).setAction(ACTION_PLUS_ONE).putExtra("habitId", habitId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/** Menambah progres habit 1 dari notifikasi atau widget tanpa membuka aplikasi. */
class HabitActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != HabitActions.ACTION_PLUS_ONE) return
        val id = i.getStringExtra("habitId") ?: return
        val h = HabitLocal.get(id) ?: return
        val next = h.count + 1
        HabitLocal.setCount(id, next)   // langsung terlihat di notifikasi dan widget
        val key = ("h" + id).hashCode()
        if (next >= h.target) c.getSystemService(NotificationManager::class.java).cancel(key)
        else Notifier.showHabit(c, key, id, "${h.icon} ${h.name}", "Progres $next/${h.target} ${h.unit}", true)
        if (!Supa.loggedIn) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Repo.setHabitCount(id, next) } catch (e: Exception) { /* offline: sinkron ulang saat habit dimuat */ }
            finally { pending.finish() }
        }
    }
}

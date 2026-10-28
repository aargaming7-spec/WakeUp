package com.wakeup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        val al = AlarmStore.load(c).firstOrNull { it.id == id } ?: return
        AlarmStore.schedule(c, al) // jadwalkan lagi besok
        c.startForegroundService(Intent(c, AlarmService::class.java).putExtra("level", al.level))
    }
}

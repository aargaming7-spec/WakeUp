package com.wakeup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        AlarmStore.rescheduleAll(c)
        NotificationSync.restoreFromCache(c)   // pengingat habit & reminder hilang saat HP restart; pasang lagi tanpa internet
    }
}

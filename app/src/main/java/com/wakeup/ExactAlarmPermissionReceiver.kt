package com.wakeup

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Android mengirim broadcast ini setelah akses exact alarm diberikan.
 * Jadwalkan kembali alarm lokal yang sebelumnya tersimpan saat izin belum ada.
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            intent?.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED &&
            AlarmStore.canScheduleExactAlarms(context)
        ) {
            AlarmStore.rescheduleAll(context)
        }
    }
}

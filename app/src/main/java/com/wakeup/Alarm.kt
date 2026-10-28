package com.wakeup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Calendar

/** level: 0 Easy, 1 Medium, 2 Hard — challenge sebenarnya diundi acak saat alarm bunyi. */
data class Alarm(val id: Int, val hour: Int, val minute: Int, val level: Int = 0,
                 val enabled: Boolean = true, val days: List<Int> = emptyList())

object AlarmStore {
    private fun prefs(c: Context) = c.getSharedPreferences("wakeup", Context.MODE_PRIVATE)
    private fun ints(s: String) = s.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
    fun load(c: Context): List<Alarm> {
        val a = JSONArray(prefs(c).getString("alarms", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Alarm(o.getInt("id"), o.getInt("h"), o.getInt("m"), o.optInt("lv", 0),
                o.getBoolean("e"), ints(o.optString("d", "")))
        }
    }
    fun save(c: Context, list: List<Alarm>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("h", it.hour).put("m", it.minute)
            .put("lv", it.level).put("e", it.enabled).put("d", it.days.joinToString(","))) }
        prefs(c).edit().putString("alarms", a.toString()).apply()
        list.forEach { if (it.enabled) schedule(c, it) else cancel(c, it) }
    }
    fun rescheduleAll(c: Context) = load(c).filter { it.enabled }.forEach { schedule(c, it) }
    private fun pi(c: Context, al: Alarm) = PendingIntent.getBroadcast(c, al.id,
        Intent(c, AlarmReceiver::class.java).putExtra("id", al.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(c: Context, al: Alarm) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, al.hour); set(Calendar.MINUTE, al.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        var n = 0
        while (n < 8 && (cal.timeInMillis <= now || (al.days.isNotEmpty() && cal.get(Calendar.DAY_OF_WEEK) !in al.days))) {
            cal.add(Calendar.DAY_OF_YEAR, 1); n++
        }
        c.getSystemService(AlarmManager::class.java)
            .setAlarmClock(AlarmManager.AlarmClockInfo(cal.timeInMillis, pi(c, al)), pi(c, al))
    }
    fun cancel(c: Context, al: Alarm) = c.getSystemService(AlarmManager::class.java).cancel(pi(c, al))
}

/** Riwayat bangun sukses (challenge selesai) + streak harian. */
object History {
    private fun prefs(c: Context) = c.getSharedPreferences("wakeup", Context.MODE_PRIVATE)
    private fun days(c: Context): Set<String> = prefs(c).getStringSet("hist", emptySet()) ?: emptySet()
    fun record(c: Context) = prefs(c).edit().putStringSet("hist", HashSet(days(c)) + LocalDate.now().toString()).apply()
    fun total(c: Context) = days(c).size
    fun streak(c: Context): Int {
        val s = days(c); var d = LocalDate.now()
        if (d.toString() !in s) d = d.minusDays(1)
        var n = 0
        while (d.toString() in s) { n++; d = d.minusDays(1) }
        return n
    }
    fun week(c: Context): List<Boolean> {  // Sen..Min minggu ini
        val mon = LocalDate.now().let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
        return (0..6).map { mon.plusDays(it.toLong()).toString() in days(c) }
    }
}

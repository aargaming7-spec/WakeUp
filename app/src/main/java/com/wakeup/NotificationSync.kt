package com.wakeup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Menjaga pengingat habit & reminder tetap terpasang.
 * - sync(): ambil data terbaru dari Supabase, pasang notifikasi, simpan cache lokal, batalkan yang sudah dihapus.
 * - restoreFromCache(): pasang ulang dari cache tanpa internet (dipakai BootReceiver setelah HP restart,
 *   karena AlarmManager kehilangan semua jadwal saat reboot).
 * Key notifikasi sama dengan yang dipakai HabitScreen/ScheduleScreen, jadi tidak ada notifikasi ganda.
 */
object NotificationSync {
    private fun prefs(c: Context) = c.getSharedPreferences("notif_cache", Context.MODE_PRIVATE)

    suspend fun sync(c: Context) {
        if (!Supa.loggedIn) return
        val items = JSONArray()
        Repo.habits().forEach { h ->
            val t = h.reminder ?: return@forEach
            items.put(JSONObject().put("key", ("h" + h.id).hashCode()).put("kind", "habit")
                .put("time", t.take(5)).put("title", "${h.icon} ${h.name}").put("text", "Waktunya ${h.name}!"))
        }
        Repo.reminders().filter { !it.done && it.startsAt.isAfter(Instant.now()) }.forEach { r ->
            items.put(JSONObject().put("key", r.id.hashCode()).put("kind", "reminder")
                .put("at", r.startsAt.toEpochMilli() - r.remindBefore * 60_000L)
                .put("title", "🔔 ${r.title}").put("text", "Mulai pukul ${fmtTime(r.startsAt)} (${r.category})"))
        }
        val newKeys = (0 until items.length()).map { items.getJSONObject(it).getInt("key") }.toSet()
        read(c).forEach { old -> if (old.getInt("key") !in newKeys) Notifier.cancel(c, old.getInt("key")) }
        prefs(c).edit().putString("items", items.toString()).apply()
        scheduleAll(c, items)
    }

    fun restoreFromCache(c: Context) {
        val a = JSONArray(prefs(c).getString("items", "[]"))
        scheduleAll(c, a)
    }

    private fun read(c: Context): List<JSONObject> {
        val a = JSONArray(prefs(c).getString("items", "[]"))
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    private fun scheduleAll(c: Context, items: JSONArray) {
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            when (o.getString("kind")) {
                "habit" -> Notifier.scheduleDaily(c, o.getInt("key"), o.getString("time"), o.getString("title"), o.getString("text"))
                else -> Notifier.schedule(c, o.getInt("key"), o.getLong("at"), o.getString("title"), o.getString("text"))
            }
        }
    }
}

package com.wakeup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Antrian lokal untuk data yang harus sampai ke Supabase tetapi mungkin dikirim saat offline.
 * Saat ini dipakai untuk riwayat bangun (alarm_logs). Tiap entri membawa id sendiri,
 * sehingga mengirim ulang tidak membuat data ganda (server memakai ignore-duplicates).
 */
object SyncQueue {
    private const val KEY = "pending_alarm_logs"
    private const val MAX_AGE_DAYS = 30L
    private lateinit var ctx: Context

    fun init(c: Context) { ctx = c.applicationContext }
    private fun p() = ctx.getSharedPreferences("sync_queue", Context.MODE_PRIVATE)

    @Synchronized private fun all(): List<JSONObject> {
        val a = JSONArray(p().getString(KEY, "[]"))
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    @Synchronized private fun save(list: List<JSONObject>) {
        val a = JSONArray(); list.forEach { a.put(it) }
        p().edit().putString(KEY, a.toString()).apply()
    }

    @Synchronized fun add(o: JSONObject) { save(all() + o) }

    /** Entri milik [userId]; entri yang lebih tua dari 30 hari dibuang agar antrian tidak menumpuk selamanya. */
    @Synchronized fun entriesFor(userId: String): List<JSONObject> {
        val current = all()
        val cutoff = Instant.now().minusSeconds(MAX_AGE_DAYS * 86_400)
        val fresh = current.filter { Instant.parse(it.getString("completed_at")).isAfter(cutoff) }
        if (fresh.size != current.size) save(fresh)
        return fresh.filter { it.getString("user_id") == userId }
    }

    @Synchronized fun remove(ids: Set<String>) { save(all().filter { it.getString("id") !in ids }) }

    @Synchronized fun size(): Int = all().size
}

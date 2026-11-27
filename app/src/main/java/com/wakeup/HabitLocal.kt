package com.wakeup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Salinan ringan satu habit untuk hari ini. */
data class LocalHabit(val id: String, val name: String, val icon: String, val count: Int, val target: Int, val unit: String)

/**
 * Cache lokal progres habit hari ini. Dipakai oleh hal-hal yang jalan tanpa layar aplikasi:
 * notifikasi (dilewati bila habit sudah tercapai), tombol "+1" di notifikasi, dan widget.
 * Diisi setiap Repo.habits() berhasil dan setiap progres diubah. Jika tanggal cache bukan hari ini,
 * semua jumlah dianggap 0.
 */
object HabitLocal {
    private lateinit var ctx: Context
    fun init(c: Context) { ctx = c.applicationContext }
    private fun p() = ctx.getSharedPreferences("habit_local", Context.MODE_PRIVATE)

    @Synchronized private fun write(list: List<LocalHabit>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon)
                .put("count", it.count).put("target", it.target).put("unit", it.unit))
        }
        p().edit().putString("date", LocalDate.now().toString()).putString("items", a.toString()).apply()
    }

    @Synchronized fun all(): List<LocalHabit> {
        val fresh = p().getString("date", null) == LocalDate.now().toString()
        val a = JSONArray(p().getString("items", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            LocalHabit(o.getString("id"), o.getString("name"), o.getString("icon"),
                if (fresh) o.getInt("count") else 0, o.getInt("target"), o.getString("unit"))
        }
    }

    fun get(id: String): LocalHabit? = all().firstOrNull { it.id == id }

    fun isDone(id: String): Boolean = get(id)?.let { it.count >= it.target } ?: false

    @Synchronized fun save(habits: List<Habit>) {
        write(habits.map { LocalHabit(it.id, it.name, it.icon, it.today, it.target, it.unit) })
        HabitWidget.refresh(ctx)
    }

    @Synchronized fun setCount(id: String, count: Int) {
        write(all().map { if (it.id == id) it.copy(count = count) else it })
        HabitWidget.refresh(ctx)
    }

    @Synchronized fun clear() {
        p().edit().clear().apply()
        HabitWidget.refresh(ctx)
    }
}

package com.wakeup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

data class Profile(val id: String, val username: String, val fullName: String?)
data class Habit(val id: String, val name: String, val icon: String, val target: Int, val unit: String,
                 val reminder: String?, val today: Int, val streak: Int, val week: List<Boolean>)
data class Reminder(val id: String, val title: String, val category: String, val notes: String?,
                    val startsAt: Instant, val remindBefore: Int, val done: Boolean)
data class Friendship(val id: String, val other: Profile, val status: String, val incoming: Boolean)
data class Group(val id: String, val name: String, val description: String?, val ownerId: String)
data class Member(val profile: Profile, val role: String)
data class SharedSchedule(val id: String, val groupId: String, val groupName: String, val title: String,
                          val description: String?, val startsAt: Instant, val endsAt: Instant?, val createdBy: String,
                          val myStatus: String, val going: Int, val invited: Int, val declined: Int)
/** Satu baris di halaman Jadwal: reminder pribadi atau jadwal grup. */
data class AgendaItem(val key: String, val kind: String, val refId: String, val title: String, val sub: String,
                      val start: Instant, val end: Instant, val status: String, val done: Boolean, val conflict: Boolean)

private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)
private fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun ts(s: String): Instant = OffsetDateTime.parse(s).toInstant()
private fun JSONObject.profile() = Profile(getString("id"), getString("username"), str("full_name"))
private fun uid(): String = Supa.userId ?: throw ApiException("Belum login")
private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

object Repo {
    // ---------- profil ----------
    suspend fun myProfile(): Profile? =
        Supa.get("profiles?select=id,username,full_name&id=eq.${uid()}").objs().firstOrNull()?.profile()

    suspend fun updateFullName(name: String) {
        Supa.rest("PATCH", "profiles?id=eq.${uid()}", JSONObject().put("full_name", name).toString())
    }

    // ---------- alarm (lokal tetap utama; ini cadangan di cloud) ----------
    suspend fun pushAlarms(list: List<Alarm>) {
        Supa.rest("DELETE", "alarms?user_id=eq.${uid()}")
        if (list.isEmpty()) return
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().put("user_id", uid()).put("hour", a.hour).put("minute", a.minute)
                .put("level", a.level).put("enabled", a.enabled).put("days", JSONArray(a.days)))
        }
        Supa.rest("POST", "alarms", arr.toString())
    }

    suspend fun pullAlarms(): List<Alarm> =
        Supa.get("alarms?user_id=eq.${uid()}&order=created_at").objs().mapIndexed { i, o ->
            val d = o.getJSONArray("days")
            Alarm((System.currentTimeMillis() % 900_000).toInt() + i, o.getInt("hour"), o.getInt("minute"),
                o.getInt("level"), o.getBoolean("enabled"), (0 until d.length()).map { d.getInt(it) })
        }

    fun logAlarmAsync(challengeId: Int) {
        if (!Supa.loggedIn) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                Supa.rest("POST", "alarm_logs", JSONObject().put("user_id", uid()).put("challenge_id", challengeId).toString())
            }
        }
    }

    // ---------- habit ----------
    suspend fun habits(): List<Habit> {
        val u = uid()
        val hs = Supa.get("habits?user_id=eq.$u&archived=eq.false&order=created_at").objs()
        val since = LocalDate.now().minusDays(60)
        val logs = Supa.get("habit_logs?user_id=eq.$u&log_date=gte.$since&select=habit_id,log_date,count").objs()
            .groupBy { it.getString("habit_id") }
        val today = LocalDate.now()
        return hs.map { o ->
            val id = o.getString("id"); val target = o.getInt("target_count")
            val mine = logs[id].orEmpty()
            val done = mine.filter { it.getInt("count") >= target }.map { LocalDate.parse(it.getString("log_date")) }.toSet()
            val cnt = mine.firstOrNull { it.getString("log_date") == today.toString() }?.getInt("count") ?: 0
            var d = today; if (d !in done) d = d.minusDays(1)
            var streak = 0
            while (d in done) { streak++; d = d.minusDays(1) }
            Habit(id, o.getString("name"), o.str("icon") ?: "✅", target, o.str("unit") ?: "", o.str("reminder_time"),
                cnt, streak, (6 downTo 0).map { today.minusDays(it.toLong()) in done })
        }
    }

    suspend fun addHabit(name: String, icon: String, target: Int, unit: String, reminder: String?) {
        val o = JSONObject().put("user_id", uid()).put("name", name).put("icon", icon).put("target_count", target).put("unit", unit)
        if (reminder != null) o.put("reminder_time", reminder)
        Supa.rest("POST", "habits", o.toString())
    }

    suspend fun setHabitCount(habitId: String, count: Int) {
        val row = JSONObject().put("habit_id", habitId).put("user_id", uid())
            .put("log_date", LocalDate.now().toString()).put("count", count)
        Supa.rest("POST", "habit_logs?on_conflict=habit_id,log_date", row.toString(), "resolution=merge-duplicates")
    }

    suspend fun deleteHabit(id: String) { Supa.rest("DELETE", "habits?id=eq.$id") }

    // ---------- reminder ----------
    suspend fun reminders(): List<Reminder> =
        Supa.get("reminders?user_id=eq.${uid()}&order=starts_at").objs().map {
            Reminder(it.getString("id"), it.getString("title"), it.getString("category"), it.str("notes"),
                ts(it.getString("starts_at")), it.getInt("remind_before_min"), it.getBoolean("done"))
        }

    suspend fun addReminder(title: String, category: String, notes: String, start: Instant, before: Int): String {
        val o = JSONObject().put("user_id", uid()).put("title", title).put("category", category)
            .put("notes", notes).put("starts_at", start.toString()).put("remind_before_min", before)
        val res = JSONArray(Supa.rest("POST", "reminders", o.toString(), "return=representation"))
        return res.getJSONObject(0).getString("id")
    }

    suspend fun setReminderDone(id: String, done: Boolean) {
        Supa.rest("PATCH", "reminders?id=eq.$id", JSONObject().put("done", done).toString())
    }
    suspend fun deleteReminder(id: String) { Supa.rest("DELETE", "reminders?id=eq.$id") }

    // ---------- friends ----------
    suspend fun friendships(): List<Friendship> {
        val u = uid()
        val sel = "id,status,requester_id,addressee_id," +
            "requester:profiles!friendships_requester_id_fkey(id,username,full_name)," +
            "addressee:profiles!friendships_addressee_id_fkey(id,username,full_name)"
        return Supa.get("friendships?select=$sel&or=(requester_id.eq.$u,addressee_id.eq.$u)").objs().map {
            val incoming = it.getString("addressee_id") == u
            Friendship(it.getString("id"),
                it.getJSONObject(if (incoming) "requester" else "addressee").profile(), it.getString("status"), incoming)
        }
    }

    suspend fun searchUsers(q: String): List<Profile> =
        Supa.get("profiles?select=id,username,full_name&username=ilike.*${enc(q)}*&id=neq.${uid()}&limit=20").objs().map { it.profile() }

    suspend fun sendRequest(toId: String) {
        Supa.rest("POST", "friendships", JSONObject().put("requester_id", uid()).put("addressee_id", toId).toString())
    }
    suspend fun answerRequest(id: String, accept: Boolean) {
        Supa.rest("PATCH", "friendships?id=eq.$id", JSONObject().put("status", if (accept) "accepted" else "declined").toString())
    }
    suspend fun removeFriendship(id: String) { Supa.rest("DELETE", "friendships?id=eq.$id") }

    // ---------- groups ----------
    suspend fun groups(): List<Group> =
        Supa.get("group_members?select=group:groups(id,name,description,owner_id)&user_id=eq.${uid()}").objs().map {
            val g = it.getJSONObject("group")
            Group(g.getString("id"), g.getString("name"), g.str("description"), g.getString("owner_id"))
        }

    suspend fun createGroup(name: String, desc: String) {
        Supa.rest("POST", "groups", JSONObject().put("name", name).put("description", desc).put("owner_id", uid()).toString())
    }
    suspend fun deleteGroup(id: String) { Supa.rest("DELETE", "groups?id=eq.$id") }

    suspend fun groupMembers(gid: String): List<Member> =
        Supa.get("group_members?select=role,profile:profiles(id,username,full_name)&group_id=eq.$gid").objs().map {
            Member(it.getJSONObject("profile").profile(), it.getString("role"))
        }

    suspend fun addMember(gid: String, userId: String) {
        Supa.rest("POST", "group_members", JSONObject().put("group_id", gid).put("user_id", userId).toString())
    }
    suspend fun removeMember(gid: String, userId: String) {
        Supa.rest("DELETE", "group_members?group_id=eq.$gid&user_id=eq.$userId")
    }

    // ---------- shared schedule ----------
    suspend fun sharedSchedules(): List<SharedSchedule> {
        val u = uid()
        val sel = "id,group_id,title,description,starts_at,ends_at,created_by,group:groups(name),schedule_participants(user_id,status)"
        return Supa.get("shared_schedules?select=$sel&order=starts_at").objs().map { o ->
            val ps = o.getJSONArray("schedule_participants")
            var mine = "invited"; var go = 0; var inv = 0; var dec = 0
            for (j in 0 until ps.length()) {
                val p = ps.getJSONObject(j); val st = p.getString("status")
                when (st) { "going" -> go++; "declined" -> dec++; else -> inv++ }
                if (p.getString("user_id") == u) mine = st
            }
            SharedSchedule(o.getString("id"), o.getString("group_id"), o.getJSONObject("group").getString("name"),
                o.getString("title"), o.str("description"), ts(o.getString("starts_at")), o.str("ends_at")?.let { ts(it) },
                o.getString("created_by"), mine, go, inv, dec)
        }
    }

    suspend fun createSchedule(groupId: String, title: String, desc: String, start: Instant, end: Instant) {
        val o = JSONObject().put("group_id", groupId).put("created_by", uid()).put("title", title)
            .put("description", desc).put("starts_at", start.toString()).put("ends_at", end.toString())
        Supa.rest("POST", "shared_schedules", o.toString())
    }
    suspend fun deleteSchedule(id: String) { Supa.rest("DELETE", "shared_schedules?id=eq.$id") }
    suspend fun answerSchedule(id: String, going: Boolean) {
        Supa.rest("PATCH", "schedule_participants?schedule_id=eq.$id&user_id=eq.${uid()}",
            JSONObject().put("status", if (going) "going" else "declined").toString())
    }

    // ---------- agenda gabungan + deteksi bentrok ----------
    suspend fun agenda(): List<AgendaItem> {
        val items = mutableListOf<AgendaItem>()
        reminders().forEach {
            items += AgendaItem("r" + it.id, "reminder", it.id, it.title, it.category, it.startsAt,
                it.startsAt.plusSeconds(3600), "", it.done, false)
        }
        sharedSchedules().forEach {
            items += AgendaItem("s" + it.id, "group", it.id, it.title, it.groupName, it.startsAt,
                it.endsAt ?: it.startsAt.plusSeconds(3600), it.myStatus, false, false)
        }
        val active = items.filter { !it.done && it.status != "declined" }
        return items.sortedBy { it.start }.map { a ->
            a.copy(conflict = a in active && active.any { b -> b.key != a.key && a.start < b.end && b.start < a.end })
        }
    }
}

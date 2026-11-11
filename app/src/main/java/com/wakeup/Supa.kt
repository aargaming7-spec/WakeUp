package com.wakeup

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ApiException(msg: String) : Exception(msg)

object Supa {
    private val http = OkHttpClient()
    private val JSON = "application/json".toMediaType()
    private lateinit var ctx: Context
    fun init(c: Context) { ctx = c.applicationContext; SyncQueue.init(c) }
    private fun p() = ctx.getSharedPreferences("supa", Context.MODE_PRIVATE)

    val token: String? get() = p().getString("at", null)
    val userId: String? get() = p().getString("uid", null)
    val email: String? get() = p().getString("email", null)
    val loggedIn: Boolean get() = token != null && userId != null
    /** Hapus sesi lokal, lalu cabut token di server (di background, gagal pun tidak masalah). */
    fun signOut() {
        val t = token
        p().edit().clear().apply()
        if (t != null) CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                http.newCall(Request.Builder().url(Config.SUPABASE_URL + "/auth/v1/logout")
                    .header("apikey", Config.SUPABASE_ANON_KEY).header("Authorization", "Bearer $t")
                    .post("".toRequestBody(JSON)).build()).execute().close()
            }
        }
    }

    private fun saveSession(o: JSONObject): Boolean {
        val at = o.optString("access_token")
        if (at.isEmpty()) return false
        val user = o.optJSONObject("user")
        p().edit().putString("at", at).putString("rt", o.optString("refresh_token"))
            .putString("uid", user?.optString("id")).putString("email", user?.optString("email")).apply()
        return true
    }

    private fun authCall(path: String, body: JSONObject): JSONObject {
        val req = Request.Builder().url(Config.SUPABASE_URL + path)
            .header("apikey", Config.SUPABASE_ANON_KEY)
            .post(body.toString().toRequestBody(JSON)).build()
        http.newCall(req).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            val o = try { JSONObject(txt) } catch (e: Exception) { JSONObject() }
            if (!r.isSuccessful) throw ApiException(
                o.optString("msg").ifEmpty { o.optString("error_description").ifEmpty { o.optString("message").ifEmpty { "Gagal (kode ${r.code})" } } })
            return o
        }
    }

    /** true = langsung login; false = perlu konfirmasi email dulu. */
    suspend fun signUp(email: String, pass: String, username: String, fullName: String): Boolean = withContext(Dispatchers.IO) {
        val data = JSONObject().put("username", username).put("full_name", fullName)
        saveSession(authCall("/auth/v1/signup", JSONObject().put("email", email).put("password", pass).put("data", data)))
    }

    suspend fun signIn(email: String, pass: String) = withContext(Dispatchers.IO) {
        val o = authCall("/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", pass))
        if (!saveSession(o)) throw ApiException("Login gagal")
    }

    private fun refresh(): Boolean {
        val rt = p().getString("rt", null) ?: return false
        return try { saveSession(authCall("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", rt))) }
        catch (e: ApiException) { signOut(); false }   // refresh token ditolak server: sesi benar-benar berakhir
        catch (e: Exception) { false }                  // jaringan putus: sesi dipertahankan, coba lagi nanti
    }

    suspend fun rest(method: String, path: String, body: String? = null, prefer: String? = null): String = withContext(Dispatchers.IO) {
        fun build(): Request {
            val b = Request.Builder().url("${Config.SUPABASE_URL}/rest/v1/$path")
                .header("apikey", Config.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${token ?: Config.SUPABASE_ANON_KEY}")
            if (prefer != null) b.header("Prefer", prefer)
            val rb = when {
                body != null -> body.toRequestBody(JSON)
                method == "GET" || method == "DELETE" -> null
                else -> "".toRequestBody(JSON)
            }
            return b.method(method, rb).build()
        }
        var r = http.newCall(build()).execute()
        if (r.code == 401 && refresh()) { r.close(); r = http.newCall(build()).execute() }
        r.use {
            val txt = it.body?.string().orEmpty()
            if (it.code == 401) throw ApiException("Sesi berakhir atau tidak valid, silakan masuk lagi.")
            if (!it.isSuccessful) throw ApiException(
                try { JSONObject(txt).optString("message", txt) } catch (e: Exception) { txt.ifEmpty { "Gagal (kode ${it.code})" } })
            txt
        }
    }

    /** Cek username lewat fungsi database username_available (bisa dipanggil sebelum login). */
    suspend fun usernameAvailable(u: String): Boolean = try {
        rest("POST", "rpc/username_available", JSONObject().put("uname", u).toString()).trim() != "false"
    } catch (e: Exception) { true }   // fungsi belum ada / offline: biarkan trigger database yang menangani

    suspend fun get(path: String): JSONArray = JSONArray(rest("GET", path).ifEmpty { "[]" })
}

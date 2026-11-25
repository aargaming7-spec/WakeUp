package com.wakeup

/** Isi dari Supabase Dashboard → Project Settings → API. */
object Config {
    const val SUPABASE_URL = "https://uxuzatvllcdsqlxaskuf.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_n_Q5wkQ12YpIImZM0j4b2g_D2uIoy3y"
    val configured get() = !SUPABASE_URL.contains("YOUR-") && !SUPABASE_ANON_KEY.contains("YOUR-")
}

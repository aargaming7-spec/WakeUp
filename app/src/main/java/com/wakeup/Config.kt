package com.wakeup

/** Isi dari Supabase Dashboard → Project Settings → API. */
object Config {
    const val SUPABASE_URL = "https://csethtfdplslmceoztyx.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_N-MytkK_dbyHRCfURd17Rw_cYXD2hLL"
    val configured get() = !SUPABASE_URL.contains("YOUR-") && !SUPABASE_ANON_KEY.contains("YOUR-")
}

package com.wakeup

import android.app.Application

class WakeUpApp : Application() {
    override fun onCreate() { super.onCreate(); Supa.init(this) }
}

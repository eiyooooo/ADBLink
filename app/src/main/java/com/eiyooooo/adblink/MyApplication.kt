package com.eiyooooo.adblink

import android.app.Application
import timber.log.Timber
import com.eiyooooo.adblink.entity.Preferences
import com.eiyooooo.adblink.util.FLog
import java.util.Date

lateinit var application: MyApplication private set

class MyApplication : Application() {

    companion object {
        lateinit var appStartTime: Date
            private set
    }

    override fun onCreate() {
        super.onCreate()
        application = this
        appStartTime = Date()

        Preferences.init(this)

        FLog.init(this)
        if (Preferences.enableLog) FLog.startFLog()
        Timber.i("App started at: $appStartTime")
    }
}

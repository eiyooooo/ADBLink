package com.eiyooooo.adblink

import android.app.Application
import android.content.Context
import com.eiyooooo.adblink.adb.AdbManager
import com.eiyooooo.adblink.entity.Preferences
import com.eiyooooo.adblink.util.FLog
import com.eiyooooo.adblink.util.LanguageUtil
import timber.log.Timber
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

        FLog.init(this)
        FLog.start()
        if (AdbManager.init()) {
            if (!Preferences.enableLog) {
                FLog.stop()
            }
            Timber.i("App started at: $appStartTime, AdbManager initialized")
        }
    }

    override fun attachBaseContext(base: Context) {
        Preferences.init(base)
        super.attachBaseContext(LanguageUtil.setLocale(base))
    }
}

package com.eiyooooo.adblink.entity

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.eiyooooo.adblink.AdbUsbDeviceReceiver
import com.eiyooooo.adblink.BuildConfig
import com.eiyooooo.adblink.application
import com.eiyooooo.adblink.util.get
import com.eiyooooo.adblink.util.put
import com.fredporciuncula.flow.preferences.FlowSharedPreferences

object Preferences {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var flowSharedPreferences: FlowSharedPreferences

    fun init(context: Context) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        this.sharedPreferences = sharedPreferences
        this.flowSharedPreferences = FlowSharedPreferences(sharedPreferences)

        if (!sharedPreferences.contains("enableLog")) {
            enableLog = BuildConfig.DEBUG
        }
    }

    var defaultCastMaxSize
        get() = sharedPreferences.get("defaultCastMaxSize", 1600)
        set(value) = sharedPreferences.put("defaultCastMaxSize", value)

    val defaultCastMaxSizeFlow
        get() = flowSharedPreferences.getInt("defaultCastMaxSize", 1600).asFlow()

    var defaultCastMaxFps
        get() = sharedPreferences.get("defaultCastMaxFps", 60)
        set(value) = sharedPreferences.put("defaultCastMaxFps", value)

    val defaultCastMaxFpsFlow
        get() = flowSharedPreferences.getInt("defaultCastMaxFps", 60).asFlow()

    var defaultCastMaxVideoBitrate
        get() = sharedPreferences.get("defaultCastMaxVideoBitrate", 4)
        set(value) = sharedPreferences.put("defaultCastMaxVideoBitrate", value)

    val defaultCastMaxVideoBitrateFlow
        get() = flowSharedPreferences.getInt("defaultCastMaxVideoBitrate", 4).asFlow()

    var defaultCastEnableAudio
        get() = sharedPreferences.get("defaultCastEnableAudio", true)
        set(value) = sharedPreferences.put("defaultCastEnableAudio", value)

    val defaultCastEnableAudioFlow
        get() = flowSharedPreferences.getBoolean("defaultCastEnableAudio", true).asFlow()

    var defaultCastClipboardSync
        get() = sharedPreferences.get("defaultCastClipboardSync", false)
        set(value) = sharedPreferences.put("defaultCastClipboardSync", value)

    val defaultCastClipboardSyncFlow
        get() = flowSharedPreferences.getBoolean("defaultCastClipboardSync", false).asFlow()

    var defaultCastPreferH265
        get() = sharedPreferences.get("defaultCastPreferH265", true)
        set(value) = sharedPreferences.put("defaultCastPreferH265", value)

    val defaultCastPreferH265Flow
        get() = flowSharedPreferences.getBoolean("defaultCastPreferH265", true).asFlow()

    var defaultCastPreferOpus
        get() = sharedPreferences.get("defaultCastPreferOpus", true)
        set(value) = sharedPreferences.put("defaultCastPreferOpus", value)

    val defaultCastPreferOpusFlow
        get() = flowSharedPreferences.getBoolean("defaultCastPreferOpus", true).asFlow()


    var castWakeDeviceOnConnect
        get() = sharedPreferences.get("castWakeDeviceOnConnect", true)
        set(value) = sharedPreferences.put("castWakeDeviceOnConnect", value)

    val castWakeDeviceOnConnectFlow
        get() = flowSharedPreferences.getBoolean("castWakeDeviceOnConnect", true).asFlow()

    var castTurnOffScreenOnConnect
        get() = sharedPreferences.get("castTurnOffScreenOnConnect", false)
        set(value) = sharedPreferences.put("castTurnOffScreenOnConnect", value)

    val castTurnOffScreenOnConnectFlow
        get() = flowSharedPreferences.getBoolean("castTurnOffScreenOnConnect", false).asFlow()

    var castLockDeviceOnDisconnect
        get() = sharedPreferences.get("castLockDeviceOnDisconnect", false)
        set(value) = sharedPreferences.put("castLockDeviceOnDisconnect", value)

    val castLockDeviceOnDisconnectFlow
        get() = flowSharedPreferences.getBoolean("castLockDeviceOnDisconnect", false).asFlow()

    var castTurnOnScreenOnDisconnect
        get() = sharedPreferences.get("castTurnOnScreenOnDisconnect", true)
        set(value) = sharedPreferences.put("castTurnOnScreenOnDisconnect", value)

    val castTurnOnScreenOnDisconnectFlow
        get() = flowSharedPreferences.getBoolean("castTurnOnScreenOnDisconnect", true).asFlow()

    var castKeepScreenAwake
        get() = sharedPreferences.get("castKeepScreenAwake", true)
        set(value) = sharedPreferences.put("castKeepScreenAwake", value)

    val castKeepScreenAwakeFlow
        get() = flowSharedPreferences.getBoolean("castKeepScreenAwake", true).asFlow()

    var defaultCastShowNavBar
        get() = sharedPreferences.get("defaultCastShowNavBar", true)
        set(value) = sharedPreferences.put("defaultCastShowNavBar", value)

    val defaultCastShowNavBarFlow
        get() = flowSharedPreferences.getBoolean("defaultCastShowNavBar", true).asFlow()


    var systemColor
        get() = sharedPreferences.get("systemColor", true)
        set(value) = sharedPreferences.put("systemColor", value)

    val systemColorFlow
        get() = flowSharedPreferences.getBoolean("systemColor", true).asFlow()

    var darkTheme
        get() = sharedPreferences.get("darkTheme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = sharedPreferences.put("darkTheme", value)

    val darkThemeFlow
        get() = flowSharedPreferences.getInt("darkTheme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM).asFlow()

    var appLanguage
        get() = sharedPreferences.get("appLanguage", 0)
        set(value) = sharedPreferences.put("appLanguage", value)

    val appLanguageFlow
        get() = flowSharedPreferences.getInt("appLanguage", 0).asFlow()

    var enableUSB
        get() = sharedPreferences.get("enableUSB", true)
        set(value) {
            sharedPreferences.put("enableUSB", value)
            if (value) {
                AdbUsbDeviceReceiver.INSTANCE.checkConnectedUsbDevice(application)
            } else {
                AdbUsbDeviceReceiver.INSTANCE.resetUsbConnections()
            }
        }

    val enableUSBFlow
        get() = flowSharedPreferences.getBoolean("enableUSB", true).asFlow()

    var enableDelayedAck
        get() = sharedPreferences.get("enableDelayedAck", true)
        set(value) = sharedPreferences.put("enableDelayedAck", value)

    val enableDelayedAckFlow
        get() = flowSharedPreferences.getBoolean("enableDelayedAck", true).asFlow()

    var setFullScreen
        get() = sharedPreferences.get("setFullScreen", true)
        set(value) = sharedPreferences.put("setFullScreen", value)

    val setFullScreenFlow
        get() = flowSharedPreferences.getBoolean("setFullScreen", true).asFlow()

    var enableLog
        get() = sharedPreferences.get("enableLog", BuildConfig.DEBUG)
        set(value) = sharedPreferences.put("enableLog", value)

    val enableLogFlow
        get() = flowSharedPreferences.getBoolean("enableLog", BuildConfig.DEBUG).asFlow()

    var adbConnectionTimeout
        get() = sharedPreferences.get("adbConnectionTimeout", 10)
        set(value) = sharedPreferences.put("adbConnectionTimeout", value)

    val adbConnectionTimeoutFlow
        get() = flowSharedPreferences.getInt("adbConnectionTimeout", 10).asFlow()
}

package com.eiyooooo.adblink.data

import android.hardware.usb.UsbDevice
import com.eiyooooo.adblink.entity.Preferences
import com.eiyooooo.adblink.util.generateUuid

data class Device(
    val uuid: String,
    // device information
    val isUnidentified: Boolean,
    val deviceBrand: String,
    val deviceName: String,
    val deviceSerial: String,
    // device name for display
    val name: String = "$deviceBrand $deviceName",
    // connection information
    @Transient val usbDevice: UsbDevice? = null,
    val tcpHostPort: HostPort?,
    val tlsName: String?,
    val tlsHostPort: HostPort?,
    // cast configuration
    val maxSize: Int,
    val maxFps: Int,
    val maxVideoBitrate: Int,
    val enableAudio: Boolean,
    val clipboardSync: Boolean,
    val preferH265: Boolean,
    val preferOpus: Boolean
) {

    companion object {
        fun createWithDefaults(
            deviceBrand: String,
            deviceName: String,
            deviceSerial: String,
            usbDevice: UsbDevice?,
            tcpHostPort: HostPort?,
            tlsName: String?,
            tlsHostPort: HostPort?
        ): Device {
            return Device(
                uuid = generateUuid(),
                isUnidentified = true,
                deviceBrand = deviceBrand,
                deviceName = deviceName,
                deviceSerial = deviceSerial,
                usbDevice = usbDevice,
                tcpHostPort = tcpHostPort,
                tlsName = tlsName,
                tlsHostPort = tlsHostPort,
                maxSize = Preferences.defaultCastMaxSize,
                maxFps = Preferences.defaultCastMaxFps,
                maxVideoBitrate = Preferences.defaultCastMaxVideoBitrate,
                enableAudio = Preferences.defaultCastEnableAudio,
                clipboardSync = Preferences.defaultCastClipboardSync,
                preferH265 = Preferences.defaultCastPreferH265,
                preferOpus = Preferences.defaultCastPreferOpus
            )
        }
    }
}

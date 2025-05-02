package com.eiyooooo.adblink.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey
    val uuid: String,
    val isUnidentified: Boolean,
    val deviceBrand: String,
    val deviceName: String,
    val deviceSerial: String,
    val name: String,
    val tcpHostPort: HostPort?,
    val tlsName: String?,
    val tlsHostPort: HostPort?,
    val maxSize: Int,
    val maxFps: Int,
    val maxVideoBitrate: Int,
    val enableAudio: Boolean,
    val clipboardSync: Boolean,
    val nightModeSync: Boolean,
    val preferH265: Boolean,
    val preferOpus: Boolean,
    val freeScaling: Boolean,
    val showNavBar: Boolean
) {

    fun toDevice() = Device(
        uuid = uuid,
        isUnidentified = isUnidentified,
        deviceBrand = deviceBrand,
        deviceName = deviceName,
        deviceSerial = deviceSerial,
        name = name,
        usbDevice = null,
        tcpHostPort = tcpHostPort,
        tlsName = tlsName,
        tlsHostPort = tlsHostPort,
        maxSize = maxSize,
        maxFps = maxFps,
        maxVideoBitrate = maxVideoBitrate,
        enableAudio = enableAudio,
        clipboardSync = clipboardSync,
        nightModeSync = nightModeSync,
        preferH265 = preferH265,
        preferOpus = preferOpus,
        freeScaling = freeScaling,
        showNavBar = showNavBar
    )

    companion object {
        fun fromDevice(device: Device) = DeviceEntity(
            uuid = device.uuid,
            isUnidentified = device.isUnidentified,
            deviceBrand = device.deviceBrand,
            deviceName = device.deviceName,
            deviceSerial = device.deviceSerial,
            name = device.name,
            tcpHostPort = device.tcpHostPort,
            tlsName = device.tlsName,
            tlsHostPort = device.tlsHostPort,
            maxSize = device.maxSize,
            maxFps = device.maxFps,
            maxVideoBitrate = device.maxVideoBitrate,
            enableAudio = device.enableAudio,
            clipboardSync = device.clipboardSync,
            nightModeSync = device.nightModeSync,
            preferH265 = device.preferH265,
            preferOpus = device.preferOpus,
            freeScaling = device.freeScaling,
            showNavBar = device.showNavBar
        )
    }
}

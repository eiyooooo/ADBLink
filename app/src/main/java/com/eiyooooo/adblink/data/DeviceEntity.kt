package com.eiyooooo.adblink.data

import android.hardware.usb.UsbDevice
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
    val hosts: List<ConnectionHost>,
    val connectionEndpoints: List<ConnectionEndpoint>,
    val tlsName: String?,
    val maxSize: Int,
    val maxFps: Int,
    val maxVideoBitrate: Int,
    val enableAudio: Boolean,
    val clipboardSync: Boolean,
    val preferH265: Boolean,
    val preferOpus: Boolean
) {

    fun toDevice(usbDevice: UsbDevice? = null) = Device(
        uuid = uuid,
        isUnidentified = isUnidentified,
        deviceBrand = deviceBrand,
        deviceName = deviceName,
        deviceSerial = deviceSerial,
        name = name,
        usbDevice = usbDevice,
        hosts = hosts,
        connectionEndpoints = connectionEndpoints,
        tlsName = tlsName,
        maxSize = maxSize,
        maxFps = maxFps,
        maxVideoBitrate = maxVideoBitrate,
        enableAudio = enableAudio,
        clipboardSync = clipboardSync,
        preferH265 = preferH265,
        preferOpus = preferOpus
    )

    companion object {
        fun fromDevice(device: Device) = DeviceEntity(
            uuid = device.uuid,
            isUnidentified = device.isUnidentified,
            deviceBrand = device.deviceBrand,
            deviceName = device.deviceName,
            deviceSerial = device.deviceSerial,
            name = device.name,
            hosts = device.hosts,
            connectionEndpoints = device.connectionEndpoints,
            tlsName = device.tlsName,
            maxSize = device.maxSize,
            maxFps = device.maxFps,
            maxVideoBitrate = device.maxVideoBitrate,
            enableAudio = device.enableAudio,
            clipboardSync = device.clipboardSync,
            preferH265 = device.preferH265,
            preferOpus = device.preferOpus
        )
    }
}

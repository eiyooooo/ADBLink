package com.eiyooooo.adblink.data

import android.hardware.usb.UsbDevice
import com.eiyooooo.adblink.adb.AdbManager
import com.eiyooooo.adblink.application
import com.eiyooooo.adblink.model.Device
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

object DeviceRepository {

    private var hasTriggeredColdStartConnection = false

    private val database = DeviceDatabase.getInstance(application)

    private val deviceDao = database.deviceDao()

    private val usbDeviceMap = MutableStateFlow<Map<String, UsbDevice>>(emptyMap())

    private val persistedDevices: Flow<List<Device>> = deviceDao.getAllDevices().map { entities ->
        entities.map { it.toDevice() }
    }

    val devices: Flow<List<Device>> = combine(persistedDevices, usbDeviceMap) { devices, usbMap ->
        devices.map { device ->
            if (usbMap.containsKey(device.uuid)) {
                device.copy(usbDevice = usbMap[device.uuid])
            } else {
                device
            }
        }
    }

    suspend fun addDevice(device: Device) {
        if (device.usbDevice != null) {
            updateUsbDevice(device, device.usbDevice)
        }
        deviceDao.insertDevice(DeviceEntity.fromDevice(device))
        AdbManager.connectDevice(device)
    }

    suspend fun updateDevice(device: Device, update: (Device) -> Device) {
        val updatedDevice = update(device)
        if (updatedDevice.usbDevice != null) {
            updateUsbDevice(updatedDevice, updatedDevice.usbDevice)
        }
        deviceDao.updateDevice(DeviceEntity.fromDevice(updatedDevice))
        AdbManager.reconnectDevice(updatedDevice)
    }

    suspend fun removeDevice(uuid: String) {
        val currentMap = usbDeviceMap.value.toMutableMap()
        if (currentMap.containsKey(uuid)) {
            currentMap.remove(uuid)
            usbDeviceMap.value = currentMap
        }
        deviceDao.deleteDevice(uuid)
        AdbManager.disconnectDevice(uuid)
    }

    fun updateUsbDevice(device: Device, usbDevice: UsbDevice?, needReconnect: Boolean = false) {
        val currentMap = usbDeviceMap.value.toMutableMap()
        val existingDevice = currentMap[device.uuid]
        if (existingDevice == usbDevice) {
            return
        }
        if (usbDevice == null) {
            currentMap.remove(device.uuid)
        } else {
            currentMap[device.uuid] = usbDevice
        }
        usbDeviceMap.value = currentMap
        if (needReconnect) {
            AdbManager.reconnectDevice(device.copy(usbDevice = usbDevice))
        }
    }

    fun clearAllUsbDevices() {
        usbDeviceMap.value = emptyMap()
    }

    suspend fun reconnectAllDevices() {
        val currentDevices = devices.first()
        currentDevices.forEach { device ->
            AdbManager.reconnectDevice(device)
        }
    }

    suspend fun connectAllDevicesOnColdStart() {
        if (hasTriggeredColdStartConnection) {
            return
        }
        hasTriggeredColdStartConnection = true
        Timber.d("Connecting all devices on cold start")
        delay(1000)
        reconnectAllDevices()
    }
}

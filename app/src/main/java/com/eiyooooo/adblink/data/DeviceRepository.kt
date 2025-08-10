package com.eiyooooo.adblink.data

import android.hardware.usb.UsbDevice
import com.eiyooooo.adblink.adb.AdbManager
import com.eiyooooo.adblink.application
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
            updateUsbDevice(device.uuid, device.usbDevice)
        }
        deviceDao.insertDevice(DeviceEntity.fromDevice(device))
        AdbManager.connectDevice(device)
    }

    suspend fun updateDevice(device: Device, update: (Device) -> Device) {
        val oldDevice = device
        val updatedDevice = update(device)
        if (updatedDevice.usbDevice != null) {
            updateUsbDevice(updatedDevice.uuid, updatedDevice.usbDevice)
        }
        deviceDao.updateDevice(DeviceEntity.fromDevice(updatedDevice))
        AdbManager.reconnectDevice(oldDevice, updatedDevice)
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

    fun updateUsbDevice(uuid: String, usbDevice: UsbDevice?) {
        val currentMap = usbDeviceMap.value.toMutableMap()
        val existingDevice = currentMap[uuid]
        if (existingDevice == usbDevice) {
            return
        }
        if (usbDevice == null) {
            currentMap.remove(uuid)
        } else {
            currentMap[uuid] = usbDevice
        }
        usbDeviceMap.value = currentMap
    }

    fun clearAllUsbDevices() {
        usbDeviceMap.value = emptyMap()
    }

    suspend fun reconnectAllDevices() {
        val currentDevices = devices.first()
        currentDevices.forEach { device ->
            AdbManager.connectDevice(device)
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

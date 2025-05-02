package com.eiyooooo.adblink.data

import android.hardware.usb.UsbDevice
import com.eiyooooo.adblink.application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

object DeviceRepository {

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
    }

    suspend fun updateDevice(device: Device) {
        if (device.usbDevice != null) {
            updateUsbDevice(device.uuid, device.usbDevice)
        }
        deviceDao.updateDevice(DeviceEntity.fromDevice(device))
    }

    suspend fun removeDevice(uuid: String) {
        val currentMap = usbDeviceMap.value.toMutableMap()
        if (currentMap.containsKey(uuid)) {
            currentMap.remove(uuid)
            usbDeviceMap.value = currentMap
        }
        deviceDao.deleteDevice(uuid)
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
}

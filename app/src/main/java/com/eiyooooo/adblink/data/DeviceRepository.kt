package com.eiyooooo.adblink.data

import android.content.Context
import android.hardware.usb.UsbDevice
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eiyooooo.adblink.application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

object DeviceRepository {

    private val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "devices")
    private val DEVICES_KEY = stringPreferencesKey("devices")

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val usbDeviceMap = MutableStateFlow<Map<String, UsbDevice>>(emptyMap())

    private val persistedDevices: Flow<List<Device>> = application.deviceDataStore.data
        .map { preferences ->
            preferences[DEVICES_KEY]?.let {
                try {
                    json.decodeFromString<List<Device>>(it)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decode devices")
                    emptyList()
                }
            } ?: emptyList()
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

    suspend fun addOrUpdateDevice(device: Device) {
        if (device.usbDevice != null) {
            updateUsbDevice(device.uuid, device.usbDevice)
        }

        application.deviceDataStore.edit { preferences ->
            val currentDevices = preferences[DEVICES_KEY]?.let {
                try {
                    json.decodeFromString<List<Device>>(it)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()

            val updatedDevices = currentDevices.toMutableList()
            val index = updatedDevices.indexOfFirst { it.uuid == device.uuid }
            if (index >= 0) {
                updatedDevices[index] = device
            } else {
                updatedDevices.add(device)
            }

            preferences[DEVICES_KEY] = json.encodeToString(updatedDevices)
        }
    }

    suspend fun removeDevice(uuid: String) {
        val currentMap = usbDeviceMap.value.toMutableMap()
        if (currentMap.containsKey(uuid)) {
            currentMap.remove(uuid)
            usbDeviceMap.value = currentMap
        }

        application.deviceDataStore.edit { preferences ->
            val currentDevices = preferences[DEVICES_KEY]?.let {
                try {
                    json.decodeFromString<List<Device>>(it)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()

            val updatedDevices = currentDevices.filter { it.uuid != uuid }
            preferences[DEVICES_KEY] = json.encodeToString(updatedDevices)
        }
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

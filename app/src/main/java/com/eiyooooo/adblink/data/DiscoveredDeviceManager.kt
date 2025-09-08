package com.eiyooooo.adblink.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

object DiscoveredDeviceManager {

    private val _discoveredConnectDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredConnectDevices: StateFlow<List<DiscoveredDevice>> = _discoveredConnectDevices

    private val _discoveredPairingDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredPairingDevices: StateFlow<List<DiscoveredDevice>> = _discoveredPairingDevices

    fun updateDiscoveredConnectDevices(update: (List<DiscoveredDevice>) -> List<DiscoveredDevice>) {
        _discoveredConnectDevices.update(update)
        Timber.d("Updated discovered connect devices: ${_discoveredConnectDevices.value.size}")
    }

    fun updateDiscoveredPairingDevices(update: (List<DiscoveredDevice>) -> List<DiscoveredDevice>) {
        _discoveredPairingDevices.update(update)
        Timber.d("Updated discovered pairing devices: ${_discoveredPairingDevices.value.size}")
    }

    fun removeDiscoveredConnectDevice(deviceSerial: String, hostAddress: String) {
        _discoveredConnectDevices.update {
            it.filter { device ->
                !(device.deviceSerial == deviceSerial && device.hostAddresses.contains(hostAddress))
            }
        }
    }
}

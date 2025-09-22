package com.eiyooooo.adblink.adb.discover

import android.net.nsd.NsdServiceInfo
import com.eiyooooo.adblink.data.ConnectionEndpoint
import com.eiyooooo.adblink.data.Device
import com.eiyooooo.adblink.data.DeviceRepository
import com.eiyooooo.adblink.entity.ConnectionType
import com.eiyooooo.adblink.util.findBestHostAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

object DiscoveredDeviceManager {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _discoveredConnectDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredConnectDevices: StateFlow<List<DiscoveredDevice>> = _discoveredConnectDevices

    private val _discoveredPairingDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredPairingDevices: StateFlow<List<DiscoveredDevice>> = _discoveredPairingDevices

    fun handleDiscoveredConnectDevices(infos: List<NsdServiceInfo>, serviceType: AdbDiscoverServiceType) {
        scope.launch {
            val discoveredDevices = infos.mapNotNull { info ->
                DiscoveredDevice.fromNsdServiceInfo(info)
            }

            if (discoveredDevices.isEmpty()) {
                _discoveredConnectDevices.update { currentList ->
                    currentList.filter {
                        it.serviceType != serviceType
                    }
                }
                return@launch
            }

            val devicesBySerial = discoveredDevices.groupBy { it.deviceSerial }
            val existingDevices = DeviceRepository.devices.first()

            for ((serial, devices) in devicesBySerial) {
                existingDevices.find { it.deviceSerial == serial }?.let {
                    updateExistingDeviceWithBestAddress(it, devices, serviceType)
                }
            }

            _discoveredConnectDevices.update { currentList ->
                val filteredList = currentList.filter { it.serviceType != serviceType }.toMutableList()
                for (newDevice in discoveredDevices) {
                    val alreadyExists = filteredList.any { existingDevice ->
                        existingDevice.deviceSerial == newDevice.deviceSerial &&
                                existingDevice.serviceType == newDevice.serviceType &&
                                existingDevice.hostAddresses.any { it in newDevice.hostAddresses }
                    }
                    if (!alreadyExists) {
                        filteredList.add(newDevice)
                    }
                }
                filteredList
            }
        }
    }

    private suspend fun updateExistingDeviceWithBestAddress(
        existingDevice: Device,
        discoveredDevices: List<DiscoveredDevice>,
        serviceType: AdbDiscoverServiceType
    ) {
        val allHostAddresses = discoveredDevices.flatMap { it.hostAddresses }.distinct()

        if (allHostAddresses.isEmpty()) return

        val bestAddress = findBestHostAddress(allHostAddresses)

        if (bestAddress != null) {
            val port = discoveredDevices.first().port
            val connectionType = when (serviceType) {
                AdbDiscoverServiceType.ADB_TCP -> ConnectionType.TCP
                AdbDiscoverServiceType.ADB_TLS_CONNECT -> ConnectionType.TLS
                else -> return
            }

            val newEndpoint = ConnectionEndpoint(bestAddress, port, connectionType)

            // Check if this endpoint already exists
            val existingEndpoint = existingDevice.connectionEndpoints.find {
                it.host == newEndpoint.host && it.port == newEndpoint.port && it.type == newEndpoint.type
            }

            if (existingEndpoint == null) {
                // Add new endpoint or update existing endpoint of same type
                val updatedEndpoints = existingDevice.connectionEndpoints
                    .filterNot { it.type == connectionType } // Remove old endpoint of same type
                    .plus(newEndpoint) // Add new endpoint

                DeviceRepository.updateDevice(existingDevice) {
                    it.copy(connectionEndpoints = updatedEndpoints)
                }
                Timber.d("Updated device ${existingDevice.deviceSerial} ${connectionType.name} address to $bestAddress:$port")
            }
        }
    }

    fun handleDiscoveredPairingDevices(infos: List<NsdServiceInfo>) {
        scope.launch {
            val discoveredDevices = infos.mapNotNull { info ->
                DiscoveredDevice.fromNsdServiceInfo(info)
            }

            if (discoveredDevices.isEmpty()) {
                _discoveredPairingDevices.update { emptyList() }
                return@launch
            }

            _discoveredPairingDevices.update { currentList ->
                val filteredList = currentList.toMutableList()
                for (newDevice in discoveredDevices) {
                    val alreadyExists = filteredList.any { existingDevice ->
                        existingDevice.deviceSerial == newDevice.deviceSerial &&
                                existingDevice.serviceType == newDevice.serviceType &&
                                existingDevice.hostAddresses.any { it in newDevice.hostAddresses }
                    }
                    if (!alreadyExists) {
                        filteredList.add(newDevice)
                    }
                }
                filteredList
            }
        }
    }
}

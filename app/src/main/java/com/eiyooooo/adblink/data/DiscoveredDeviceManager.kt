package com.eiyooooo.adblink.data

import android.net.nsd.NsdServiceInfo
import com.eiyooooo.adblink.adb.AdbMdns
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

    fun handleDiscoveredDevices(infos: List<NsdServiceInfo>, serviceType: String) {
        scope.launch {
            val discoveredDevices = infos.mapNotNull { info ->
                DiscoveredDevice.fromNsdServiceInfo(info)
            }

            if (discoveredDevices.isEmpty()) {
                _discoveredConnectDevices.update { currentList ->
                    currentList.filter {
                        it.serviceType.value != serviceType
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
                val filteredList = currentList.filter { it.serviceType.value != serviceType }.toMutableList()
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

    private suspend fun updateExistingDeviceWithBestAddress(
        existingDevice: Device,
        discoveredDevices: List<DiscoveredDevice>,
        serviceType: String
    ) {
        val allHostAddresses = discoveredDevices.flatMap { it.hostAddresses }.distinct()

        if (allHostAddresses.isEmpty()) return

        val bestAddress = findBestHostAddress(allHostAddresses)

        if (bestAddress != null) {
            val port = discoveredDevices.first().port
            val connectionType = when (serviceType) {
                AdbMdns.SERVICE_TYPE_ADB -> ConnectionType.TCP
                AdbMdns.SERVICE_TYPE_TLS_CONNECT -> ConnectionType.TLS
                else -> ConnectionType.TCP
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
}

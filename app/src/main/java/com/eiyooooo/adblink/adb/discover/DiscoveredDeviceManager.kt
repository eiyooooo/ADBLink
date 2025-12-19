package com.eiyooooo.adblink.adb.discover

import android.net.nsd.NsdServiceInfo
import com.eiyooooo.adblink.data.DeviceRepository
import com.eiyooooo.adblink.data.normalizeEndpointList
import com.eiyooooo.adblink.data.normalizeHostList
import com.eiyooooo.adblink.entity.ConnectionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class DiscoveredDeviceManager(private val scope: CoroutineScope) {

    private val _discoveredConnectDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredConnectDevices: StateFlow<List<DiscoveredDevice>> = _discoveredConnectDevices

    private val _discoveredPairingDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredPairingDevices: StateFlow<List<DiscoveredDevice>> = _discoveredPairingDevices

    fun handleDiscoveredConnectDevices(infos: List<NsdServiceInfo>, serviceType: AdbDiscoverServiceType) {
        scope.launch {
            val discoveredDevices = infos.mapNotNull { info ->
                DiscoveredDevice.fromNsdServiceInfo(info)
            }.groupBy { it.deviceSerial }.mapValues { (_, devices) ->
                devices.reduce { acc, device -> acc.mergeWith(device) }
            }

            val connectionTypeToReplace = when (serviceType) {
                AdbDiscoverServiceType.ADB_TCP -> {
                    ConnectionType.TCP
                }

                AdbDiscoverServiceType.ADB_TLS_CONNECT -> {
                    ConnectionType.TLS
                }

                else -> {
                    Timber.w("Unexpected service type for connect devices: $serviceType")
                    return@launch
                }
            }

            val existingDevices = DeviceRepository.devices.first()
            val updatedSerials = mutableSetOf<String>()
            discoveredDevices.forEach { (serial, newDevice) ->
                val existing = existingDevices.find { it.deviceSerial == serial } ?: return@forEach
                val manualHosts = existing.hosts.filter { it.manuallyAdded }
                val manualEndpoints = existing.connectionEndpoints.filter { it.manuallyAdded }
                val updatedHosts = normalizeHostList(manualHosts, newDevice.hosts)
                val updatedEndpoints = normalizeEndpointList(manualEndpoints, newDevice.connectionEndpoints)
                if (updatedHosts != existing.hosts || updatedEndpoints != existing.connectionEndpoints) {
                    DeviceRepository.updateDevice(existing) {
                        it.copy(
                            hosts = updatedHosts,
                            connectionEndpoints = updatedEndpoints
                        )
                    }
                }
                updatedSerials.add(serial)
            }

            _discoveredConnectDevices.update { currentList ->
                val deviceMap = currentList.associateBy { it.deviceSerial }.toMutableMap()

                val iterator = deviceMap.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.key in updatedSerials) {
                        iterator.remove()
                        continue
                    }
                    val filteredEndpoints = entry.value.connectionEndpoints.filterNot { endpoint ->
                        endpoint.type == connectionTypeToReplace
                    }

                    if (filteredEndpoints.isEmpty()) {
                        iterator.remove()
                    } else if (filteredEndpoints.size != entry.value.connectionEndpoints.size) {
                        deviceMap[entry.key] = entry.value.copy(
                            connectionEndpoints = filteredEndpoints,
                            serviceTypes = entry.value.serviceTypes - serviceType
                        )
                    }
                }

                discoveredDevices.forEach { (serial, newDevice) ->
                    if (serial in updatedSerials) {
                        return@forEach
                    }
                    val existing = deviceMap[serial]
                    deviceMap[serial] = existing?.mergeWith(newDevice) ?: newDevice
                }

                deviceMap.values.toList()
            }
        }
    }

    fun handleDiscoveredPairingDevices(infos: List<NsdServiceInfo>) {
        scope.launch {
            val discoveredDevices = infos.mapNotNull { info ->
                DiscoveredDevice.fromNsdServiceInfo(info)
            }.groupBy { it.deviceSerial }.map { (_, devices) ->
                devices.reduce { acc, device -> acc.mergeWith(device) }
            }

            _discoveredPairingDevices.update {
                discoveredDevices
            }
        }
    }

    fun removeDiscoveredConnectDevice(deviceSerial: String) {
        _discoveredConnectDevices.update { currentList ->
            currentList.filterNot { it.deviceSerial == deviceSerial }
        }
    }
}

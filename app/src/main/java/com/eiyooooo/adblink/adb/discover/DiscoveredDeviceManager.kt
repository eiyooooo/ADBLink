package com.eiyooooo.adblink.adb.discover

import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object DiscoveredDeviceManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _discoveredConnectDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredConnectDevices: StateFlow<List<DiscoveredDevice>> = _discoveredConnectDevices

    private val _discoveredPairingDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredPairingDevices: StateFlow<List<DiscoveredDevice>> = _discoveredPairingDevices

    fun handleDiscoveredConnectDevices(infos: List<NsdServiceInfo>, serviceType: AdbDiscoverServiceType) {
        scope.launch {
            val discoveredDevices = infos.mapNotNull { info ->
                DiscoveredDevice.fromNsdServiceInfo(info)
            }
            _discoveredConnectDevices.update { currentList ->
                currentList.filter { it.serviceType != serviceType }.toMutableList().apply {
                    addAll(discoveredDevices)
                }
            }
        }
    }

    fun handleDiscoveredPairingDevices(infos: List<NsdServiceInfo>) {
        scope.launch {
            val discoveredDevices = infos.mapNotNull { info ->
                DiscoveredDevice.fromNsdServiceInfo(info)
            }
            _discoveredPairingDevices.update {
                discoveredDevices
            }
        }
    }
}

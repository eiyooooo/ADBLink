package com.eiyooooo.adblink.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.adb.discover.AdbDiscoverServiceType
import com.eiyooooo.adblink.adb.discover.DiscoveredDevice
import com.eiyooooo.adblink.adb.discover.DiscoveredDeviceManager
import com.eiyooooo.adblink.data.ConnectionEndpoint
import com.eiyooooo.adblink.data.Device
import com.eiyooooo.adblink.data.DeviceRepository
import com.eiyooooo.adblink.entity.ConnectionType
import com.eiyooooo.adblink.ui.navigation.NavRoutes
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private sealed class DeviceDetailType {
    data class Edit(val device: Device) : DeviceDetailType()
    data class Discovered(val discoveredDevice: DiscoveredDevice) : DeviceDetailType()
}

@Composable
fun DeviceDetailScreen(
    deviceType: String,
    deviceIdentifier: String,
    navController: NavHostController,
    showSnackbar: (String) -> Unit = {}
) {
    val devices by DeviceRepository.devices.collectAsState(initial = emptyList())
    val discoveredConnectDevices by DiscoveredDeviceManager.discoveredConnectDevices.collectAsState()
    val discoveredPairingDevices by DiscoveredDeviceManager.discoveredPairingDevices.collectAsState()

    var deviceDetailType by remember { mutableStateOf<DeviceDetailType?>(null) }

    LaunchedEffect(deviceType, deviceIdentifier, devices, discoveredConnectDevices, discoveredPairingDevices) {
        Timber.d("DeviceDetailScreen - type: $deviceType, identifier: $deviceIdentifier")
        Timber.d("Available devices: ${devices.map { "${it.name}(${it.uuid})" }}")

        val foundDeviceType = when (deviceType) {
            NavRoutes.DEVICE_DETAIL_TYPE_EDIT -> {
                val device = devices.find { it.uuid == deviceIdentifier }
                Timber.d("Looking for device with UUID: $deviceIdentifier, found: ${device != null}")
                device?.let {
                    DeviceDetailType.Edit(it)
                }
            }

            NavRoutes.DEVICE_DETAIL_TYPE_DISCOVERED -> {
                val decodedSerial = URLDecoder.decode(deviceIdentifier, StandardCharsets.UTF_8.toString())
                val discoveredDevice = (discoveredConnectDevices + discoveredPairingDevices)
                    .find { it.deviceSerial == decodedSerial }
                Timber.d("Looking for discovered device with serial: $decodedSerial, found: ${discoveredDevice != null}")
                discoveredDevice?.let {
                    DeviceDetailType.Discovered(it)
                }
            }

            else -> {
                Timber.d("Unknown device type: $deviceType")
                null
            }
        }

        deviceDetailType = foundDeviceType
    }

    DeviceDetailScreenContent(
        deviceDetailType = deviceDetailType,
        navController = navController,
        showSnackbar = showSnackbar
    )
}

@Composable
private fun DeviceDetailScreenContent(
    deviceDetailType: DeviceDetailType?,
    navController: NavHostController,
    showSnackbar: (String) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var isAddingDevice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        val updatedTips = stringResource(R.string.device_info_updated)

        when (deviceDetailType) {
            is DeviceDetailType.Edit -> {
                EditDeviceContent(
                    device = deviceDetailType.device,
                    isSaving = isSaving,
                    onSave = { updatedDevice ->
                        if (!isSaving) {
                            isSaving = true
                            coroutineScope.launch {
                                try {
                                    DeviceRepository.updateDevice(deviceDetailType.device) {
                                        updatedDevice
                                    }
                                    showSnackbar(updatedTips)
                                    navController.popBackStack()
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to update device")
                                    showSnackbar("Failed to save device: ${e.message}")
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    },
                    showSnackbar = showSnackbar
                )
            }

            is DeviceDetailType.Discovered -> {
                DiscoveredDeviceContent(
                    discoveredDevice = deviceDetailType.discoveredDevice,
                    isAddingDevice = isAddingDevice,
                    onAddDevice = { discoveredDevice, selectedIpList ->
                        if (!isAddingDevice) {
                            isAddingDevice = true
                            coroutineScope.launch {
                                try {
                                    // Create connection endpoints from the selected IP list
                                    val connectionEndpoints = selectedIpList.map { ipAddress ->
                                        val connectionType = when (discoveredDevice.serviceType) {
                                            AdbDiscoverServiceType.ADB_TCP -> ConnectionType.TCP
                                            AdbDiscoverServiceType.ADB_TLS_CONNECT,
                                            AdbDiscoverServiceType.ADB_TLS_PAIRING -> ConnectionType.TLS
                                        }
                                        ConnectionEndpoint(
                                            host = ipAddress,
                                            port = discoveredDevice.port,
                                            type = connectionType
                                        )
                                    }

                                    val device = Device.createWithDefaults(
                                        deviceBrand = "",
                                        deviceName = "",
                                        deviceSerial = discoveredDevice.deviceSerial,
                                        usbDevice = null,
                                        connectionEndpoints = connectionEndpoints,
                                        lastConnectedEndpoint = connectionEndpoints.firstOrNull(),
                                        tlsName = if (discoveredDevice.serviceType == AdbDiscoverServiceType.ADB_TLS_CONNECT
                                            || discoveredDevice.serviceType == AdbDiscoverServiceType.ADB_TLS_PAIRING
                                        ) discoveredDevice.serviceName else null
                                    )

                                    DeviceRepository.addDevice(device)
                                    showSnackbar(updatedTips)
                                    navController.popBackStack()
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to add device")
                                    showSnackbar("Failed to add device: ${e.message}")
                                } finally {
                                    isAddingDevice = false
                                }
                            }
                        }
                    }
                )
            }

            null -> {
                DeviceNotFoundContent()
            }
        }
    }
}

package com.eiyooooo.adblink.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.adb.discover.AdbDiscoverServiceType
import com.eiyooooo.adblink.adb.discover.DiscoveredDevice
import com.eiyooooo.adblink.adb.discover.DiscoveredDeviceManager
import com.eiyooooo.adblink.data.ConnectionEndpoint
import com.eiyooooo.adblink.data.ConnectionHost
import com.eiyooooo.adblink.data.Device
import com.eiyooooo.adblink.data.DeviceRepository
import com.eiyooooo.adblink.data.normalizeEndpointList
import com.eiyooooo.adblink.data.normalizeHostList
import com.eiyooooo.adblink.entity.ConnectionType
import com.eiyooooo.adblink.ui.component.info.DetailInfoRow
import com.eiyooooo.adblink.ui.component.info.HostListCard
import com.eiyooooo.adblink.ui.component.info.PortListCard
import com.eiyooooo.adblink.ui.navigation.NavRoutes
import com.eiyooooo.adblink.ui.snackbar.SnackbarManager
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
    navController: NavHostController
) {
    val devices by DeviceRepository.devices.collectAsState(initial = emptyList())
    val discoveredConnectDevices by DiscoveredDeviceManager.discoveredConnectDevices.collectAsState()

    var deviceDetailType by remember { mutableStateOf<DeviceDetailType?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            SnackbarManager.dismissAll()
        }
    }

    LaunchedEffect(deviceType, deviceIdentifier, devices, discoveredConnectDevices) {
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
                val discoveredDevice = discoveredConnectDevices.find { it.deviceSerial == decodedSerial }
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
        navController = navController
    )
}

@Composable
private fun DeviceDetailScreenContent(
    deviceDetailType: DeviceDetailType?,
    navController: NavHostController
) {
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var isAddingDevice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        when (deviceDetailType) {
            is DeviceDetailType.Edit -> {
                DeviceDetailContent(
                    detailType = deviceDetailType,
                    isProcessing = isSaving,
                    onSaveDevice = { updatedDevice ->
                        if (!isSaving) {
                            isSaving = true
                            coroutineScope.launch {
                                try {
                                    DeviceRepository.updateDevice(deviceDetailType.device) {
                                        updatedDevice
                                    }
                                    SnackbarManager.show(context.getString(R.string.device_info_updated))
                                    navController.popBackStack()
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to update device")
                                    SnackbarManager.show(context.getString(R.string.save_failed))
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    }
                )
            }

            is DeviceDetailType.Discovered -> {
                DeviceDetailContent(
                    detailType = deviceDetailType,
                    isProcessing = isAddingDevice,
                    onAddDevice = { discoveredDevice, editedName, selectedHosts, selectedEndpoints ->
                        if (!isAddingDevice) {
                            isAddingDevice = true
                            coroutineScope.launch {
                                try {
                                    val device = Device.createWithDefaults(
                                        deviceSerial = discoveredDevice.deviceSerial,
                                        name = editedName,
                                        hosts = selectedHosts,
                                        connectionEndpoints = selectedEndpoints,
                                        tlsName = if (discoveredDevice.serviceTypes.contains(AdbDiscoverServiceType.ADB_TLS_CONNECT)
                                            || discoveredDevice.serviceTypes.contains(AdbDiscoverServiceType.ADB_TLS_PAIRING)
                                        ) {
                                            discoveredDevice.serviceName
                                        } else {
                                            null
                                        }
                                    )
                                    DeviceRepository.addDevice(device)
                                    SnackbarManager.show(context.getString(R.string.device_info_updated))
                                    navController.popBackStack()
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to add device")
                                    SnackbarManager.show(context.getString(R.string.save_failed))
                                } finally {
                                    isAddingDevice = false
                                }
                            }
                        }
                    }
                )
            }

            null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(R.string.device_not_found),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.device_not_found_message),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceDetailContent(
    detailType: DeviceDetailType,
    isProcessing: Boolean,
    onAddDevice: (DiscoveredDevice, String, List<ConnectionHost>, List<ConnectionEndpoint>) -> Unit = { _, _, _, _ -> },
    onSaveDevice: (Device) -> Unit = {}
) {
    val context = LocalContext.current

    val hostList = remember(detailType) { mutableStateListOf<ConnectionHost>() }
    val connectionEndpointList = remember(detailType) { mutableStateListOf<ConnectionEndpoint>() }
    val endpointSnapshot by remember { derivedStateOf { connectionEndpointList.toList() } }
    var editedName by remember(detailType) {
        mutableStateOf(
            when (detailType) {
                is DeviceDetailType.Discovered -> detailType.discoveredDevice.deviceName.takeIf {
                    !it.isNullOrBlank()
                } ?: detailType.discoveredDevice.deviceSerial

                is DeviceDetailType.Edit -> detailType.device.name
            }
        )
    }

    when (detailType) {
        is DeviceDetailType.Discovered -> {
            LaunchedEffect(detailType.discoveredDevice.hosts) {
                hostList.clear()
                hostList.addAll(normalizeHostList(detailType.discoveredDevice.hosts))
            }
            LaunchedEffect(detailType.discoveredDevice.connectionEndpoints) {
                connectionEndpointList.clear()
                connectionEndpointList.addAll(normalizeEndpointList(detailType.discoveredDevice.connectionEndpoints))
            }
        }

        is DeviceDetailType.Edit -> {
            LaunchedEffect(detailType.device.hosts) {
                hostList.clear()
                hostList.addAll(normalizeHostList(detailType.device.hosts))
            }
            LaunchedEffect(detailType.device.connectionEndpoints) {
                connectionEndpointList.clear()
                connectionEndpointList.addAll(normalizeEndpointList(detailType.device.connectionEndpoints))
            }
        }
    }

    val trimmedName = editedName.trim()

    val (icon, iconColor) = when (detailType) {
        is DeviceDetailType.Discovered -> {
            when {
                detailType.discoveredDevice.serviceTypes.contains(AdbDiscoverServiceType.ADB_TLS_CONNECT) ||
                        detailType.discoveredDevice.serviceTypes.contains(AdbDiscoverServiceType.ADB_TLS_PAIRING) ->
                    Icons.Filled.Security to MaterialTheme.colorScheme.tertiary

                else -> Icons.Filled.Wifi to MaterialTheme.colorScheme.primary
            }
        }

        is DeviceDetailType.Edit -> {
            val hasTls = connectionEndpointList.any { it.type == ConnectionType.TLS }
            if (hasTls) {
                Icons.Filled.Security to MaterialTheme.colorScheme.tertiary
            } else {
                Icons.Filled.Wifi to MaterialTheme.colorScheme.primary
            }
        }
    }

    val connectionTypeText = when (detailType) {
        is DeviceDetailType.Discovered -> buildList {
            if (detailType.discoveredDevice.serviceTypes.contains(AdbDiscoverServiceType.ADB_TCP)) {
                add(stringResource(R.string.connection_type_tcp))
            }
            if (detailType.discoveredDevice.serviceTypes.contains(AdbDiscoverServiceType.ADB_TLS_CONNECT)) {
                add(stringResource(R.string.connection_type_tls_connect))
            }
            if (detailType.discoveredDevice.serviceTypes.contains(AdbDiscoverServiceType.ADB_TLS_PAIRING)) {
                add(stringResource(R.string.connection_type_tls_pairing))
            }
        }.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ") ?: stringResource(R.string.connection_type_tcp)

        is DeviceDetailType.Edit -> {
            val types = connectionEndpointList.map { it.type }.toSet()
            if (types.isEmpty()) {
                stringResource(R.string.connection_type_tcp)
            } else {
                types.map { type ->
                    when (type) {
                        ConnectionType.TCP -> stringResource(R.string.connection_type_tcp)
                        ConnectionType.TLS -> stringResource(R.string.connection_type_tls_connect)
                    }
                }.distinct().joinToString(separator = " · ")
            }
        }
    }

    val detailRows = when (detailType) {
        is DeviceDetailType.Discovered -> listOfNotNull(
            stringResource(R.string.device_serial_label) to detailType.discoveredDevice.deviceSerial,
            detailType.discoveredDevice.serviceName.takeIf { it.isNotBlank() }?.let {
                stringResource(R.string.service_name_label) to it
            }
        )

        is DeviceDetailType.Edit -> buildList {
            add(stringResource(R.string.device_serial_label) to detailType.device.deviceSerial)
            if (detailType.device.deviceBrand.isNotBlank()) {
                add(stringResource(R.string.device_brand_label) to detailType.device.deviceBrand)
            }
            if (detailType.device.deviceName.isNotBlank()) {
                add(stringResource(R.string.device_model_label) to detailType.device.deviceName)
            }
            detailType.device.tlsName?.takeIf { it.isNotBlank() }?.let { tlsName ->
                add(stringResource(R.string.device_tls_name_label) to tlsName)
            }
        }
    }

    val normalizedHosts = normalizeHostList(hostList)
    val normalizedEndpoints = normalizeEndpointList(connectionEndpointList)

    val actionEnabled = when (detailType) {
        is DeviceDetailType.Discovered ->
            normalizedHosts.isNotEmpty() && normalizedEndpoints.isNotEmpty() && trimmedName.isNotEmpty() && !isProcessing

        is DeviceDetailType.Edit -> trimmedName.isNotEmpty() && !isProcessing
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = 0.2f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(R.string.discovered_device_icon_description),
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        val headerTitle = when (detailType) {
                            is DeviceDetailType.Discovered -> stringResource(R.string.device_details)
                            is DeviceDetailType.Edit -> stringResource(R.string.edit_device)
                        }
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = connectionTypeText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider()

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    detailRows.forEach { (label, value) ->
                        DetailInfoRow(
                            label = label,
                            value = value
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.device_name_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.device_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (detailType is DeviceDetailType.Edit || hostList.isNotEmpty()) {
            HostListCard(
                hosts = hostList,
                endpoints = endpointSnapshot,
                showAddButton = true,
                onRemoveHost = { removedHost, _, restore ->
                    val message = context.getString(R.string.device_host_removed_message, removedHost.host.trim())
                    SnackbarManager.show(message, context.getString(R.string.undo), dismissCurrent = false) {
                        restore()
                    }
                }
            )
        }

        PortListCard(
            endpoints = connectionEndpointList,
            showAddButton = true,
            onAddEndpoint = { newEndpoint ->
                val existingIndex = connectionEndpointList.indexOfFirst { endpoint ->
                    endpoint.key == newEndpoint.key
                }
                if (existingIndex >= 0) {
                    connectionEndpointList[existingIndex] = newEndpoint
                } else {
                    connectionEndpointList.add(newEndpoint)
                }
            },
            onRemoveEndpoint = { endpointToRemove ->
                val removalIndex = connectionEndpointList.indexOfFirst { endpoint ->
                    endpoint.key == endpointToRemove.key
                }
                if (removalIndex >= 0) {
                    val removedEndpoint = connectionEndpointList.removeAt(removalIndex)
                    val message = context.getString(R.string.device_port_removed_message, removedEndpoint.port)
                    SnackbarManager.show(message, context.getString(R.string.undo), dismissCurrent = false) {
                        val insertIndex = removalIndex.coerceIn(0, connectionEndpointList.size)
                        connectionEndpointList.add(insertIndex, removedEndpoint)
                    }
                }
            }
        )

        Button(
            onClick = {
                when (detailType) {
                    is DeviceDetailType.Discovered -> {
                        onAddDevice(
                            detailType.discoveredDevice,
                            trimmedName,
                            normalizeHostList(hostList),
                            normalizeEndpointList(connectionEndpointList)
                        )
                    }

                    is DeviceDetailType.Edit -> {
                        val sanitizedDevice = detailType.device.copy(
                            name = trimmedName,
                            hosts = normalizeHostList(hostList),
                            connectionEndpoints = normalizeEndpointList(connectionEndpointList)
                        )
                        onSaveDevice(sanitizedDevice)
                    }
                }
            },
            enabled = actionEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                val iconVector = when (detailType) {
                    is DeviceDetailType.Discovered -> Icons.Filled.Add
                    is DeviceDetailType.Edit -> Icons.Filled.Save
                }
                Icon(
                    imageVector = iconVector,
                    contentDescription = when (detailType) {
                        is DeviceDetailType.Discovered -> stringResource(R.string.add_device)
                        is DeviceDetailType.Edit -> stringResource(R.string.save)
                    },
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            val buttonText = when (detailType) {
                is DeviceDetailType.Discovered -> if (isProcessing) {
                    stringResource(R.string.adding_device)
                } else {
                    stringResource(R.string.add_device_to_list)
                }

                is DeviceDetailType.Edit -> if (isProcessing) {
                    stringResource(R.string.saving)
                } else {
                    stringResource(R.string.save)
                }
            }

            Text(
                text = buttonText,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

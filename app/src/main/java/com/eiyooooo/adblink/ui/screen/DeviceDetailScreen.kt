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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.data.AdbServiceType
import com.eiyooooo.adblink.data.Device
import com.eiyooooo.adblink.data.DeviceRepository
import com.eiyooooo.adblink.data.DiscoveredDevice
import com.eiyooooo.adblink.data.DiscoveredDeviceManager
import com.eiyooooo.adblink.ui.component.BubbleMessage
import com.eiyooooo.adblink.ui.component.info.DetailInfoRow
import com.eiyooooo.adblink.ui.component.info.LatencyIndicator
import com.eiyooooo.adblink.ui.navigation.NavRoutes
import com.eiyooooo.adblink.util.IpLatency
import com.eiyooooo.adblink.util.parseHostPort
import com.eiyooooo.adblink.util.testMultipleLatencies
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
                    onAddDevice = { discoveredDevice, selectedIp ->
                        if (!isAddingDevice) {
                            isAddingDevice = true
                            coroutineScope.launch {
                                try {
                                    // TODO
                                    val tcpHostPort = if (discoveredDevice.serviceType == AdbServiceType.ADB_TCP) {
                                        selectedIp?.let { "$it:${discoveredDevice.port}".parseHostPort() }
                                    } else null

                                    val tlsHostPort = if (discoveredDevice.serviceType == AdbServiceType.ADB_TLS_CONNECT
                                        || discoveredDevice.serviceType == AdbServiceType.ADB_TLS_PAIRING
                                    ) {
                                        selectedIp?.let { "$it:${discoveredDevice.port}".parseHostPort() }
                                    } else null

                                    val device = Device.createWithDefaults(
                                        deviceBrand = "",
                                        deviceName = "",
                                        deviceSerial = discoveredDevice.deviceSerial,
                                        usbDevice = null,
                                        tcpHostPort = tcpHostPort,
                                        tlsName = if (discoveredDevice.serviceType == AdbServiceType.ADB_TLS_CONNECT
                                            || discoveredDevice.serviceType == AdbServiceType.ADB_TLS_PAIRING
                                        ) discoveredDevice.serviceName else null,
                                        tlsHostPort = tlsHostPort
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

@Composable
private fun EditDeviceContent(
    device: Device,
    isSaving: Boolean = false,
    onSave: (Device) -> Unit,
    showSnackbar: (String) -> Unit = {}
) {
    var editedName by remember(device.name) { mutableStateOf(device.name) }
    var editedTcpHostPort by remember(device.tcpHostPort) {
        mutableStateOf(device.tcpHostPort?.let { "${it.host}:${it.port}" } ?: "")
    }
    var editedTlsHostPort by remember(device.tlsHostPort) {
        mutableStateOf(device.tlsHostPort?.let { "${it.host}:${it.port}" } ?: "")
    }

    var isTcpHostPortError by remember { mutableStateOf(false) }
    var isTlsHostPortError by remember { mutableStateOf(false) }

    val invalidHostPortMessage = stringResource(R.string.invalid_host_or_port)

    fun validateInputs(): Boolean {
        isTcpHostPortError = editedTcpHostPort.isNotBlank() && editedTcpHostPort.parseHostPort() == null
        isTlsHostPortError = editedTlsHostPort.isNotBlank() && editedTlsHostPort.parseHostPort() == null
        return !isTcpHostPortError && !isTlsHostPortError
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = stringResource(R.string.edit_device),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.edit_device),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.device_information),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text(stringResource(R.string.device_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.connection_configuration),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                BubbleMessage(stringResource(R.string.edit_device_instructions))

                OutlinedTextField(
                    value = editedTcpHostPort,
                    onValueChange = {
                        editedTcpHostPort = it
                        if (isTcpHostPortError) {
                            isTcpHostPortError = it.isNotBlank() && it.parseHostPort() == null
                        }
                    },
                    label = { Text(stringResource(R.string.adb_tcp_manual_address)) },
                    placeholder = { Text(stringResource(R.string.host_address_port)) },
                    singleLine = true,
                    isError = isTcpHostPortError,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editedTlsHostPort,
                    onValueChange = {
                        editedTlsHostPort = it
                        if (isTlsHostPortError) {
                            isTlsHostPortError = it.isNotBlank() && it.parseHostPort() == null
                        }
                    },
                    label = { Text(stringResource(R.string.adb_tls_manual_address)) },
                    placeholder = { Text(stringResource(R.string.host_address_port)) },
                    singleLine = true,
                    isError = isTlsHostPortError,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = {
                if (!isSaving && validateInputs()) {
                    val tcpHostPort = editedTcpHostPort.parseHostPort()
                    val tlsHostPort = editedTlsHostPort.parseHostPort()
                    onSave(
                        device.copy(
                            name = editedName,
                            tcpHostPort = tcpHostPort,
                            tlsHostPort = tlsHostPort
                        )
                    )
                } else if (!isSaving) {
                    showSnackbar(invalidHostPortMessage)
                }
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Filled.Save,
                    contentDescription = stringResource(R.string.save),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isSaving) stringResource(R.string.saving)
                else stringResource(R.string.save)
            )
        }
    }
}

@Composable
private fun DiscoveredDeviceContent(
    discoveredDevice: DiscoveredDevice,
    isAddingDevice: Boolean = false,
    onAddDevice: (DiscoveredDevice, String?) -> Unit
) {
    val icon = when (discoveredDevice.serviceType) {
        AdbServiceType.ADB_TCP -> Icons.Filled.Wifi
        AdbServiceType.ADB_TLS_CONNECT -> Icons.Filled.Security
        AdbServiceType.ADB_TLS_PAIRING -> Icons.Filled.Security
    }

    val backgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)

    // 状态管理
    var selectedIpAddress by remember { mutableStateOf<String?>(null) }
    var showDropdown by remember { mutableStateOf(false) }
    var ipLatencies by remember { mutableStateOf<Map<String, IpLatency>>(emptyMap()) }
    var testingIps by remember { mutableStateOf<Set<String>>(emptySet()) }
    val coroutineScope = rememberCoroutineScope()

    // 测试延迟 - 非阻塞版本
    LaunchedEffect(discoveredDevice.hostAddresses) { // TODO: 偶现所有的测试已完成但仍在转圈；转完圈但是其中一个没有结果
        if (discoveredDevice.hostAddresses.isNotEmpty()) {
            // 初始化测试中的IP集合
            testingIps = discoveredDevice.hostAddresses.toSet()

            coroutineScope.launch {
                testMultipleLatencies(
                    discoveredDevice.hostAddresses,
                    discoveredDevice.port
                ) { ip, latency ->
                    // 每个IP测试完成后立即更新结果
                    ipLatencies = ipLatencies + (ip to latency)
                    testingIps = testingIps - ip

                    // 如果这是第一个完成的或者延迟更好，自动选择
                    if (selectedIpAddress == null ||
                        (latency.isReachable && latency.latencyMs < (ipLatencies[selectedIpAddress]?.latencyMs ?: Long.MAX_VALUE))
                    ) {
                        selectedIpAddress = ip
                    }
                }
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 设备信息头部
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
                            .background(backgroundColor)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(R.string.discovered_device_icon_description),
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.device_details),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (discoveredDevice.serviceType) {
                                AdbServiceType.ADB_TCP -> stringResource(R.string.connection_type_tcp)
                                AdbServiceType.ADB_TLS_CONNECT -> stringResource(R.string.connection_type_tls_connect)
                                AdbServiceType.ADB_TLS_PAIRING -> stringResource(R.string.connection_type_tls_pairing)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider()

                // 基本信息
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailInfoRow(
                        label = stringResource(R.string.device_serial_label),
                        value = discoveredDevice.deviceSerial
                    )

                    DetailInfoRow(
                        label = stringResource(R.string.device_port_label),
                        value = discoveredDevice.port.toString()
                    )

                    DetailInfoRow(
                        label = stringResource(R.string.service_name_label),
                        value = discoveredDevice.serviceName
                    )

                    DetailInfoRow(
                        label = stringResource(R.string.available_addresses_label),
                        value = "${discoveredDevice.hostAddresses.size} ${stringResource(R.string.addresses_count)}"
                    )
                }
            }
        }

        // IP 地址选择
        if (discoveredDevice.hostAddresses.isNotEmpty()) {
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.select_ip_address),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (testingIps.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = stringResource(R.string.testing_latency),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Box {
                        OutlinedButton(
                            onClick = { showDropdown = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedIpAddress ?: stringResource(R.string.select_ip_address_placeholder),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = null
                            )
                        }

                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            discoveredDevice.hostAddresses.forEach { ipAddress ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = ipAddress,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodyMedium
                                            )

                                            // 延迟显示
                                            when {
                                                ipLatencies.containsKey(ipAddress) -> {
                                                    LatencyIndicator(latency = ipLatencies[ipAddress]!!)
                                                }

                                                testingIps.contains(ipAddress) -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(12.dp),
                                                            strokeWidth = 1.5.dp
                                                        )
                                                        Text(
                                                            text = "测试中",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedIpAddress = ipAddress
                                        showDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // 选中的 IP 信息
                    selectedIpAddress?.let { ip ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.selected_address, ip),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                when {
                                    ipLatencies.containsKey(ip) -> {
                                        LatencyIndicator(latency = ipLatencies[ip]!!)
                                    }

                                    testingIps.contains(ip) -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = "测试中",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                if (!isAddingDevice) {
                    onAddDevice(discoveredDevice, selectedIpAddress)
                }
            },
            enabled = selectedIpAddress != null && !isAddingDevice,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isAddingDevice) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_device),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isAddingDevice) stringResource(R.string.adding_device)
                else stringResource(R.string.add_device_to_list),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun DeviceNotFoundContent() {
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

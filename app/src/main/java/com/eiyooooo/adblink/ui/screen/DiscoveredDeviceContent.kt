package com.eiyooooo.adblink.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.adb.discover.AdbDiscoverServiceType
import com.eiyooooo.adblink.adb.discover.DiscoveredDevice
import com.eiyooooo.adblink.data.ConnectionEndpoint
import com.eiyooooo.adblink.ui.component.info.DetailInfoRow
import com.eiyooooo.adblink.ui.component.info.HostAddressListCard

@Composable
fun DiscoveredDeviceContent(
    discoveredDevice: DiscoveredDevice,
    isAddingDevice: Boolean = false,
    onAddDevice: (DiscoveredDevice, List<ConnectionEndpoint>) -> Unit
) {
    val icon = when {
        discoveredDevice.serviceTypes.contains(AdbDiscoverServiceType.ADB_TLS_CONNECT) -> Icons.Filled.Security
        discoveredDevice.serviceTypes.contains(AdbDiscoverServiceType.ADB_TLS_PAIRING) -> Icons.Filled.Security
        else -> Icons.Filled.Wifi
    }

    val backgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)

    val connectionEndpointList = remember { mutableStateListOf<ConnectionEndpoint>() }

    LaunchedEffect(discoveredDevice.connectionEndpoints) {
        val discoveredEndpoints = discoveredDevice.connectionEndpoints
            .distinctBy { endpointKey(it) }
        if (connectionEndpointList.isEmpty()) {
            connectionEndpointList.addAll(discoveredEndpoints)
        } else {
            val existingKeys = connectionEndpointList.map { endpointKey(it) }.toSet()
            val toAdd = discoveredEndpoints
                .filterNot { endpoint -> endpointKey(endpoint) in existingKeys }
            if (toAdd.isNotEmpty()) {
                connectionEndpointList.addAll(toAdd)
            }
        }
    }

    val connectionTypeText = buildList {
        if (discoveredDevice.serviceTypes.contains(AdbDiscoverServiceType.ADB_TCP)) {
            add(stringResource(R.string.connection_type_tcp))
        }
        if (discoveredDevice.serviceTypes.contains(AdbDiscoverServiceType.ADB_TLS_CONNECT)) {
            add(stringResource(R.string.connection_type_tls_connect))
        }
        if (discoveredDevice.serviceTypes.contains(AdbDiscoverServiceType.ADB_TLS_PAIRING)) {
            add(stringResource(R.string.connection_type_tls_pairing))
        }
    }.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ") ?: stringResource(R.string.connection_type_tcp)

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
                    DetailInfoRow(
                        label = stringResource(R.string.device_serial_label),
                        value = discoveredDevice.deviceSerial
                    )

                    DetailInfoRow(
                        label = stringResource(R.string.service_name_label),
                        value = discoveredDevice.serviceName
                    )
                }
            }
        }

        if (connectionEndpointList.isNotEmpty()) {
            HostAddressListCard(
                endpoints = connectionEndpointList,
                showAddButton = true
            )
        }

        Button(
            onClick = {
                if (!isAddingDevice) {
                    onAddDevice(discoveredDevice, connectionEndpointList.toList())
                }
            },
            enabled = connectionEndpointList.isNotEmpty() && !isAddingDevice,
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
                text = if (isAddingDevice) {
                    stringResource(R.string.adding_device)
                } else {
                    stringResource(R.string.add_device_to_list)
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private fun endpointKey(endpoint: ConnectionEndpoint): String {
    return buildString {
        append(endpoint.type)
        append(":")
        append(endpoint.host.lowercase())
        append(":")
        append(endpoint.port)
    }
}

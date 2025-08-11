package com.eiyooooo.adblink.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.adb.AdbManager
import com.eiyooooo.adblink.data.Device
import com.eiyooooo.adblink.entity.ConnectionState
import kotlinx.coroutines.delay

@Composable
fun DeviceCard(
    device: Device,
    onEditClick: (Device) -> Unit,
    onDeleteClick: (Device) -> Unit
) {
    val connectionStates by AdbManager.deviceConnectionStates.collectAsState()
    val connectionState = connectionStates[device.uuid] ?: ConnectionState.DISCONNECTED

    var showPermissionHint by remember { mutableStateOf(false) }

    LaunchedEffect(connectionState) {
        when (connectionState) {
            ConnectionState.CONNECTING -> {
                showPermissionHint = false
                delay(2000)
                showPermissionHint = true
            }

            else -> {
                showPermissionHint = false
            }
        }
    }

    val (icon, backgroundColor) = when (connectionState) {
        ConnectionState.DISCONNECTED -> Pair(
            Icons.Filled.LinkOff,
            MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
        )

        ConnectionState.CONNECTING -> Pair(
            Icons.Filled.Sync,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
        )

        ConnectionState.CONNECTED_USB -> Pair(
            Icons.Filled.Usb,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )

        ConnectionState.CONNECTED_TLS, ConnectionState.CONNECTED_TCP -> Pair(
            Icons.Filled.Wifi,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )

        ConnectionState.CONNECTION_FAILED_TIMEOUT,
        ConnectionState.CONNECTION_FAILED_UNAUTHORIZED,
        ConnectionState.CONNECTION_FAILED_PAIRING_REQUIRED,
        ConnectionState.CONNECTION_FAILED_HOST_UNREACHABLE,
        ConnectionState.CONNECTION_FAILED_UNKNOWN -> Pair(
            Icons.Filled.Warning,
            MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
        )
    }

    val connectionStatusText = when (connectionState) {
        ConnectionState.CONNECTING -> {
            if (showPermissionHint) {
                stringResource(R.string.connection_connecting_permission)
            } else {
                stringResource(R.string.connection_connecting)
            }
        }

        ConnectionState.CONNECTED_USB -> stringResource(R.string.connected_via_usb)

        ConnectionState.CONNECTED_TLS -> {
            device.tlsHostPort?.let {
                stringResource(R.string.connected_via_tls, "${it.host}:${it.port}")
            } ?: stringResource(R.string.connected_via_tls_no_host_port)
        }

        ConnectionState.CONNECTED_TCP -> {
            device.tcpHostPort?.let {
                stringResource(R.string.connected_via_tcp, "${it.host}:${it.port}")
            } ?: stringResource(R.string.connected_via_tcp_no_host_port)
        }

        ConnectionState.DISCONNECTED -> stringResource(R.string.connection_disconnected)

        ConnectionState.CONNECTION_FAILED_TIMEOUT -> stringResource(R.string.connection_failed_timeout)
        ConnectionState.CONNECTION_FAILED_UNAUTHORIZED -> stringResource(R.string.connection_failed_unauthorized)
        ConnectionState.CONNECTION_FAILED_PAIRING_REQUIRED -> stringResource(R.string.connection_failed_pairing_required)
        ConnectionState.CONNECTION_FAILED_HOST_UNREACHABLE -> stringResource(R.string.connection_failed_host_unreachable)
        ConnectionState.CONNECTION_FAILED_UNKNOWN -> stringResource(R.string.connection_failed_unknown)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(top = 18.dp, bottom = 24.dp, start = 18.dp, end = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(backgroundColor)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(R.string.connection_status_icon_description),
                        tint = when (connectionState) {
                            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error
                            ConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
                            ConnectionState.CONNECTED_USB,
                            ConnectionState.CONNECTED_TLS,
                            ConnectionState.CONNECTED_TCP -> MaterialTheme.colorScheme.primary
                            ConnectionState.CONNECTION_FAILED_TIMEOUT,
                            ConnectionState.CONNECTION_FAILED_UNAUTHORIZED,
                            ConnectionState.CONNECTION_FAILED_PAIRING_REQUIRED,
                            ConnectionState.CONNECTION_FAILED_HOST_UNREACHABLE,
                            ConnectionState.CONNECTION_FAILED_UNKNOWN -> MaterialTheme.colorScheme.error
                        }
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    modifier = Modifier.weight(1f),
                    text = if (device.isUnidentified) {
                        stringResource(R.string.unidentified_device)
                    } else {
                        device.name
                    },
                    style = MaterialTheme.typography.titleMedium
                )

                TextButton(
                    onClick = { onEditClick(device) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.edit_device),
                        modifier = Modifier.size(24.dp)
                    )
                }

                TextButton(
                    onClick = { onDeleteClick(device) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete_device),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!device.isUnidentified && connectionState != ConnectionState.CONNECTING) {
                    Text(
                        text = stringResource(R.string.device_brand, device.deviceBrand),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = stringResource(R.string.device_model, device.deviceName),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = stringResource(R.string.device_serial, device.deviceSerial),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = stringResource(R.string.connection_info, connectionStatusText),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

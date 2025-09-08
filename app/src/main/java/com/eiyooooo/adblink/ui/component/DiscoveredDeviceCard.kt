package com.eiyooooo.adblink.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.data.AdbServiceType
import com.eiyooooo.adblink.data.DiscoveredDevice

@Composable
fun DiscoveredDeviceCard(
    device: DiscoveredDevice,
    onClick: (DiscoveredDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (device.serviceType) {
        AdbServiceType.ADB_TCP -> Icons.Filled.Wifi
        AdbServiceType.ADB_TLS_CONNECT -> Icons.Filled.Security
        AdbServiceType.ADB_TLS_PAIRING -> Icons.Filled.Security
    }

    val backgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)

    val connectionTypeText = when (device.serviceType) {
        AdbServiceType.ADB_TCP -> stringResource(R.string.connection_type_tcp)
        AdbServiceType.ADB_TLS_CONNECT -> stringResource(R.string.connection_type_tls_connect)
        AdbServiceType.ADB_TLS_PAIRING -> stringResource(R.string.connection_type_tls_pairing)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(device) }
                .padding(18.dp),
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
                    contentDescription = stringResource(R.string.discovered_device_icon_description),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.device_serial, device.deviceSerial),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = connectionTypeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.view_details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

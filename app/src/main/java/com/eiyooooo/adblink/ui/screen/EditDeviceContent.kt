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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.data.Device
import com.eiyooooo.adblink.ui.component.BubbleMessage

@Composable
fun EditDeviceContent(
    device: Device,
    isSaving: Boolean = false,
    onSave: (Device) -> Unit,
    showSnackbar: (String) -> Unit = {}
) {
    var editedName by remember(device.name) { mutableStateOf(device.name) }
//    var editedTcpHostPort by remember(device.tcpHostPort) {
//        mutableStateOf(device.tcpHostPort?.let { "${it.host}:${it.port}" } ?: "")
//    }
//    var editedTlsHostPort by remember(device.tlsHostPort) {
//        mutableStateOf(device.tlsHostPort?.let { "${it.host}:${it.port}" } ?: "")
//    }

    var isTcpHostPortError by remember { mutableStateOf(false) }
    var isTlsHostPortError by remember { mutableStateOf(false) }

    val invalidHostPortMessage = stringResource(R.string.invalid_host_or_port)

//    fun validateInputs(): Boolean {
//        isTcpHostPortError = editedTcpHostPort.isNotBlank() && editedTcpHostPort.parseHostPort() == null
//        isTlsHostPortError = editedTlsHostPort.isNotBlank() && editedTlsHostPort.parseHostPort() == null
//        return !isTcpHostPortError && !isTlsHostPortError
//    }

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

//                OutlinedTextField(
//                    value = editedTcpHostPort,
//                    onValueChange = {
//                        editedTcpHostPort = it
//                        if (isTcpHostPortError) {
//                            isTcpHostPortError = it.isNotBlank() && it.parseHostPort() == null
//                        }
//                    },
//                    label = { Text(stringResource(R.string.adb_tcp_manual_address)) },
//                    placeholder = { Text(stringResource(R.string.host_address_port)) },
//                    singleLine = true,
//                    isError = isTcpHostPortError,
//                    modifier = Modifier.fillMaxWidth()
//                )
//
//                OutlinedTextField(
//                    value = editedTlsHostPort,
//                    onValueChange = {
//                        editedTlsHostPort = it
//                        if (isTlsHostPortError) {
//                            isTlsHostPortError = it.isNotBlank() && it.parseHostPort() == null
//                        }
//                    },
//                    label = { Text(stringResource(R.string.adb_tls_manual_address)) },
//                    placeholder = { Text(stringResource(R.string.host_address_port)) },
//                    singleLine = true,
//                    isError = isTlsHostPortError,
//                    modifier = Modifier.fillMaxWidth()
//                )
            }
        }

        Button(
            onClick = {
                if (!isSaving) {
//                    if (!isSaving && validateInputs()) {
//                    val tcpHostPort = editedTcpHostPort.parseHostPort()
//                    val tlsHostPort = editedTlsHostPort.parseHostPort()
                    onSave(
                        device.copy(
                            name = editedName,
//                            tcpHostPort = tcpHostPort,
//                            tlsHostPort = tlsHostPort
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

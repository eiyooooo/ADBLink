package com.eiyooooo.adblink.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.data.Device
import com.eiyooooo.adblink.ui.component.BubbleMessage
import com.eiyooooo.adblink.util.parseHostPort
import kotlinx.coroutines.delay

@Composable
fun EditDeviceDialog(
    device: Device,
    onDismiss: () -> Unit,
    onSave: (Device) -> Unit
) {
    var message by remember { mutableStateOf("") }

    var editedName by remember(device.name) { mutableStateOf(device.name) }
    var editedTcpHostPort by remember(device.tcpHostPort) {
        mutableStateOf(device.tcpHostPort?.let { "${it.host}:${it.port}" } ?: "")
    }
    var editedTlsHostPort by remember(device.tlsHostPort) {
        mutableStateOf(device.tlsHostPort?.let { "${it.host}:${it.port}" } ?: "")
    }

    var isTcpHostPortError by remember { mutableStateOf(false) }
    var isTlsHostPortError by remember { mutableStateOf(false) }

    fun validateInputs(): Boolean {
        isTcpHostPortError = editedTcpHostPort.isNotBlank() && editedTcpHostPort.parseHostPort() == null
        isTlsHostPortError = editedTlsHostPort.isNotBlank() && editedTlsHostPort.parseHostPort() == null
        return !isTcpHostPortError && !isTlsHostPortError
    }

    LaunchedEffect(message) {
        if (message.isNotEmpty()) {
            delay(4000)
            message = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_device)) },
        text = {
            Column {
                if (message.isNotEmpty()) {
                    BubbleMessage(message)
                }

                BubbleMessage(stringResource(R.string.edit_device_instructions))

                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text(stringResource(R.string.device_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(8.dp))

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
        },
        confirmButton = {
            val invalidHostOrPortMessage = stringResource(R.string.invalid_host_or_port)

            TextButton(
                onClick = {
                    if (validateInputs()) {
                        val tcpHostPort = editedTcpHostPort.parseHostPort()
                        val tlsHostPort = editedTlsHostPort.parseHostPort()
                        onSave(
                            device.copy(
                                name = editedName,
                                tcpHostPort = tcpHostPort,
                                tlsHostPort = tlsHostPort
                            )
                        )
                    } else {
                        message = invalidHostOrPortMessage
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

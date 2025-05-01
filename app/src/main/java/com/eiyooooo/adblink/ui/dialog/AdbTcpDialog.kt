package com.eiyooooo.adblink.ui.dialog

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.data.Device
import com.eiyooooo.adblink.data.DeviceRepository
import com.eiyooooo.adblink.data.HostPort
import com.eiyooooo.adblink.ui.component.BubbleMessage
import com.eiyooooo.adblink.util.isValidHostAddress
import com.eiyooooo.adblink.util.isValidPort
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun AdbTcpDialog(showSnackbar: (String) -> Unit, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var message by remember { mutableStateOf("") }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.guide),
        stringResource(R.string.add_device)
    )

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5555") }

    LaunchedEffect(message) {
        if (message.isNotEmpty()) {
            delay(4000)
            message = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.adb_tcp), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                TabRow(
                    selectedTabIndex = selectedTabIndex
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                selectedTabIndex = index
                                message = ""
                            },
                            text = { Text(title) }
                        )
                    }
                }

                if (message.isNotEmpty()) {
                    BubbleMessage(
                        message = message,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                when (selectedTabIndex) {
                    0 -> GuideTab(
                        onGuideClick = {
                            val intent = Intent(Intent.ACTION_VIEW, context.getString(R.string.adb_tcp_enable_guide_url).toUri()).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to open browser")
                                message = context.getString(R.string.open_browser_failed)
                            }
                        }
                    )

                    1 -> ConnectTab(
                        host = host,
                        port = port,
                        onHostChange = { host = it },
                        onPortChange = { port = it.filter { char -> char.isDigit() } },
                        onConnectClick = {
                            if (!host.isValidHostAddress() || !port.isValidPort()) {
                                message = context.getString(R.string.invalid_host_or_port)
                                return@ConnectTab
                            }
                            val tcpHostPort = HostPort(host, port.toInt())
                            scope.launch {
                                val existingDevice = DeviceRepository.devices.first().find {
                                    it.tcpHostPort == tcpHostPort
                                }
                                if (existingDevice == null) {
                                    val device = Device.createWithDefaults(
                                        deviceBrand = "",
                                        deviceName = host,
                                        deviceSerial = "",
                                        usbDevice = null,
                                        tcpHostPort = tcpHostPort,
                                        tlsName = null,
                                        tlsHostPort = null
                                    )
                                    DeviceRepository.addOrUpdateDevice(device)
                                    Timber.d("Added device to repository via AdbTcpDialog: $host:$port")
                                    showSnackbar(context.getString(R.string.device_added))
                                    onDismissRequest()
                                } else {
                                    message = context.getString(R.string.device_already_exists)
                                }
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun GuideTab(onGuideClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.adb_tcp_security_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.adb_tcp_enable_instructions),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }

        ElevatedButton(
            onClick = onGuideClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.adb_tcp_enable_guide))
        }
    }
}

@Composable
private fun ConnectTab(
    host: String,
    port: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnectClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.adb_tcp_security_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text(stringResource(R.string.host_address)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text(stringResource(R.string.adb_tcp_port_label)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        ElevatedButton(
            onClick = onConnectClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.add_device))
        }
    }
}

package com.eiyooooo.adblink.ui.dialog

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.adb.AdbManager
import com.eiyooooo.adblink.ui.component.BubbleMessage
import com.eiyooooo.adblink.util.isValidHostAddress
import com.eiyooooo.adblink.util.isValidPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun AdbTlsDialog(showSnackbar: (String) -> Unit, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val foregroundColor = MaterialTheme.colorScheme.onSurface

    var message by remember { mutableStateOf("") }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.guide),
        stringResource(R.string.qr_code),
        stringResource(R.string.pair_code)
    )

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingQr by remember { mutableStateOf(false) }
    var qrGenerationFailed by remember { mutableStateOf(false) }

    var pairingHostPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var isPairing by remember { mutableStateOf(false) }

    val qrPairingSuccess by AdbManager.qrPairingSuccess.collectAsState()

    LaunchedEffect(qrPairingSuccess) {
        if (qrPairingSuccess) {
            AdbManager.resetQrPairingSuccess()
            showSnackbar(context.getString(R.string.pairing_success))
            onDismissRequest()
        }
    }

    LaunchedEffect(message) {
        if (message.isNotEmpty()) {
            delay(4000)
            message = ""
        }
    }

    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 1 && qrBitmap == null && !isGeneratingQr) {
            isGeneratingQr = true
            qrGenerationFailed = false
            try {
                withContext(Dispatchers.IO) {
                    qrBitmap = AdbManager.createPairingQrCode(foregroundColor = foregroundColor.toArgb())
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to create pairing QR code")
                qrGenerationFailed = true
            } finally {
                isGeneratingQr = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.adb_tls), fontWeight = FontWeight.Bold) },
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
                            val intent = Intent(Intent.ACTION_VIEW, context.getString(R.string.adb_tls_enable_guide_url).toUri()).apply {
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

                    1 -> QrPairContent(
                        qrBitmap = qrBitmap,
                        isGeneratingQr = isGeneratingQr,
                        generationFailed = qrGenerationFailed
                    )

                    2 -> CodePairContent(
                        hostPort = pairingHostPort,
                        code = pairingCode,
                        isPairing = isPairing,
                        onHostPortChange = { pairingHostPort = it },
                        onCodeChange = { pairingCode = it.filter { char -> char.isDigit() } },
                        onPairClick = {
                            val parts = pairingHostPort.trim().split(":")
                            val host = parts[0]
                            val port = parts[1]

                            if (parts.size != 2 || !host.isValidHostAddress() || !port.isValidPort() || pairingCode.isBlank()) {
                                message = context.getString(R.string.invalid_host_port_code)
                                return@CodePairContent
                            }

                            isPairing = true
                            scope.launch(Dispatchers.IO) {
                                val pairResult = AdbManager.pair(host, port.toInt(), pairingCode)
                                isPairing = false
                                if (pairResult) {
                                    showSnackbar(context.getString(R.string.pairing_success))
                                    onDismissRequest()
                                } else {
                                    message = context.getString(R.string.pairing_failed)
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
                stringResource(R.string.adb_tls_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.adb_tls_enable_instructions),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }

        ElevatedButton(
            onClick = onGuideClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.adb_tls_enable_guide))
        }
    }
}

@Composable
private fun QrPairContent(
    qrBitmap: Bitmap?,
    isGeneratingQr: Boolean,
    generationFailed: Boolean
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
                stringResource(R.string.adb_tls_pair_notice, AdbManager.getAdbKeyPairName()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(220.dp)
                .padding(8.dp)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            if (isGeneratingQr) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.generating_qr_code))
                }
            } else if (generationFailed) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.qr_generation_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.pairing_qr_code),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CodePairContent(
    hostPort: String,
    code: String,
    isPairing: Boolean,
    onHostPortChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onPairClick: () -> Unit
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
                stringResource(R.string.adb_tls_pair_notice, AdbManager.getAdbKeyPairName()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
        }

        OutlinedTextField(
            value = hostPort,
            onValueChange = onHostPortChange,
            label = { Text(stringResource(R.string.host_address_port)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text(stringResource(R.string.pair_code)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        ElevatedButton(
            onClick = onPairClick,
            enabled = !isPairing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(
                    if (isPairing) R.string.pairing else R.string.pair
                )
            )
        }
    }
}

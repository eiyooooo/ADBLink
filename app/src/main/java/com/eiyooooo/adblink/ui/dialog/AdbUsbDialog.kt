package com.eiyooooo.adblink.ui.dialog

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.eiyooooo.adblink.AdbUsbDeviceReceiver
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.UsbAuthorizationStatus
import com.eiyooooo.adblink.ui.component.BubbleMessage
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
fun AdbUsbDialog(onDismissRequest: () -> Unit) {
    val context = LocalContext.current

    var message by remember { mutableStateOf("") }
    val authorizationStatus by AdbUsbDeviceReceiver.INSTANCE.authorizationStatus.collectAsState()

    LaunchedEffect(message) {
        if (message.isNotEmpty()) {
            delay(4000)
            message = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.adb_usb), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (message.isNotEmpty()) {
                    BubbleMessage(message = message)
                }

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.adb_usb_enable_instructions),
                        modifier = Modifier.padding(16.dp)
                    )
                }

                ElevatedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, context.getString(R.string.adb_enable_guide_url).toUri()).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to open browser")
                            message = context.getString(R.string.open_browser_failed)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth()
                ) {
                    Text(stringResource(R.string.adb_enable_guide))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                when (authorizationStatus) {
                    UsbAuthorizationStatus.NO_DEVICES -> {
                        Text(
                            text = stringResource(R.string.usb_no_device_hint),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    UsbAuthorizationStatus.PERMISSION_NEEDED -> {
                        ElevatedButton(
                            onClick = {
                                AdbUsbDeviceReceiver.INSTANCE.checkConnectedUsbDevice(context)
                            },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.check_usb_device_permission))
                        }
                    }

                    UsbAuthorizationStatus.ALL_AUTHORIZED -> {
                        Text(
                            text = stringResource(R.string.usb_all_authorized_hint),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

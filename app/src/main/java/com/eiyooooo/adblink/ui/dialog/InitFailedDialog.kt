package com.eiyooooo.adblink.ui.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.util.FLog
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

@Composable
fun InitFailedDialog() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var exportResultMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.init_failed_title)) },
        text = {
            Text(
                exportResultMessage ?: stringResource(R.string.init_failed_message)
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    exitProcess(0)
                }
            ) {
                Text(stringResource(R.string.close_app))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    coroutineScope.launch {
                        FLog.export(context) { message ->
                            exportResultMessage = message
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.export_logs))
            }
        }
    )
}

package com.eiyooooo.adblink.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.util.FLog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogContent(showSnackbar: (String) -> Unit) {//TODO
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var logText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var currentUuid by remember { mutableStateOf<String?>(null) }

    // 设备列表，包括"应用日志"和"其他设备"选项
    val devices = remember {
        val list = mutableListOf<String>()
        list.add(context.getString(R.string.app_log))
//        list.add(context.getString(R.string.other_devices))
//        DeviceListAdapter.devicesList.forEach { device ->
//            list.add(device.name)
//        }
        list
    }


    // 初始化选中第一个设备（应用日志）
    LaunchedEffect(Unit) {
        selectedDevice = devices.firstOrNull()
        logText = FLog.read() ?: ""
    }

    Column {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            OutlinedTextField(
                value = selectedDevice ?: "",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                placeholder = { Text(context.getString(R.string.app_log)) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device) },
                        onClick = {
                            selectedDevice = device
                            expanded = false

                            // 更新日志文本
//                            if (device == context.getString(R.string.app_log)) {
                            // 使用协程读取应用日志
                            coroutineScope.launch {
                                logText = FLog.read() ?: ""
                            }
                            currentUuid = null
//                            } else if (device == context.getString(R.string.other_devices)) {
//                                logText = DLog.getLogs()
//                                currentUuid = null
//                            } else {
//                                for (d in DeviceListAdapter.devicesList) {
//                                    if (d.name == device) {
//                                        logText = L.getLogs(d.uuid)
//                                        currentUuid = d.uuid
//                                        break
//                                    }
//                                }
//                            }
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = logText,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            textStyle = MaterialTheme.typography.bodySmall
        )

        Button(
            onClick = { FLog.export(context, showSnackbar) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Export"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.export_logs))
        }
    }
}

@Composable
fun LogScreen(showSnackbar: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LogContent(showSnackbar)
        }
    }
}

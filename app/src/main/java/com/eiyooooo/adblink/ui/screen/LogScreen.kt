package com.eiyooooo.adblink.ui.screen

import android.content.Intent
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.util.FLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogContent() {//TODO
    val context = LocalContext.current
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

    // 读取应用日志函数
    suspend fun readAppLog(): String {
        return withContext(Dispatchers.IO) {
            FLog.logFile?.let {
                if (it.exists()) {
                    it.readText()
                } else {
                    "应用日志文件不存在"
                }
            } ?: "应用日志未初始化"
        }
    }

    // 初始化选中第一个设备（应用日志）
    LaunchedEffect(Unit) {
        selectedDevice = devices.firstOrNull()
        logText = readAppLog()
    }

    // 导出日志函数
    fun exportLogs() {
        FLog.logScope.launch {
            try {
                FLog.writeLastFLog()
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "logs_${if (currentUuid != null) "${currentUuid}_" else ""}$timeStamp.txt"

                // 创建日志文件
                val logsDir = File(context.cacheDir, "logs")
                if (!logsDir.exists()) logsDir.mkdirs()
                val logFile = File(logsDir, fileName)

                // 写入日志内容
                logFile.writeText(logText)

                // 使用FileProvider共享文件
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    logFile
                )

                // 创建分享Intent
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    type = "text/plain"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // 启动分享
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.export_logs)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
                            FLog.logScope.launch {
                                logText = readAppLog()
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
            onClick = { exportLogs() },
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
fun LogScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LogContent()
        }
    }
}

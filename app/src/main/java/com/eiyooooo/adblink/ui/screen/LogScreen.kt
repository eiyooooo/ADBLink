package com.eiyooooo.adblink.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.util.DLog
import com.eiyooooo.adblink.util.FLog
import kotlinx.coroutines.launch

enum class DialogType {
    NONE,
    EXPORT,
    CLEAR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogContent(showSnackbar: (String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var fullLogText by remember { mutableStateOf("") }
    var currentPageText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var currentUuid by remember { mutableStateOf<String?>(null) }

    val linesPerPage = 50
    var currentPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var logPages by remember { mutableStateOf(listOf("")) }

    var currentPageLines by remember { mutableStateOf(listOf("")) }
    val lazyColumnState = rememberLazyListState()

    var devicesList by remember { mutableStateOf(listOf<String>()) }

    var showDialog by remember { mutableStateOf(DialogType.NONE) }

    fun updatePageContent() {
        if (logPages.isNotEmpty() && currentPage <= logPages.size) {
            currentPageText = logPages[currentPage - 1]
            currentPageLines = currentPageText.lines()
        }
    }

    fun splitLogIntoPages(text: String) {
        val lines = text.lines()
        val pages = mutableListOf<String>()

        for (i in lines.indices step linesPerPage) {
            val endIndex = minOf(i + linesPerPage, lines.size)
            val pageContent = lines.subList(i, endIndex).joinToString("\n")
            pages.add(pageContent)
        }

        if (pages.isEmpty()) pages.add("")

        logPages = pages
        totalPages = pages.size
        currentPage = totalPages
        updatePageContent()
    }

    fun refreshLog() {
        coroutineScope.launch {
            devicesList = listOf(context.getString(R.string.app_log)) + DLog.getAllDeviceIds()
            fullLogText = if (currentUuid == null) {
                FLog.read() ?: ""
            } else {
                DLog.getLogs(currentUuid!!)
            }
            splitLogIntoPages(fullLogText)
            showSnackbar(context.getString(R.string.log_refresh_success))
        }
    }

    fun clearLog() {
        coroutineScope.launch {
            if (currentUuid == null) {
                if (FLog.clear()) {
                    fullLogText = ""
                    splitLogIntoPages(fullLogText)
                    showSnackbar(context.getString(R.string.log_clear_success))
                } else {
                    showSnackbar(context.getString(R.string.log_clear_failed))
                }
            } else {
                DLog.clearLogs(currentUuid)
                fullLogText = ""
                splitLogIntoPages(fullLogText)
                showSnackbar(context.getString(R.string.log_clear_success))
            }
        }
    }

    fun exportLog() {
        if (currentUuid == null) {
            FLog.export(context, showSnackbar)
        } else {
            coroutineScope.launch {
                DLog.export(context, currentUuid!!, showSnackbar)
            }
        }
    }

    LaunchedEffect(Unit) {
        devicesList = listOf(context.getString(R.string.app_log)) + DLog.getAllDeviceIds()
        selectedDevice = devicesList.firstOrNull()
        coroutineScope.launch {
            fullLogText = FLog.read() ?: ""
            splitLogIntoPages(fullLogText)
        }
    }

    if (showDialog != DialogType.NONE) {
        AlertDialog(
            onDismissRequest = { showDialog = DialogType.NONE },
            title = {
                Text(
                    when (showDialog) {
                        DialogType.EXPORT -> stringResource(id = R.string.export_logs)
                        DialogType.CLEAR -> stringResource(id = R.string.clear_logs)
                        else -> ""
                    }
                )
            },
            text = {
                Text(
                    when (showDialog) {
                        DialogType.EXPORT -> stringResource(id = R.string.log_export_confirm)
                        DialogType.CLEAR -> stringResource(id = R.string.log_clear_confirm)
                        else -> ""
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (showDialog) {
                            DialogType.EXPORT -> exportLog()
                            DialogType.CLEAR -> clearLog()
                            else -> {}
                        }
                        showDialog = DialogType.NONE
                    }
                ) {
                    Text(stringResource(id = R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = DialogType.NONE }
                ) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 16.dp, end = 8.dp)
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
                    devicesList.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device) },
                            onClick = {
                                selectedDevice = device
                                expanded = false

                                coroutineScope.launch {
                                    if (device == context.getString(R.string.app_log)) {
                                        currentUuid = null
                                        fullLogText = FLog.read() ?: ""
                                    } else {
                                        currentUuid = device
                                        fullLogText = DLog.getLogs(device)
                                    }
                                    splitLogIntoPages(fullLogText)
                                }
                            }
                        )
                    }
                }
            }

            IconButton(
                onClick = { refreshLog() },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(id = R.string.refresh_logs)
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp)),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 1.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
            ) {
                LazyColumn(
                    state = lazyColumnState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentPageLines.size) { index ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = "${(currentPage - 1) * linesPerPage + index + 1}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .width(32.dp)
                            )

                            Text(
                                text = currentPageLines[index],
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (currentPage > 1) {
                        currentPage--
                        updatePageContent()
                    }
                },
                enabled = currentPage > 1
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(id = R.string.previous_page)
                )
            }

            Text(
                text = stringResource(id = R.string.page_indicator, currentPage, totalPages),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = {
                    if (currentPage < totalPages) {
                        currentPage++
                        updatePageContent()
                    }
                },
                enabled = currentPage < totalPages
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(id = R.string.next_page)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showDialog = DialogType.EXPORT },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(id = R.string.export_logs)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.export_logs))
            }

            Button(
                onClick = { showDialog = DialogType.CLEAR },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(id = R.string.clear_logs)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.clear_logs))
            }
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

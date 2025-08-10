package com.eiyooooo.adblink.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.data.Device
import com.eiyooooo.adblink.data.DeviceRepository
import com.eiyooooo.adblink.ui.component.DeviceCard
import com.eiyooooo.adblink.ui.dialog.DeleteDeviceDialog
import com.eiyooooo.adblink.ui.dialog.EditDeviceDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(widthSizeClass: WindowWidthSizeClass) {
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    val devices by DeviceRepository.devices.collectAsState(initial = emptyList())

    var deviceToEdit by remember { mutableStateOf<Device?>(null) }
    var deviceToDelete by remember { mutableStateOf<Device?>(null) }

    LaunchedEffect(Unit) {
        DeviceRepository.connectAllDevicesOnColdStart()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DevicesOther,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_devices),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_devices_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        DeviceRepository.reconnectAllDevices()
                        delay(1500)
                        isRefreshing = false
                    }
                },
                state = pullRefreshState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (widthSizeClass) {
                    WindowWidthSizeClass.Compact -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(devices) { device ->
                                DeviceCard(
                                    device = device,
                                    onEditClick = { deviceToEdit = it },
                                    onDeleteClick = { deviceToDelete = it }
                                )
                            }
                        }
                    }

                    WindowWidthSizeClass.Medium, WindowWidthSizeClass.Expanded -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 280.dp),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(devices) { device ->
                                DeviceCard(
                                    device = device,
                                    onEditClick = { deviceToEdit = it },
                                    onDeleteClick = { deviceToDelete = it }
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(devices) { device ->
                                DeviceCard(
                                    device = device,
                                    onEditClick = { deviceToEdit = it },
                                    onDeleteClick = { deviceToDelete = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deviceToEdit?.let { currentDevice ->
        EditDeviceDialog(
            device = currentDevice,
            onDismiss = { deviceToEdit = null },
            onSave = { updatedDevice ->
                coroutineScope.launch {
                    DeviceRepository.updateDevice(currentDevice) {
                        updatedDevice
                    }
                }
                deviceToEdit = null
            }
        )
    }

    deviceToDelete?.let { currentDevice ->
        DeleteDeviceDialog(
            deviceName = currentDevice.name,
            onDismiss = { deviceToDelete = null },
            onConfirm = {
                coroutineScope.launch {
                    DeviceRepository.removeDevice(currentDevice.uuid)
                }
                deviceToDelete = null
            }
        )
    }
}

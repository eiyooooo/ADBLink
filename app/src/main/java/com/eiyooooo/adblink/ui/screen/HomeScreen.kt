package com.eiyooooo.adblink.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact, navController: NavController) {
    val context = LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    // 加载设备列表
//    LaunchedEffect(key1 = true) {
//        ComposeDeviceManager.loadDevices()
//    }

    // 处理下拉刷新
//    LaunchedEffect(isRefreshing) {
//        if (isRefreshing) {
//            isRefreshing = true
//            ComposeDeviceManager.loadDevices()
//            isRefreshing = false
////            pullRefreshState.endRefresh()
//        }
//    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
//                    PublicTools.createAddDeviceView(
//                        context,
//                        Device.getDefaultDevice(UUID.randomUUID().toString(), Device.TYPE_NORMAL),
//                        null
//                    ).show()
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Device")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
//                        pullRefreshState.startRefresh()
                    }
                },
                state = pullRefreshState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
            }
        }
    }
//                when (widthSizeClass) {
//                    WindowWidthSizeClass.Compact -> {
//                        // 窄屏使用列表布局
//                        LazyColumn(
//                            modifier = Modifier.fillMaxWidth(),
//                            verticalArrangement = Arrangement.spacedBy(8.dp)
//                        ) {
//                            items(ComposeDeviceManager.devices) { device ->
//                                DeviceCard(device = device)
//                            }
//                        }
//                    }
//
//                    WindowWidthSizeClass.Medium -> {
//                        // 中等宽度使用2列网格
//                        LazyVerticalGrid(
//                            columns = GridCells.Fixed(2),
//                            horizontalArrangement = Arrangement.spacedBy(8.dp),
//                            verticalArrangement = Arrangement.spacedBy(8.dp)
//                        ) {
//                            items(ComposeDeviceManager.devices) { device ->
//                                DeviceCard(device = device)
//                            }
//                        }
//                    }
//
//                    else -> {
//                        // 宽屏使用3列网格
//                        LazyVerticalGrid(
//                            columns = GridCells.Fixed(3),
//                            horizontalArrangement = Arrangement.spacedBy(8.dp),
//                            verticalArrangement = Arrangement.spacedBy(8.dp)
//                        ) {
//                            items(ComposeDeviceManager.devices) { device ->
//                                DeviceCard(device = device)
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun DeviceCard(device: Device) {
//    val context = LocalContext.current
//    val connectHelper = remember { ConnectHelper(context) }
//
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .clickable {
////                connectHelper.startConnect(device)
//            },
//        colors = CardDefaults.cardColors(
//            containerColor = MaterialTheme.colorScheme.surfaceVariant
//        )
//    ) {
//        Column(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(16.dp)
//        ) {
//            Text(
//                text = device.name,
//                fontSize = 18.sp,
//                fontWeight = FontWeight.Bold,
//                color = MaterialTheme.colorScheme.onSurfaceVariant
//            )
//
//            Spacer(modifier = Modifier.height(4.dp))
//
//            Text(
//                text = "IP: ${device.address}",
//                fontSize = 14.sp,
//                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
//            )
//
//            Text(
//                text = "连接方式: ${getConnectionTypeText(device.type)}",
//                fontSize = 14.sp,
//                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
//            )
//        }
//    }
//}
//
//// 辅助函数：根据设备类型返回连接方式文本
//fun getConnectionTypeText(type: Int): String {
//    return when (type) {
//        Device.TYPE_NORMAL -> "常规连接"
////        Device.TYPE_ADB -> "ADB连接"
////        Device.TYPE_WIFI -> "WIFI连接"
//        else -> "未知连接方式"
//    }
}

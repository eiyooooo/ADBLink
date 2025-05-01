package com.eiyooooo.adblink.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.adb.AdbManager
import com.eiyooooo.adblink.entity.Preferences
import com.eiyooooo.adblink.ui.component.ExpandableFab
import com.eiyooooo.adblink.ui.component.FabItem
import com.eiyooooo.adblink.ui.dialog.AdbTcpDialog
import com.eiyooooo.adblink.ui.dialog.AdbTlsDialog
import com.eiyooooo.adblink.ui.dialog.AdbUsbDialog
import com.eiyooooo.adblink.ui.dialog.InitFailedDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(navController: NavHostController, windowSizeClass: WindowSizeClass) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoutes.HOME

    val title = getRouteTitle(currentRoute)
    val showBackButton = shouldShowBackButton(currentRoute)

    var showUsbDeviceDialog by remember { mutableStateOf(false) }
    var showAdbTcpDeviceDialog by remember { mutableStateOf(false) }
    var showAdbTlsDeviceDialog by remember { mutableStateOf(false) }

    val widthSizeClass = windowSizeClass.widthSizeClass
    val heightSizeClass = windowSizeClass.heightSizeClass

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val showSnackbar: (String) -> Unit = { message ->
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    if (!AdbManager.initialized) {
        InitFailedDialog()
    }

    if (showUsbDeviceDialog) {
        AdbUsbDialog(onDismissRequest = { showUsbDeviceDialog = false })
    }

    if (showAdbTcpDeviceDialog) {
        AdbTcpDialog(
            showSnackbar = showSnackbar,
            onDismissRequest = { showAdbTcpDeviceDialog = false }
        )
    }

    if (showAdbTlsDeviceDialog) {
        AdbTlsDialog(
            showSnackbar = showSnackbar,
            onDismissRequest = { showAdbTlsDeviceDialog = false }
        )
    }

    when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            if (showBackButton) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back)
                                    )
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.home)) },
                            label = { Text(stringResource(R.string.home)) },
                            selected = currentRoute == NavRoutes.HOME,
                            onClick = {
                                if (currentRoute != NavRoutes.HOME) {
                                    navController.navigate(NavRoutes.HOME) {
                                        popUpTo(NavRoutes.HOME) { inclusive = true }
                                    }
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Filled.DeviceHub, contentDescription = stringResource(R.string.manage)) },
                            label = { Text(stringResource(R.string.manage)) },
                            selected = currentRoute == NavRoutes.MANAGE,
                            onClick = {
                                if (currentRoute != NavRoutes.MANAGE) {
                                    navController.navigate(NavRoutes.MANAGE) {
                                        popUpTo(NavRoutes.HOME)
                                    }
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings)) },
                            label = { Text(stringResource(R.string.settings)) },
                            selected = isSettingsRelatedRoute(currentRoute),
                            onClick = {
                                if (currentRoute != NavRoutes.SETTINGS) {
                                    navController.navigate(NavRoutes.SETTINGS) {
                                        popUpTo(NavRoutes.HOME)
                                    }
                                }
                            }
                        )
                    }
                },
                floatingActionButton = {
                    if (currentRoute == NavRoutes.HOME) {
                        ExpandableFab(
                            items = if (Preferences.enableUSB) {
                                listOf(
                                    FabItem(Icons.Filled.Usb, stringResource(R.string.adb_usb)) {
                                        showUsbDeviceDialog = true
                                    },
                                    FabItem(Icons.Filled.Wifi, stringResource(R.string.adb_tcp)) {
                                        showAdbTcpDeviceDialog = true
                                    },
                                    FabItem(Icons.Filled.Security, stringResource(R.string.adb_tls)) {
                                        showAdbTlsDeviceDialog = true
                                    }
                                )
                            } else {
                                listOf(
                                    FabItem(Icons.Filled.Wifi, stringResource(R.string.adb_tcp)) {
                                        showAdbTcpDeviceDialog = true
                                    },
                                    FabItem(Icons.Filled.Security, stringResource(R.string.adb_tls)) {
                                        showAdbTlsDeviceDialog = true
                                    }
                                )
                            },
                            icon = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_device)
                        )
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                AppNavHost(navController, widthSizeClass, innerPadding, showSnackbar)
            }
        }

        else -> {
            if (isSettingsSubRoute(currentRoute)) {
                navController.navigate(NavRoutes.SETTINGS) {
                    popUpTo(NavRoutes.SETTINGS)
                }
            }
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail {
                    NavigationRailItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.home)) },
                        label = { Text(stringResource(R.string.home)) },
                        selected = currentRoute == NavRoutes.HOME,
                        onClick = {
                            if (currentRoute != NavRoutes.HOME) {
                                navController.navigate(NavRoutes.HOME) {
                                    popUpTo(NavRoutes.HOME) { inclusive = true }
                                }
                            }
                        }
                    )
                    NavigationRailItem(
                        icon = { Icon(Icons.Filled.DeviceHub, contentDescription = stringResource(R.string.manage)) },
                        label = { Text(stringResource(R.string.manage)) },
                        selected = currentRoute == NavRoutes.MANAGE,
                        onClick = {
                            if (currentRoute != NavRoutes.MANAGE) {
                                navController.navigate(NavRoutes.MANAGE) {
                                    popUpTo(NavRoutes.HOME)
                                }
                            }
                        }
                    )
                    NavigationRailItem(
                        icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings)) },
                        label = { Text(stringResource(R.string.settings)) },
                        selected = isSettingsRelatedRoute(currentRoute),
                        onClick = {
                            if (currentRoute != NavRoutes.SETTINGS) {
                                navController.navigate(NavRoutes.SETTINGS) {
                                    popUpTo(NavRoutes.HOME)
                                }
                            }
                        }
                    )
                }

                Scaffold(
                    topBar = {
                        if (heightSizeClass != WindowHeightSizeClass.Compact) {
                            TopAppBar(
                                title = { Text(title) },
                                navigationIcon = {
                                    if (showBackButton) {
                                        IconButton(onClick = { navController.popBackStack() }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = stringResource(R.string.back)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    },
                    floatingActionButton = {
                        if (currentRoute == NavRoutes.HOME) {
                            ExpandableFab(
                                items = if (Preferences.enableUSB) {
                                    listOf(
                                        FabItem(Icons.Filled.Usb, stringResource(R.string.adb_usb)) {
                                            showUsbDeviceDialog = true
                                        },
                                        FabItem(Icons.Filled.Wifi, stringResource(R.string.adb_tcp)) {
                                            showAdbTcpDeviceDialog = true
                                        },
                                        FabItem(Icons.Filled.Security, stringResource(R.string.adb_tls)) {
                                            showAdbTlsDeviceDialog = true
                                        }
                                    )
                                } else {
                                    listOf(
                                        FabItem(Icons.Filled.Wifi, stringResource(R.string.adb_tcp)) {
                                            showAdbTcpDeviceDialog = true
                                        },
                                        FabItem(Icons.Filled.Security, stringResource(R.string.adb_tls)) {
                                            showAdbTlsDeviceDialog = true
                                        }
                                    )
                                },
                                icon = Icons.Default.Add,
                                contentDescription = stringResource(R.string.add_device)
                            )
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    AppNavHost(navController, widthSizeClass, innerPadding, showSnackbar)
                }
            }
        }
    }
}

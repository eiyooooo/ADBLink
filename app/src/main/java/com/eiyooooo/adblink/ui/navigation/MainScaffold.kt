package com.eiyooooo.adblink.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.eiyooooo.adblink.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(navController: NavHostController, windowSizeClass: WindowSizeClass) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoutes.HOME

    val title = getRouteTitle(currentRoute)
    val showBackButton = shouldShowBackButton(currentRoute)

    val widthSizeClass = windowSizeClass.widthSizeClass
    val heightSizeClass = windowSizeClass.heightSizeClass

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val showSnackbar: (String) -> Unit = { message ->
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
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
                            icon = { Icon(Icons.Filled.Devices, contentDescription = stringResource(R.string.manage)) },
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
                        icon = { Icon(Icons.Filled.Devices, contentDescription = stringResource(R.string.manage)) },
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
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    AppNavHost(navController, widthSizeClass, innerPadding, showSnackbar)
                }
            }
        }
    }
}

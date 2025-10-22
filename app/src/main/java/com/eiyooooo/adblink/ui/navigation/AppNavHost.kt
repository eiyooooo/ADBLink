package com.eiyooooo.adblink.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.eiyooooo.adblink.ui.screen.AboutScreen
import com.eiyooooo.adblink.ui.screen.CastSettingsScreen
import com.eiyooooo.adblink.ui.screen.ConnectionSettingsScreen
import com.eiyooooo.adblink.ui.screen.DefaultCastParametersSettingsScreen
import com.eiyooooo.adblink.ui.screen.DeviceDetailScreen
import com.eiyooooo.adblink.ui.screen.HomeScreen
import com.eiyooooo.adblink.ui.screen.IpScreen
import com.eiyooooo.adblink.ui.screen.LogScreen
import com.eiyooooo.adblink.ui.screen.ManageScreen
import com.eiyooooo.adblink.ui.screen.OtherSettingsScreen
import com.eiyooooo.adblink.ui.screen.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    widthSizeClass: WindowWidthSizeClass,
    innerPadding: PaddingValues
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(
            navController = navController,
            startDestination = NavRoutes.HOME,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            composable(route = NavRoutes.HOME) {
                HomeScreen(widthSizeClass, navController)
            }
            composable(route = NavRoutes.MANAGE) {
                ManageScreen(widthSizeClass)
            }
            composable(route = NavRoutes.SETTINGS) {
                SettingsScreen(widthSizeClass, navController)
            }
            composable(route = NavRoutes.SETTINGS_DEFAULT_CAST_PARAMETERS) {
                DefaultCastParametersSettingsScreen()
            }
            composable(route = NavRoutes.SETTINGS_CAST) {
                CastSettingsScreen()
            }
            composable(route = NavRoutes.SETTINGS_CONNECTION) {
                ConnectionSettingsScreen()
            }
            composable(route = NavRoutes.SETTINGS_OTHER) {
                OtherSettingsScreen(navController)
            }
            composable(route = NavRoutes.SETTINGS_OTHER_LOG) {
                LogScreen()
            }
            composable(route = NavRoutes.SETTINGS_OTHER_IP) {
                IpScreen()
            }
            composable(route = NavRoutes.SETTINGS_ABOUT) {
                AboutScreen()
            }
            composable(route = NavRoutes.DEVICE_DETAIL) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: ""
                val identifier = backStackEntry.arguments?.getString("identifier") ?: ""
                DeviceDetailScreen(
                    deviceType = type,
                    deviceIdentifier = identifier,
                    navController = navController
                )
            }
        }
    }
}

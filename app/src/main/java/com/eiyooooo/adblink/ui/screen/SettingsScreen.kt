package com.eiyooooo.adblink.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.ui.component.SettingsMenuItem
import com.eiyooooo.adblink.ui.navigation.NavRoutes

@Composable
fun SettingsScreen(
    widthSizeClass: WindowWidthSizeClass,
    navController: NavHostController,
    showSnackbar: (String) -> Unit
) {
    val compactScrollState = rememberScrollState()
    val isCompactScreen = widthSizeClass == WindowWidthSizeClass.Compact

    var selectedContent by remember { mutableStateOf(NavRoutes.SETTINGS_ABOUT) }
    val castScrollState = rememberScrollState()
    val defaultParamsScrollState = rememberScrollState()
    val otherScrollState = rememberScrollState()
    val ipScrollState = rememberScrollState()
    val aboutScrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isCompactScreen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(compactScrollState)
                    .padding(16.dp)
            ) {
                SettingsMenuItem(
                    title = stringResource(R.string.default_cast_parameters),
                    icon = Icons.Default.Settings,
                    onClick = {
                        navController.navigate(NavRoutes.SETTINGS_DEFAULT_CAST_PARAMETERS)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsMenuItem(
                    title = stringResource(R.string.cast_settings),
                    icon = Icons.Default.DisplaySettings,
                    onClick = {
                        navController.navigate(NavRoutes.SETTINGS_CAST)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsMenuItem(
                    title = stringResource(R.string.other),
                    icon = Icons.Default.Tune,
                    onClick = {
                        navController.navigate(NavRoutes.SETTINGS_OTHER)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                AboutContent()
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.3f)
                        .padding(end = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsMenuItem(
                        title = stringResource(R.string.default_cast_parameters),
                        icon = Icons.Default.Settings,
                        isSelected = selectedContent == NavRoutes.SETTINGS_DEFAULT_CAST_PARAMETERS,
                        onClick = {
                            selectedContent = NavRoutes.SETTINGS_DEFAULT_CAST_PARAMETERS
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsMenuItem(
                        title = stringResource(R.string.cast_settings),
                        icon = Icons.Default.DisplaySettings,
                        isSelected = selectedContent == NavRoutes.SETTINGS_CAST,
                        onClick = {
                            selectedContent = NavRoutes.SETTINGS_CAST
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsMenuItem(
                        title = stringResource(R.string.other),
                        icon = Icons.Default.Tune,
                        isSelected = selectedContent == NavRoutes.SETTINGS_OTHER,
                        onClick = {
                            selectedContent = NavRoutes.SETTINGS_OTHER
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsMenuItem(
                        title = stringResource(R.string.about),
                        icon = Icons.Default.Info,
                        isSelected = selectedContent == NavRoutes.SETTINGS_ABOUT,
                        onClick = {
                            selectedContent = NavRoutes.SETTINGS_ABOUT
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(0.7f)
                        .padding(start = 8.dp)
                        .let { modifier ->
                            if (selectedContent == NavRoutes.SETTINGS_OTHER_LOG) {
                                modifier
                            } else {
                                modifier.verticalScroll(
                                    when (selectedContent) {
                                        NavRoutes.SETTINGS_DEFAULT_CAST_PARAMETERS -> defaultParamsScrollState
                                        NavRoutes.SETTINGS_CAST -> castScrollState
                                        NavRoutes.SETTINGS_OTHER -> otherScrollState
                                        NavRoutes.SETTINGS_OTHER_IP -> ipScrollState
                                        NavRoutes.SETTINGS_ABOUT -> aboutScrollState
                                        else -> throw Exception("Unknown selected content")
                                    }
                                )
                            }
                        }
                ) {
                    when (selectedContent) {
                        NavRoutes.SETTINGS_DEFAULT_CAST_PARAMETERS -> DefaultCastParametersSettingsContent()
                        NavRoutes.SETTINGS_CAST -> CastSettingsContent()
                        NavRoutes.SETTINGS_OTHER -> OtherSettingsContent { selectedContent = it }
                        NavRoutes.SETTINGS_OTHER_IP -> IpContent(showSnackbar)
                        NavRoutes.SETTINGS_OTHER_LOG -> LogContent(showSnackbar)
                        NavRoutes.SETTINGS_ABOUT -> AboutContent()
                    }
                }
            }
        }
    }
}

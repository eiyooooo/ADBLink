package com.eiyooooo.adblink.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eiyooooo.adblink.R

object NavRoutes {
    const val HOME = "home"
    const val MANAGE = "manage"
    const val SETTINGS = "settings"
    const val SETTINGS_DEFAULT_CAST_PARAMETERS = "settings/default_cast_parameters"
    const val SETTINGS_CAST = "settings/cast"
    const val SETTINGS_OTHER = "settings/other"
    const val SETTINGS_OTHER_IP = "settings/other/ip"
    const val SETTINGS_OTHER_LOG = "settings/other/log"
    const val SETTINGS_ABOUT = "settings/about"
}

@Composable
fun getRouteTitle(route: String): String {
    return when (route) {
        NavRoutes.HOME -> stringResource(R.string.app_name)
        NavRoutes.MANAGE -> stringResource(R.string.device_management)
        NavRoutes.SETTINGS -> stringResource(R.string.settings)
        NavRoutes.SETTINGS_DEFAULT_CAST_PARAMETERS -> stringResource(R.string.default_cast_parameters)
        NavRoutes.SETTINGS_CAST -> stringResource(R.string.cast_settings)
        NavRoutes.SETTINGS_OTHER -> stringResource(R.string.other)
        NavRoutes.SETTINGS_ABOUT -> stringResource(R.string.about)
        NavRoutes.SETTINGS_OTHER_IP -> stringResource(R.string.ip_address)
        NavRoutes.SETTINGS_OTHER_LOG -> stringResource(R.string.log)
        else -> stringResource(R.string.app_name)
    }
}

fun shouldShowBackButton(route: String): Boolean {
    return route != NavRoutes.HOME && route != NavRoutes.MANAGE && route != NavRoutes.SETTINGS
}

fun isSettingsRelatedRoute(route: String): Boolean {
    return route.startsWith("settings")
}

fun isSettingsSubRoute(route: String): Boolean {
    return route.startsWith("settings/")
}

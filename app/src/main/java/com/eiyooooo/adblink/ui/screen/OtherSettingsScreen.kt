package com.eiyooooo.adblink.ui.screen

import android.app.Activity
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.entity.Preferences
import com.eiyooooo.adblink.ui.component.SettingClickableItem
import com.eiyooooo.adblink.ui.component.SettingDropdownItem
import com.eiyooooo.adblink.ui.component.SettingSwitchItem
import com.eiyooooo.adblink.ui.navigation.NavRoutes
import com.eiyooooo.adblink.util.FLog

@Composable
fun OtherSettingsContent(navController: NavController? = null, onSelectedContentChange: (String) -> Unit = {}) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val systemColor by Preferences.systemColorFlow.collectAsState(initial = Preferences.systemColor)
    val darkTheme by Preferences.darkThemeFlow.collectAsState(initial = Preferences.darkTheme)
    val setFullScreen by Preferences.setFullScreenFlow.collectAsState(initial = Preferences.setFullScreen)
    val enableLog by Preferences.enableLogFlow.collectAsState(initial = Preferences.enableLog)
    val appLanguage by Preferences.appLanguageFlow.collectAsState(initial = Preferences.appLanguage)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Column {
                SettingSwitchItem(
                    title = stringResource(R.string.use_system_color),
                    description = stringResource(R.string.use_system_color_description),
                    checked = systemColor,
                    onCheckedChange = {
                        Preferences.systemColor = it
                        (context as? Activity)?.recreate()
                    },
                    isFirst = true
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        val darkThemeList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(
                stringResource(R.string.follow_system),
                stringResource(R.string.always_off),
                stringResource(R.string.always_on)
            )
        } else {
            listOf(
                stringResource(R.string.always_off),
                stringResource(R.string.always_on)
            )
        }
        val currentThemeIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            darkTheme.coerceAtLeast(0)
        } else {
            when (darkTheme) {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> 0
                AppCompatDelegate.MODE_NIGHT_NO -> 0
                else -> 1
            }
        }
        SettingDropdownItem(
            title = stringResource(R.string.dark_theme),
            currentValue = darkThemeList[currentThemeIndex],
            options = darkThemeList,
            onValueChange = {
                Preferences.darkTheme = when (it) {
                    resources.getString(R.string.follow_system) -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    resources.getString(R.string.always_off) -> AppCompatDelegate.MODE_NIGHT_NO
                    resources.getString(R.string.always_on) -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> darkTheme
                }
                AppCompatDelegate.setDefaultNightMode(Preferences.darkTheme)
                (context as? Activity)?.recreate()
            },
            isFirst = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        val languageList = listOf(
            stringResource(R.string.system_language),
            stringResource(R.string.english),
            stringResource(R.string.simplified_chinese)
        )
        SettingDropdownItem(
            title = stringResource(R.string.app_language),
            currentValue = languageList[appLanguage],
            options = languageList,
            onValueChange = { language ->
                val newValue = when (language) {
                    resources.getString(R.string.system_language) -> 0
                    resources.getString(R.string.english) -> 1
                    resources.getString(R.string.simplified_chinese) -> 2
                    else -> 0
                }
                if (newValue != Preferences.appLanguage) {
                    Preferences.appLanguage = newValue
                    (context as? Activity)?.recreate()
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingSwitchItem(
            title = stringResource(R.string.full_screen),
            description = stringResource(R.string.full_screen_description),
            checked = setFullScreen,
            onCheckedChange = { Preferences.setFullScreen = it }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingSwitchItem(
            title = stringResource(R.string.enable_log),
            description = stringResource(R.string.enable_log_description),
            checked = enableLog,
            onCheckedChange = {
                if (it) {
                    FLog.start()
                } else {
                    FLog.stop()
                }
                Preferences.enableLog = it
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        AnimatedVisibility(
            visible = enableLog,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                SettingClickableItem(
                    title = stringResource(R.string.view_log),
                    onClick = {
                        navController?.run {
                            navigate(NavRoutes.SETTINGS_OTHER_LOG)
                        } ?: run {
                            onSelectedContentChange(NavRoutes.SETTINGS_OTHER_LOG)
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        SettingClickableItem(
            title = stringResource(R.string.view_local_ip),
            onClick = {
                navController?.run {
                    navigate(NavRoutes.SETTINGS_OTHER_IP)
                } ?: run {
                    onSelectedContentChange(NavRoutes.SETTINGS_OTHER_IP)
                }
            },
            isLast = true
        )
    }
}

@Composable
fun OtherSettingsScreen(navController: NavController) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            OtherSettingsContent(navController)
        }
    }
}

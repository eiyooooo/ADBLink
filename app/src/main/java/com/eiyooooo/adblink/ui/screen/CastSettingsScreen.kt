package com.eiyooooo.adblink.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.entity.Preferences
import com.eiyooooo.adblink.ui.component.SettingSwitchItem

@Composable
fun CastSettingsContent() {

    val castWakeDeviceOnConnect by Preferences.castWakeDeviceOnConnectFlow.collectAsState(initial = Preferences.castWakeDeviceOnConnect)
    val castTurnOffScreenOnConnect by Preferences.castTurnOffScreenOnConnectFlow.collectAsState(initial = Preferences.castTurnOffScreenOnConnect)
    val castLockDeviceOnDisconnect by Preferences.castLockDeviceOnDisconnectFlow.collectAsState(initial = Preferences.castLockDeviceOnDisconnect)
    val castTurnOnScreenOnDisconnect by Preferences.castTurnOnScreenOnDisconnectFlow.collectAsState(initial = Preferences.castTurnOnScreenOnDisconnect)
    val castKeepScreenAwake by Preferences.castKeepScreenAwakeFlow.collectAsState(initial = Preferences.castKeepScreenAwake)
    val defaultCastShowNavBar by Preferences.defaultCastShowNavBarFlow.collectAsState(initial = Preferences.defaultCastShowNavBar)

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        SettingSwitchItem(
            title = stringResource(R.string.cast_wake_device_on_connect),
            description = stringResource(R.string.cast_wake_device_on_connect_description),
            checked = castWakeDeviceOnConnect,
            onCheckedChange = {
                Preferences.castWakeDeviceOnConnect = it
                if (!it) {
                    Preferences.castTurnOffScreenOnConnect = false
                }
            },
            isFirst = true
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingSwitchItem(
            title = stringResource(R.string.cast_turn_off_screen_on_connect),
            description = stringResource(R.string.cast_turn_off_screen_on_connect_description),
            checked = castTurnOffScreenOnConnect,
            enabled = castWakeDeviceOnConnect,
            onCheckedChange = {
                Preferences.castTurnOffScreenOnConnect = it
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingSwitchItem(
            title = stringResource(R.string.cast_lock_device_on_disconnect),
            description = stringResource(R.string.cast_lock_device_on_disconnect_description),
            checked = castLockDeviceOnDisconnect,
            onCheckedChange = {
                Preferences.castLockDeviceOnDisconnect = it
                if (it) {
                    Preferences.castTurnOnScreenOnDisconnect = false
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingSwitchItem(
            title = stringResource(R.string.cast_turn_on_screen_on_disconnect),
            description = stringResource(R.string.cast_turn_on_screen_on_disconnect_description),
            checked = castTurnOnScreenOnDisconnect,
            enabled = !castLockDeviceOnDisconnect,
            onCheckedChange = {
                Preferences.castTurnOnScreenOnDisconnect = it
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingSwitchItem(
            title = stringResource(R.string.cast_keep_screen_awake),
            description = stringResource(R.string.cast_keep_screen_awake_description),
            checked = castKeepScreenAwake,
            onCheckedChange = { Preferences.castKeepScreenAwake = it }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingSwitchItem(
            title = stringResource(R.string.cast_default_show_nav_bar),
            description = stringResource(R.string.cast_default_show_nav_bar_description),
            checked = defaultCastShowNavBar,
            onCheckedChange = { Preferences.defaultCastShowNavBar = it },
            isLast = true
        )
    }
}

@Composable
fun CastSettingsScreen() {
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
            CastSettingsContent()
        }
    }
}

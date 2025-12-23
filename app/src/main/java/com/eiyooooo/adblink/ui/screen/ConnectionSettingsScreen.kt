package com.eiyooooo.adblink.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.adb.AdbManager
import com.eiyooooo.adblink.config.Preferences
import com.eiyooooo.adblink.ui.component.SettingClickableItem
import com.eiyooooo.adblink.ui.component.SettingDropdownItem
import com.eiyooooo.adblink.ui.component.SettingSwitchItem

@Composable
fun ConnectionSettingsContent() {
    val resources = LocalResources.current

    val enableDelayedAck by Preferences.enableDelayedAckFlow.collectAsState(initial = Preferences.enableDelayedAck)
    val enableUSB by Preferences.enableUSBFlow.collectAsState(initial = Preferences.enableUSB)
    val enableAdbDiscoverService by Preferences.enableAdbDiscoverServiceFlow.collectAsState(initial = Preferences.enableAdbDiscoverService)
    val adbConnectionTimeout by Preferences.adbConnectionTimeoutFlow.collectAsState(initial = Preferences.adbConnectionTimeout)

    var showRegenerateKeyDialog by remember { mutableStateOf(false) }

    val timeoutList = listOf(
        stringResource(R.string.timeout_5_seconds),
        stringResource(R.string.timeout_10_seconds),
        stringResource(R.string.timeout_20_seconds),
        stringResource(R.string.timeout_30_seconds)
    )
    val timeoutValues = listOf(5, 10, 20, 30)
    val currentTimeoutIndex = timeoutValues.indexOf(adbConnectionTimeout).let {
        if (it == -1) 1 else it
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        SettingSwitchItem(
            title = stringResource(R.string.enable_delayed_ack),
            description = stringResource(R.string.enable_delayed_ack_description),
            checked = enableDelayedAck,
            onCheckedChange = { Preferences.enableDelayedAck = it },
            isFirst = true
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingSwitchItem(
            title = stringResource(R.string.enable_usb),
            description = stringResource(R.string.enable_usb_description),
            checked = enableUSB,
            onCheckedChange = { Preferences.enableUSB = it }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingSwitchItem(
            title = stringResource(R.string.enable_adb_discover_service),
            description = stringResource(R.string.enable_adb_discover_service_description),
            checked = enableAdbDiscoverService,
            onCheckedChange = { enabled ->
                Preferences.enableAdbDiscoverService = enabled
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingDropdownItem(
            title = stringResource(R.string.adb_connection_timeout),
            description = stringResource(R.string.adb_connection_timeout_description),
            currentValue = timeoutList[currentTimeoutIndex],
            options = timeoutList,
            onValueChange = { selectedTimeout ->
                val timeoutValue = when (selectedTimeout) {
                    resources.getString(R.string.timeout_5_seconds) -> 5
                    resources.getString(R.string.timeout_10_seconds) -> 10
                    resources.getString(R.string.timeout_20_seconds) -> 20
                    resources.getString(R.string.timeout_30_seconds) -> 30
                    else -> 10
                }
                Preferences.adbConnectionTimeout = timeoutValue
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingClickableItem(
            title = stringResource(R.string.regenerate_adb_key),
            description = stringResource(R.string.current_adb_key, AdbManager.getAdbKeyPairName()),
            onClick = {
                showRegenerateKeyDialog = true
            },
            isLast = true
        )
    }

    LaunchedEffect(enableAdbDiscoverService) {
        AdbManager.setDiscoverServicesEnabled(enableAdbDiscoverService)
    }

    if (showRegenerateKeyDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateKeyDialog = false },
            title = { Text(text = stringResource(R.string.regenerate_adb_key)) },
            text = { Text(text = stringResource(R.string.regenerate_adb_key_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        AdbManager.recreateAdbKeyPair()
                        showRegenerateKeyDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRegenerateKeyDialog = false }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
        showRegenerateKeyDialog
    }
}

@Composable
fun ConnectionSettingsScreen() {
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
            ConnectionSettingsContent()
        }
    }
}

package com.eiyooooo.adblink.ui.screen

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.entity.Preferences
import com.eiyooooo.adblink.ui.component.SettingDropdownItem
import com.eiyooooo.adblink.ui.component.SettingSwitchItem

private val maxFpsList = listOf("60", "50", "40", "30", "25", "20", "15", "10")
private val maxVideoBitList = listOf("8", "6", "4", "2", "1")

@Composable
fun DefaultCastParametersSettingsContent() {
    val context = LocalContext.current

    val defaultCastMaxSize by Preferences.defaultCastMaxSizeFlow.collectAsState(initial = Preferences.defaultCastMaxSize)
    val defaultCastMaxFps by Preferences.defaultCastMaxFpsFlow.collectAsState(initial = Preferences.defaultCastMaxFps)
    val defaultCastMaxVideoBitrate by Preferences.defaultCastMaxVideoBitrateFlow.collectAsState(initial = Preferences.defaultCastMaxVideoBitrate)
    val defaultCastEnableAudio by Preferences.defaultCastEnableAudioFlow.collectAsState(initial = Preferences.defaultCastEnableAudio)
    val defaultCastClipboardSync by Preferences.defaultCastClipboardSyncFlow.collectAsState(initial = Preferences.defaultCastClipboardSync)
    val defaultCastPreferH265 by Preferences.defaultCastPreferH265Flow.collectAsState(initial = Preferences.defaultCastPreferH265)
    val defaultCastPreferOpus by Preferences.defaultCastPreferOpusFlow.collectAsState(initial = Preferences.defaultCastPreferOpus)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        SettingDropdownItem(
            title = stringResource(R.string.cast_max_size),
            description = stringResource(R.string.cast_max_size_description),
            currentValue = defaultCastMaxSize.let {
                if (it == 0) stringResource(R.string.cast_max_size_original) else it.toString()
            },
            options = listOf(stringResource(R.string.cast_max_size_original), "2560", "1920", "1600", "1280", "1024", "800"),
            onValueChange = { size ->
                val sizeValue = if (size == context.getString(R.string.cast_max_size_original)) 0 else size.toInt()
                Preferences.defaultCastMaxSize = sizeValue
            },
            isFirst = true
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingDropdownItem(
            title = stringResource(R.string.cast_max_fps),
            description = stringResource(R.string.cast_max_fps_description),
            currentValue = defaultCastMaxFps.toString(),
            options = maxFpsList,
            onValueChange = { fps ->
                Preferences.defaultCastMaxFps = fps.toInt()
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingDropdownItem(
            title = stringResource(R.string.cast_max_video_bitrate),
            description = stringResource(R.string.cast_max_video_bitrate_description),
            currentValue = defaultCastMaxVideoBitrate.toString(),
            options = maxVideoBitList,
            onValueChange = { bit ->
                Preferences.defaultCastMaxVideoBitrate = bit.toInt()
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingSwitchItem(
            title = stringResource(R.string.cast_enable_audio),
            description = stringResource(R.string.cast_enable_audio_description),
            checked = defaultCastEnableAudio,
            onCheckedChange = { Preferences.defaultCastEnableAudio = it }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingSwitchItem(
            title = stringResource(R.string.cast_clipboard_sync),
            description = stringResource(R.string.cast_clipboard_sync_description),
            checked = defaultCastClipboardSync,
            onCheckedChange = { Preferences.defaultCastClipboardSync = it }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingSwitchItem(
            title = stringResource(R.string.cast_prefer_h265),
            description = stringResource(R.string.cast_prefer_h265_description),
            checked = defaultCastPreferH265,
            onCheckedChange = { Preferences.defaultCastPreferH265 = it }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingSwitchItem(
            title = stringResource(R.string.cast_prefer_opus),
            description = stringResource(R.string.cast_prefer_opus_description),
            checked = defaultCastPreferOpus,
            onCheckedChange = { Preferences.defaultCastPreferOpus = it },
            isLast = true
        )
    }
}

@Composable
fun DefaultCastParametersSettingsScreen() {
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
            DefaultCastParametersSettingsContent()
        }
    }
}

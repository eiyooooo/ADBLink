package com.eiyooooo.adblink.ui.screen

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.BuildConfig
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.ui.component.SettingClickableItem
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity

@Composable
fun AboutContent() {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        SettingClickableItem(
            title = stringResource(R.string.project_website),
            onClick = {
                //TODO
            },
            isFirst = true
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingClickableItem(
            title = stringResource(R.string.instructions_for_use),
            onClick = {
                //TODO
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingClickableItem(
            title = stringResource(R.string.privacy_policy),
            onClick = {
                //TODO
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingClickableItem(
            title = stringResource(R.string.license),
            onClick = {
                context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingClickableItem(
            title = stringResource(R.string.version) + BuildConfig.VERSION_NAME,
            onClick = {
                //TODO
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SettingClickableItem(
            title = stringResource(R.string.github_message),
            onClick = {
                //TODO
            },
            isLast = true
        )
    }
}


@Composable
fun AboutScreen() {
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
            AboutContent()
        }
    }
}

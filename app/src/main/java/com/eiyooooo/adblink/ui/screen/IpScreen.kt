package com.eiyooooo.adblink.ui.screen

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.entity.SystemServices
import com.eiyooooo.adblink.util.getIp

@Composable
fun IpContent(showSnackbar: (String) -> Unit) {
    val context = LocalContext.current

    val ipv4List = remember { mutableStateListOf<String>() }
    val ipv6List = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        val list = getIp()
        ipv4List.clear()
        ipv4List.addAll(list.first)
        ipv6List.clear()
        ipv6List.addAll(list.second)
    }

    fun copyIpToClipboard(ip: String) {
        SystemServices.clipboardManager.setPrimaryClip(ClipData.newPlainText(ClipDescription.MIMETYPE_TEXT_PLAIN, ip))
        showSnackbar(context.getString(R.string.copied))
    }

    @Composable
    fun IpAddressCard(ip: String, onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            onClick = onClick
        ) {
            Text(
                text = ip,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    @Composable
    fun IpAddressSection(title: String, ipList: List<String>, modifier: Modifier = Modifier) {
        Column(modifier = modifier) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (ipList.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = stringResource(R.string.no_ip),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                for (ip in ipList) {
                    IpAddressCard(ip = ip) {
                        copyIpToClipboard(ip)
                    }
                }
            }
        }
    }

    Column {
        IpAddressSection(
            title = stringResource(R.string.ipv4),
            ipList = ipv4List,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        IpAddressSection(
            title = stringResource(R.string.ipv6),
            ipList = ipv6List,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun IpScreen(showSnackbar: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            IpContent(showSnackbar)
        }
    }
}

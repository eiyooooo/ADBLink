package com.eiyooooo.adblink.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter

@Composable
fun AdbKeyContent() {//TODO
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val privateKey = remember { File(context.filesDir, "private.key") }
    val publicKey = remember { File(context.filesDir, "public.key") }

    var publicKeyText by remember { mutableStateOf("") }
    var privateKeyText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        return@LaunchedEffect //TODO
        try {
            if (publicKey.exists()) {
                val publicKeyBytes = ByteArray(publicKey.length().toInt())
                FileInputStream(publicKey).use { stream ->
                    stream.read(publicKeyBytes)
                    publicKeyText = String(publicKeyBytes)
                }
            }

            if (privateKey.exists()) {
                val privateKeyBytes = ByteArray(privateKey.length().toInt())
                FileInputStream(privateKey).use { stream ->
                    stream.read(privateKeyBytes)
                    privateKeyText = String(privateKeyBytes)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun saveKeys() {
        return //TODO
        coroutineScope.launch {
            try {
                FileWriter(publicKey).use { writer ->
                    writer.write(publicKeyText)
                    writer.flush()
                }
                FileWriter(privateKey).use { writer ->
                    writer.write(privateKeyText)
                    writer.flush()
                }
//                AppData.keyPair = AdbKeyPair.read(privateKey, publicKey)
                Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun regenerateKeys() {
        // TODO: 实现重新生成密钥的功能
    }

    Column {
        Text(
            text = stringResource(R.string.public_key),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        OutlinedTextField(
            value = publicKeyText,
            onValueChange = { publicKeyText = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.private_key),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        OutlinedTextField(
            value = privateKeyText,
            onValueChange = { privateKeyText = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row {
                Button(
                    onClick = { regenerateKeys() },
                    modifier = Modifier.widthIn(min = 120.dp)
                ) {
                    Text(text = stringResource(R.string.regenerate))
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = { saveKeys() },
                    modifier = Modifier.widthIn(min = 120.dp)
                ) {
                    Text(text = stringResource(R.string.save))
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun AdbKeyScreen() {
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
            AdbKeyContent()
        }
    }
}

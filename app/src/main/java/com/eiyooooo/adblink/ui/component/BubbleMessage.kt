package com.eiyooooo.adblink.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BubbleMessage(message: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = message,
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

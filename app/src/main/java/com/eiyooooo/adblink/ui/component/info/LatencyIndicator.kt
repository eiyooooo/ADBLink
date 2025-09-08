package com.eiyooooo.adblink.ui.component.info

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.util.IpLatency
import com.eiyooooo.adblink.util.LatencyLevel

@Composable
fun LatencyIndicator(
    latency: IpLatency,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (latency.latencyLevel) {
        LatencyLevel.EXCELLENT -> Color(0xFF4CAF50) to "${latency.latencyMs}ms"
        LatencyLevel.GOOD -> Color(0xFF8BC34A) to "${latency.latencyMs}ms"
        LatencyLevel.FAIR -> Color(0xFFFFC107) to "${latency.latencyMs}ms"
        LatencyLevel.POOR -> Color(0xFFFF9800) to "${latency.latencyMs}ms"
        LatencyLevel.VERY_POOR -> Color(0xFFFF5722) to "${latency.latencyMs}ms"
        LatencyLevel.UNREACHABLE -> Color(0xFFF44336) to stringResource(R.string.unreachable)
    }

    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

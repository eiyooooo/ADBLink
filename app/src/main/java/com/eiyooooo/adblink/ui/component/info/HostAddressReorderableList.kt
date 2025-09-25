package com.eiyooooo.adblink.ui.component.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.util.IpLatency
import com.eiyooooo.adblink.util.isValidHostAddress
import com.eiyooooo.adblink.util.testMultipleLatencies
import sh.calvin.reorderable.ReorderableColumn

@Composable
fun HostAddressReorderableList(
    addresses: SnapshotStateList<String>,
    port: Int,
    showAddButton: Boolean,
    modifier: Modifier = Modifier
) {
    var listVersion by remember { mutableIntStateOf(0) }
    var ipLatencies by remember { mutableStateOf<Map<String, IpLatency>>(emptyMap()) }
    var testingIps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var hostInput by remember { mutableStateOf("") }
    var addErrorMessage by remember { mutableStateOf<String?>(null) }

    val membershipSnapshot by remember { derivedStateOf { addresses.toSet() } }

    LaunchedEffect(showAddButton) {
        if (!showAddButton) {
            showAddDialog = false
        }
    }

    LaunchedEffect(membershipSnapshot, port) {
        val snapshot = addresses.toList()
        if (snapshot.isEmpty()) {
            ipLatencies = emptyMap()
            testingIps = emptySet()
            return@LaunchedEffect
        }

        ipLatencies = ipLatencies.filterKeys { it in snapshot }
        testingIps = snapshot.toSet()

        testMultipleLatencies(snapshot, port) { ip, latency ->
            ipLatencies = ipLatencies + (ip to latency)
            testingIps = testingIps - ip
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.host_addresses_order),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                if (showAddButton) {
                    FilledTonalButton(
                        onClick = {
                            hostInput = ""
                            addErrorMessage = null
                            showAddDialog = true
                        },
                        shape = MaterialTheme.shapes.large,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.host_addresses_add_button))
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (showAddDialog && showAddButton) {
                AlertDialog(
                    onDismissRequest = {
                        showAddDialog = false
                        addErrorMessage = null
                    },
                    title = {
                        Text(
                            text = stringResource(R.string.host_addresses_add_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = hostInput,
                                onValueChange = {
                                    hostInput = it
                                    addErrorMessage = null
                                },
                                label = { Text(text = stringResource(R.string.host_address)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            addErrorMessage?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        val invalidHostString = stringResource(R.string.host_addresses_invalid_host)
                        val alreadyExistsString = stringResource(R.string.host_addresses_already_exists)
                        TextButton(
                            onClick = {
                                val trimmedHost = hostInput.trim()
                                hostInput = trimmedHost

                                when {
                                    trimmedHost.isEmpty() || !trimmedHost.isValidHostAddress() -> {
                                        addErrorMessage = invalidHostString
                                    }

                                    addresses.any { it.equals(trimmedHost, ignoreCase = true) } -> {
                                        addErrorMessage = alreadyExistsString
                                    }

                                    else -> {
                                        addresses.add(trimmedHost)
                                        listVersion++
                                        ipLatencies = ipLatencies - trimmedHost
                                        testingIps = testingIps + trimmedHost
                                        showAddDialog = false
                                        addErrorMessage = null
                                        hostInput = ""
                                    }
                                }
                            }
                        ) {
                            Text(text = stringResource(R.string.confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showAddDialog = false
                                addErrorMessage = null
                                hostInput = ""
                            }
                        ) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    }
                )
            }

            ReorderableColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                list = addresses.toList(),
                onSettle = { fromIndex, toIndex ->
                    if (addresses.isEmpty()) return@ReorderableColumn

                    val clampedFrom = fromIndex.coerceIn(0, addresses.lastIndex)
                    val clampedTo = toIndex.coerceIn(0, addresses.size)

                    if (clampedFrom == clampedTo || clampedFrom !in addresses.indices) {
                        return@ReorderableColumn
                    }

                    val item = addresses.removeAt(clampedFrom)
                    val insertIndex = clampedTo.coerceIn(0, addresses.size)

                    addresses.add(insertIndex, item)
                    listVersion++
                },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) { index, ip, _ ->
                key("$ip-$index-$listVersion") {
                    ReorderableItem {
                        val moveUpLabel = stringResource(R.string.move_up)
                        val moveDownLabel = stringResource(R.string.move_down)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    customActions = listOf(
                                        CustomAccessibilityAction(
                                            label = moveUpLabel,
                                            action = {
                                                val currentIndex = addresses.indexOf(ip)
                                                if (currentIndex > 0) {
                                                    val movedItem = addresses.removeAt(currentIndex)
                                                    addresses.add(currentIndex - 1, movedItem)
                                                    listVersion++
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                        ),
                                        CustomAccessibilityAction(
                                            label = moveDownLabel,
                                            action = {
                                                val currentIndex = addresses.indexOf(ip)
                                                if (currentIndex >= 0 && currentIndex < addresses.lastIndex) {
                                                    val movedItem = addresses.removeAt(currentIndex)
                                                    addresses.add(currentIndex + 1, movedItem)
                                                    listVersion++
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                        )
                                    )
                                },
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 2.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = ip,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                supportingContent = {
                                    when {
                                        testingIps.contains(ip) -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )

                                                Text(
                                                    text = stringResource(R.string.testing_latency),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        ipLatencies[ip] != null -> {
                                            LatencyIndicator(latency = ipLatencies.getValue(ip))
                                        }

                                        else -> {
                                            Text(
                                                text = stringResource(R.string.latency_unknown),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                leadingContent = {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        tonalElevation = 0.dp,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                },
                                trailingContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (index in addresses.indices) {
                                                    val removedIp = addresses.removeAt(index)
                                                    ipLatencies = ipLatencies - removedIp
                                                    testingIps = testingIps - removedIp
                                                    listVersion++
                                                }
                                            },
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = stringResource(R.string.remove_ip_address)
                                            )
                                        }

                                        IconButton(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .draggableHandle()
                                                .clearAndSetSemantics { },
                                            onClick = {}
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DragHandle,
                                                contentDescription = stringResource(R.string.reorder_handle),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

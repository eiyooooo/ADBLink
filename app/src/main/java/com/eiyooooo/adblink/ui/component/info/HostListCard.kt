package com.eiyooooo.adblink.ui.component.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import com.eiyooooo.adblink.model.connection.ConnectionEndpoint
import com.eiyooooo.adblink.model.connection.ConnectionHost
import com.eiyooooo.adblink.model.connection.ConnectionType
import com.eiyooooo.adblink.util.IpLatency
import com.eiyooooo.adblink.util.isValidHostAddress
import com.eiyooooo.adblink.util.testLatency
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableColumn

@Composable
fun HostListCard(
    hosts: SnapshotStateList<ConnectionHost>,
    endpoints: List<ConnectionEndpoint>,
    showAddButton: Boolean,
    modifier: Modifier = Modifier,
    onRemoveHost: ((removedHost: ConnectionHost, index: Int, restore: () -> Unit) -> Unit)? = null
) {
    var listVersion by remember { mutableIntStateOf(0) }
    var hostLatencies by remember { mutableStateOf<Map<String, IpLatency>>(emptyMap()) }
    var testingHosts by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showAddDialog by remember { mutableStateOf(false) }
    var hostInput by remember { mutableStateOf("") }
    var addErrorMessage by remember { mutableStateOf<String?>(null) }

    val membershipSnapshot by remember {
        derivedStateOf { hosts.map { it.key }.toSet() }
    }

    LaunchedEffect(Unit) {
        val deduped = hosts.distinctBy { it.key }
        if (deduped.size != hosts.size) {
            hosts.clear()
            hosts.addAll(deduped)
            listVersion++
        }
    }

    LaunchedEffect(showAddButton) {
        if (!showAddButton) {
            showAddDialog = false
        }
    }

    LaunchedEffect(membershipSnapshot, endpoints) {
        val snapshotHosts = hosts.mapNotNull { host ->
            val trimmed = host.host.trim()
            trimmed.takeIf { it.isNotBlank() }?.let { host.copy(host = it) }
        }.distinctBy { it.key }

        if (snapshotHosts.isEmpty()) {
            hostLatencies = emptyMap()
            testingHosts = emptySet()
            return@LaunchedEffect
        }

        hostLatencies = hostLatencies.filterKeys { it in membershipSnapshot }
        testingHosts = membershipSnapshot.toSet()

        val sortedEndpoints = endpoints.sortedWith(
            compareBy<ConnectionEndpoint> {
                it.type != ConnectionType.TLS
            }.thenBy {
                it.port
            }
        ).distinctBy {
            it.port
        }

        if (sortedEndpoints.isEmpty()) {
            testingHosts = emptySet()
            return@LaunchedEffect
        }

        snapshotHosts.forEach { host ->
            val key = host.key
            launch {
                var latencyResult = IpLatency(host.host, -1, false)
                for (endpoint in sortedEndpoints) {
                    latencyResult = testLatency(host.host, endpoint.port)
                    if (latencyResult.isReachable) {
                        break
                    }
                }

                hostLatencies = hostLatencies + (key to latencyResult)
                testingHosts = testingHosts - key
            }
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
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_host_address)
                        )
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
                            text = stringResource(R.string.add_host_address),
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
                        val invalidHostString = stringResource(R.string.invalid_host)
                        val alreadyExistsString = stringResource(R.string.host_addresses_already_exists)
                        TextButton(
                            onClick = {
                                val trimmedHost = hostInput.trim()
                                hostInput = trimmedHost

                                when {
                                    trimmedHost.isEmpty() || !trimmedHost.isValidHostAddress() -> {
                                        addErrorMessage = invalidHostString
                                    }

                                    hosts.any { it.host.equals(trimmedHost, ignoreCase = true) } -> {
                                        addErrorMessage = alreadyExistsString
                                    }

                                    else -> {
                                        val connectionHost = ConnectionHost(
                                            host = trimmedHost,
                                            manuallyAdded = true
                                        )
                                        hosts.add(connectionHost)
                                        listVersion++
                                        val newKey = connectionHost.key
                                        hostLatencies = hostLatencies - newKey
                                        testingHosts = testingHosts + newKey
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
                list = hosts.toList(),
                onSettle = { fromIndex, toIndex ->
                    if (hosts.isEmpty()) return@ReorderableColumn

                    val clampedFrom = fromIndex.coerceIn(0, hosts.lastIndex)
                    val clampedTo = toIndex.coerceIn(0, hosts.size)

                    if (clampedFrom == clampedTo || clampedFrom !in hosts.indices) {
                        return@ReorderableColumn
                    }

                    val item = hosts.removeAt(clampedFrom)
                    val insertIndex = clampedTo.coerceIn(0, hosts.size)

                    hosts.add(insertIndex, item)
                    listVersion++
                },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) { index, host, _ ->
                val itemKey = host.key
                key("$itemKey-$index-$listVersion") {
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
                                                val currentIndex = hosts.indexOfFirst { it.key == itemKey }
                                                if (currentIndex > 0) {
                                                    val movedItem = hosts.removeAt(currentIndex)
                                                    hosts.add(currentIndex - 1, movedItem)
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
                                                val currentIndex = hosts.indexOfFirst { it.key == itemKey }
                                                if (currentIndex >= 0 && currentIndex < hosts.lastIndex) {
                                                    val movedItem = hosts.removeAt(currentIndex)
                                                    hosts.add(currentIndex + 1, movedItem)
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
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

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = host.host,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    when {
                                        testingHosts.contains(itemKey) -> {
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

                                        hostLatencies[itemKey] != null -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                LatencyIndicator(
                                                    latency = hostLatencies.getValue(itemKey),
                                                    modifier = Modifier.alignByBaseline()
                                                )
                                            }
                                        }

                                        else -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.unknown),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.alignByBaseline()
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (index in hosts.indices) {
                                                val removalIndex = index
                                                val removedHost = hosts.removeAt(removalIndex)
                                                val removedKey = removedHost.key
                                                hostLatencies = hostLatencies - removedKey
                                                testingHosts = testingHosts - removedKey
                                                listVersion++

                                                onRemoveHost?.let { callback ->
                                                    var restoreHandled = false
                                                    val restoreAction: () -> Unit = action@{
                                                        if (restoreHandled) return@action
                                                        restoreHandled = true
                                                        val insertIndex = removalIndex.coerceIn(0, hosts.size)
                                                        hosts.add(insertIndex, removedHost)
                                                        val newKey = removedHost.key
                                                        hostLatencies = hostLatencies - newKey
                                                        testingHosts = testingHosts + newKey
                                                        listVersion++
                                                    }
                                                    callback(removedHost, removalIndex, restoreAction)
                                                }
                                            }
                                        },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.remove_host_address)
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
                        }
                    }
                }
            }
        }
    }
}

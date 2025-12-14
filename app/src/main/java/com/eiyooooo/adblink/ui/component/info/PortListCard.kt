package com.eiyooooo.adblink.ui.component.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eiyooooo.adblink.R
import com.eiyooooo.adblink.data.ConnectionEndpoint
import com.eiyooooo.adblink.entity.ConnectionType

@Composable
fun PortListCard(
    endpoints: List<ConnectionEndpoint>,
    showAddButton: Boolean,
    onAddEndpoint: (ConnectionEndpoint) -> Unit,
    onRemoveEndpoint: (ConnectionEndpoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val resources = LocalResources.current

    var showAddDialog by remember { mutableStateOf(false) }
    var portInput by remember { mutableStateOf("") }
    var addErrorMessage by remember { mutableStateOf<String?>(null) }

    val manualEndpoints = endpoints.filter { it.manuallyAdded }
    val automaticEndpoints = endpoints.filterNot { it.manuallyAdded }
    val orderedEndpoints = manualEndpoints + automaticEndpoints

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.connection_ports_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                if (showAddButton) {
                    FilledTonalButton(
                        onClick = {
                            portInput = ""
                            addErrorMessage = null
                            showAddDialog = true
                        },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_connection_port)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (orderedEndpoints.isEmpty()) {
                Text(
                    text = stringResource(R.string.connection_ports_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                )
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    orderedEndpoints.forEach { endpoint ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (endpoint.manuallyAdded) {
                                        Text(
                                            text = stringResource(R.string.manual_added),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(
                                            text = when (endpoint.type) {
                                                ConnectionType.TCP -> stringResource(R.string.connection_type_tcp)
                                                ConnectionType.TLS -> stringResource(R.string.connection_type_tls_connect)
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = endpoint.port.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                IconButton(
                                    onClick = { onRemoveEndpoint(endpoint) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.remove_connection_port)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog && showAddButton) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                addErrorMessage = null
            },
            title = {
                Text(
                    text = stringResource(R.string.add_connection_port),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = {
                            portInput = it
                            addErrorMessage = null
                        },
                        label = {
                            Text(text = stringResource(R.string.connection_port_input_label))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = addErrorMessage != null
                    )

                    addErrorMessage?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val portNumber = portInput.trim().toIntOrNull()
                        if (portNumber == null || portNumber !in 1..65535) {
                            addErrorMessage = resources.getString(R.string.connection_port_invalid_error)
                            return@TextButton
                        }

                        val newEndpoint = ConnectionEndpoint(
                            port = portNumber,
                            type = ConnectionType.TCP, // Default to TCP for manually added ports
                            manuallyAdded = true
                        )
                        onAddEndpoint(newEndpoint)
                        showAddDialog = false
                        addErrorMessage = null
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
                    }
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
    showAddDialog
    addErrorMessage
}

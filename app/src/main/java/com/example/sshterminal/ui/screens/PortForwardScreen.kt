package com.example.sshterminal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sshterminal.domain.model.PortForward
import com.example.sshterminal.domain.model.PortForwardType
import com.example.sshterminal.viewmodel.PortForwardViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardScreen(
    hostId: Long,
    hostName: String,
    viewModel: PortForwardViewModel = koinViewModel(),
    onBack: () -> Unit
) {
    val portForwards by viewModel.portForwards.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var portForwardToDelete by remember { mutableStateOf<PortForward?>(null) }

    LaunchedEffect(hostId) {
        viewModel.loadPortForwards(hostId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Port Forwarding - $hostName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Port Forward")
            }
        }
    ) { padding ->
        if (portForwards.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No port forwards configured",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap + to add a port forward",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(portForwards, key = { it.id }) { portForward ->
                    PortForwardCard(
                        portForward = portForward,
                        onToggle = { enabled ->
                            viewModel.setEnabled(portForward.id, enabled)
                        },
                        onDelete = { portForwardToDelete = portForward }
                    )
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        AddPortForwardDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { type, localPort, remoteHost, remotePort, name ->
                viewModel.addPortForward(type, localPort, remoteHost, remotePort, name)
                showAddDialog = false
            }
        )
    }

    // Delete confirmation
    portForwardToDelete?.let { pf ->
        AlertDialog(
            onDismissRequest = { portForwardToDelete = null },
            title = { Text("Delete Port Forward") },
            text = {
                Text("Delete ${pf.name ?: "this port forward"}?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePortForward(pf)
                        portForwardToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { portForwardToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PortForwardCard(
    portForward: PortForward,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                portForward.name?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = when (portForward.type) {
                        PortForwardType.LOCAL -> "Local: ${portForward.localPort} → ${portForward.remoteHost}:${portForward.remotePort}"
                        PortForwardType.REMOTE -> "Remote: ${portForward.remotePort} → localhost:${portForward.localPort}"
                        PortForwardType.DYNAMIC -> "SOCKS: localhost:${portForward.localPort}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when (portForward.type) {
                        PortForwardType.LOCAL -> "Local Forward (-L)"
                        PortForwardType.REMOTE -> "Remote Forward (-R)"
                        PortForwardType.DYNAMIC -> "Dynamic Forward (-D)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = portForward.enabled,
                    onCheckedChange = onToggle
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPortForwardDialog(
    onDismiss: () -> Unit,
    onAdd: (PortForwardType, Int, String, Int, String?) -> Unit
) {
    var type by remember { mutableStateOf(PortForwardType.LOCAL) }
    var localPort by remember { mutableStateOf("") }
    var remoteHost by remember { mutableStateOf("localhost") }
    var remotePort by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    var expanded by remember { mutableStateOf(false) }

    val isValid = localPort.toIntOrNull() in 1..65535 &&
            (type == PortForwardType.DYNAMIC || (remoteHost.isNotBlank() && remotePort.toIntOrNull() in 1..65535))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Port Forward") },
        text = {
            Column {
                // Type selector
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = when (type) {
                            PortForwardType.LOCAL -> "Local Forward (-L)"
                            PortForwardType.REMOTE -> "Remote Forward (-R)"
                            PortForwardType.DYNAMIC -> "Dynamic/SOCKS (-D)"
                        },
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        PortForwardType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (t) {
                                            PortForwardType.LOCAL -> "Local Forward (-L)"
                                            PortForwardType.REMOTE -> "Remote Forward (-R)"
                                            PortForwardType.DYNAMIC -> "Dynamic/SOCKS (-D)"
                                        }
                                    )
                                },
                                onClick = {
                                    type = t
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Local port
                OutlinedTextField(
                    value = localPort,
                    onValueChange = { localPort = it.filter { c -> c.isDigit() } },
                    label = { Text("Local Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (type != PortForwardType.DYNAMIC) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Remote host
                    OutlinedTextField(
                        value = remoteHost,
                        onValueChange = { remoteHost = it },
                        label = { Text("Remote Host") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Remote port
                    OutlinedTextField(
                        value = remotePort,
                        onValueChange = { remotePort = it.filter { c -> c.isDigit() } },
                        label = { Text("Remote Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Optional name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    placeholder = { Text("e.g., MySQL, Redis") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(
                        type,
                        localPort.toInt(),
                        remoteHost,
                        remotePort.toIntOrNull() ?: 0,
                        name.ifBlank { null }
                    )
                },
                enabled = isValid
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

package com.example.sshterminal.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sshterminal.domain.model.Host
import com.example.sshterminal.domain.model.SSHKey
import com.example.sshterminal.viewmodel.HostViewModel
import com.example.sshterminal.viewmodel.KeyViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditScreen(
    hostId: Long?,
    hostViewModel: HostViewModel = koinViewModel(),
    keyViewModel: KeyViewModel = koinViewModel(),
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val keys by keyViewModel.keys.collectAsState()

    var name by remember { mutableStateOf("") }
    var hostname by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var selectedKeyAlias by remember { mutableStateOf<String?>(null) }
    var usePassword by remember { mutableStateOf(false) }

    var isEditing by remember { mutableStateOf(hostId != null) }
    var existingHost by remember { mutableStateOf<Host?>(null) }

    // Load existing host if editing
    LaunchedEffect(hostId) {
        if (hostId != null) {
            hostViewModel.getHostById(hostId) { host ->
                if (host != null) {
                    existingHost = host
                    name = host.name
                    hostname = host.hostname
                    port = host.port.toString()
                    username = host.username
                    selectedKeyAlias = host.keyAlias
                    usePassword = host.usePassword
                }
            }
        }
    }

    val isValid = name.isNotBlank() &&
                  hostname.isNotBlank() &&
                  port.toIntOrNull() in 1..65535 &&
                  username.isNotBlank() &&
                  (selectedKeyAlias != null || usePassword)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Host" else "New Host") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (isValid) {
                FloatingActionButton(
                    onClick = {
                        val host = Host(
                            id = existingHost?.id ?: 0,
                            name = name.trim(),
                            hostname = hostname.trim(),
                            port = port.toIntOrNull() ?: 22,
                            username = username.trim(),
                            keyAlias = selectedKeyAlias,
                            usePassword = usePassword,
                            hostKeyFingerprint = existingHost?.hostKeyFingerprint,
                            lastConnected = existingHost?.lastConnected,
                            createdAt = existingHost?.createdAt ?: System.currentTimeMillis()
                        )

                        if (isEditing) {
                            hostViewModel.updateHost(host)
                        } else {
                            hostViewModel.addHost(host)
                        }
                        onSaved()
                    }
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("My Server") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = hostname,
                onValueChange = { hostname = it },
                label = { Text("Hostname / IP") },
                placeholder = { Text("192.168.1.100 or example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                placeholder = { Text("root") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Authentication",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Key selection dropdown
            KeySelectionDropdown(
                keys = keys,
                selectedAlias = selectedKeyAlias,
                onKeySelected = { alias ->
                    selectedKeyAlias = alias
                    if (alias != null) {
                        usePassword = false
                    }
                }
            )

            // Quick select button for first available key
            if (keys.isNotEmpty() && selectedKeyAlias == null) {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.TextButton(
                    onClick = {
                        selectedKeyAlias = keys.first().alias
                        usePassword = false
                    }
                ) {
                    Text("Use ${keys.first().alias.removePrefix("ssh_key_")} key")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Password auth toggle
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column {
                    Text("Use Password")
                    Text(
                        "Ask for password when connecting",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = usePassword,
                    onCheckedChange = {
                        usePassword = it
                        if (it) {
                            selectedKeyAlias = null
                        }
                    }
                )
            }

            if (!isValid) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = when {
                        name.isBlank() -> "Please enter a name"
                        hostname.isBlank() -> "Please enter a hostname"
                        port.toIntOrNull() !in 1..65535 -> "Port must be 1-65535"
                        username.isBlank() -> "Please enter a username"
                        selectedKeyAlias == null && !usePassword -> "Select a key or enable password auth"
                        else -> ""
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeySelectionDropdown(
    keys: List<SSHKey>,
    selectedAlias: String?,
    onKeySelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedKey = keys.find { it.alias == selectedAlias }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedKey?.alias?.removePrefix("ssh_key_") ?: "Select a key",
            onValueChange = { },
            readOnly = true,
            label = { Text("SSH Key") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onKeySelected(null)
                    expanded = false
                }
            )

            keys.forEach { key ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(key.alias.removePrefix("ssh_key_"))
                            Text(
                                key.type.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onKeySelected(key.alias)
                        expanded = false
                    }
                )
            }
        }
    }
}

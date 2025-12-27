package com.example.sshterminal.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.sshterminal.ui.components.TerminalAndroidView
import com.example.sshterminal.ui.components.TerminalView
import com.example.sshterminal.viewmodel.TerminalState
import com.example.sshterminal.viewmodel.TerminalViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    hostId: Long,
    viewModel: TerminalViewModel = koinViewModel(),
    onDisconnect: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val hostKeyRequest by viewModel.hostKeyVerification.collectAsState()
    val passwordRequest by viewModel.passwordRequest.collectAsState()

    val context = LocalContext.current

    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var terminalView by remember { mutableStateOf<TerminalAndroidView?>(null) }

    // Connect on screen load
    LaunchedEffect(hostId) {
        viewModel.connect(hostId)
    }

    // Handle terminal data
    LaunchedEffect(Unit) {
        viewModel.terminalData.collect { data ->
            terminalView?.processData(data)
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnect()
        }
    }

    // Handle back press
    BackHandler {
        if (state is TerminalState.Connected) {
            showDisconnectConfirm = true
        } else {
            onDisconnect()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (val s = state) {
                            is TerminalState.Connected -> s.hostName
                            is TerminalState.Connecting -> "Connecting..."
                            is TerminalState.Disconnected -> "Disconnected"
                            is TerminalState.Error -> "Error"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state is TerminalState.Connected) {
                                showDisconnectConfirm = true
                            } else {
                                onDisconnect()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state is TerminalState.Connected) {
                        IconButton(onClick = { showDisconnectConfirm = true }) {
                            Icon(Icons.Default.Close, contentDescription = "Disconnect")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val s = state) {
                is TerminalState.Connecting -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.Green)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Connecting...", color = Color.White)
                        }
                    }
                }

                is TerminalState.Connected -> {
                    // Terminal view
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        TerminalView(
                            modifier = Modifier.fillMaxSize(),
                            onSendData = { data -> viewModel.sendData(data) },
                            onSizeChanged = { cols, rows ->
                                viewModel.resizeTerminal(cols, rows)
                            },
                            onViewCreated = { view ->
                                terminalView = view
                            }
                        )
                    }

                    // Special keys toolbar
                    SpecialKeysToolbar(
                        onSendCtrl = { char -> terminalView?.sendCtrl(char) },
                        onSendEscape = { terminalView?.sendEscape() },
                        onSendTab = { terminalView?.sendTab() },
                        onSendArrow = { dir -> terminalView?.sendArrow(dir) },
                        onSendText = { text -> viewModel.sendData(text.toByteArray(Charsets.UTF_8)) }
                    )
                }

                is TerminalState.Disconnected -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Disconnected", color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.connect(hostId) }) {
                                Text("Reconnect")
                            }
                        }
                    }
                }

                is TerminalState.Error -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Error", color = Color.Red)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(s.message, color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row {
                                Button(onClick = { viewModel.connect(hostId) }) {
                                    Text("Retry")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(onClick = onDisconnect) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Host key verification dialog
    hostKeyRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { viewModel.rejectHostKey() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (request.isKeyChanged)
                            Icons.Default.Close
                        else
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = if (request.isKeyChanged)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (request.isKeyChanged) "Host Key Changed!" else "New Host Key",
                        color = if (request.isKeyChanged)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column {
                    if (request.isKeyChanged) {
                        Text(
                            "WARNING: The host key for this server has changed!",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "This could indicate a man-in-the-middle attack, or the server has been reconfigured.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text("The authenticity of this host cannot be established.")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Host info
                    Text(
                        "Host: ${request.hostname}:${request.port}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Algorithm
                    Text(
                        "Algorithm: ${request.algorithm}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // New fingerprint
                    Text(
                        if (request.isKeyChanged) "New fingerprint:" else "Fingerprint:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        request.fingerprint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Show previous fingerprint if key changed
                    if (request.isKeyChanged && request.previousFingerprint != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Previous fingerprint:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            request.previousFingerprint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (request.isKeyChanged)
                            "Are you sure you want to continue? Only accept if you know the server was reconfigured."
                        else
                            "Do you want to continue connecting and save this key?",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.acceptHostKey() },
                    colors = if (request.isKeyChanged) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(if (request.isKeyChanged) "Accept Anyway" else "Accept & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.rejectHostKey() }) {
                    Text("Reject")
                }
            }
        )
    }

    // Password dialog
    passwordRequest?.let { request ->
        var password by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { viewModel.cancelPasswordRequest() },
            title = { Text("Password Required") },
            text = {
                Column {
                    Text("Enter password for ${request.username}@${request.hostname}")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.connectWithPassword(password) },
                    enabled = password.isNotEmpty()
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPasswordRequest() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Disconnect confirmation dialog
    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Disconnect?") },
            text = { Text("Are you sure you want to disconnect from this session?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectConfirm = false
                        viewModel.disconnect()
                        onDisconnect()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SpecialKeysToolbar(
    onSendCtrl: (Char) -> Unit,
    onSendEscape: () -> Unit,
    onSendTab: () -> Unit,
    onSendArrow: (String) -> Unit,
    onSendText: (String) -> Unit = {}
) {
    var ctrlMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2D2D2D))
    ) {
        // Row 1: Control keys and arrows
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Escape key
            SpecialKeyButton("ESC") { onSendEscape() }

            // Tab key
            SpecialKeyButton("TAB") { onSendTab() }

            // Ctrl toggle
            SpecialKeyButton(
                label = "Ctrl",
                isActive = ctrlMode
            ) { ctrlMode = !ctrlMode }

            // Common Ctrl combinations
            SpecialKeyButton("^C") { onSendCtrl('c') }
            SpecialKeyButton("^D") { onSendCtrl('d') }
            SpecialKeyButton("^Z") { onSendCtrl('z') }
            SpecialKeyButton("^L") { onSendCtrl('l') }
            SpecialKeyButton("^A") { onSendCtrl('a') }
            SpecialKeyButton("^E") { onSendCtrl('e') }
            SpecialKeyButton("^R") { onSendCtrl('r') }
            SpecialKeyButton("^W") { onSendCtrl('w') }

            Spacer(modifier = Modifier.width(8.dp))

            // Arrow keys
            IconButton(
                onClick = { onSendArrow("up") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, "Up", tint = Color.White)
            }
            IconButton(
                onClick = { onSendArrow("down") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, "Down", tint = Color.White)
            }
            IconButton(
                onClick = { onSendArrow("left") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left", tint = Color.White)
            }
            IconButton(
                onClick = { onSendArrow("right") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right", tint = Color.White)
            }
        }

        // Row 2: Common symbols (important for command line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Path and common symbols
            SpecialKeyButton("/") { onSendText("/") }
            SpecialKeyButton("\\") { onSendText("\\") }
            SpecialKeyButton("-") { onSendText("-") }
            SpecialKeyButton("_") { onSendText("_") }
            SpecialKeyButton(".") { onSendText(".") }
            SpecialKeyButton("~") { onSendText("~") }

            Spacer(modifier = Modifier.width(4.dp))

            // Pipe and redirection
            SpecialKeyButton("|") { onSendText("|") }
            SpecialKeyButton(">") { onSendText(">") }
            SpecialKeyButton("<") { onSendText("<") }
            SpecialKeyButton("&") { onSendText("&") }

            Spacer(modifier = Modifier.width(4.dp))

            // Quotes and brackets
            SpecialKeyButton("\"") { onSendText("\"") }
            SpecialKeyButton("'") { onSendText("'") }
            SpecialKeyButton("`") { onSendText("`") }
            SpecialKeyButton("(") { onSendText("(") }
            SpecialKeyButton(")") { onSendText(")") }
            SpecialKeyButton("[") { onSendText("[") }
            SpecialKeyButton("]") { onSendText("]") }
            SpecialKeyButton("{") { onSendText("{") }
            SpecialKeyButton("}") { onSendText("}") }

            Spacer(modifier = Modifier.width(4.dp))

            // Other useful symbols
            SpecialKeyButton("$") { onSendText("\$") }
            SpecialKeyButton("*") { onSendText("*") }
            SpecialKeyButton("?") { onSendText("?") }
            SpecialKeyButton("#") { onSendText("#") }
            SpecialKeyButton("@") { onSendText("@") }
            SpecialKeyButton("=") { onSendText("=") }
            SpecialKeyButton(":") { onSendText(":") }
            SpecialKeyButton(";") { onSendText(";") }
        }
    }
}

@Composable
private fun SpecialKeyButton(
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .height(32.dp)
            .background(
                if (isActive) Color(0xFF4CAF50) else Color.Transparent,
                shape = MaterialTheme.shapes.small
            ),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (isActive) Color.Black else Color.White
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

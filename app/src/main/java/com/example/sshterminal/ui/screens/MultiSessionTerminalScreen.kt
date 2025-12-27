package com.example.sshterminal.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sshterminal.data.repository.SettingsRepository
import com.example.sshterminal.data.ssh.TerminalSession
import com.example.sshterminal.domain.model.TerminalColorScheme
import com.example.sshterminal.domain.model.TerminalColors
import com.example.sshterminal.ui.components.TerminalAndroidView
import com.example.sshterminal.ui.components.TerminalView
import com.example.sshterminal.viewmodel.MultiSessionViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSessionTerminalScreen(
    initialHostId: Long? = null,
    viewModel: MultiSessionViewModel = koinViewModel(),
    settingsRepository: SettingsRepository = koinInject(),
    onExit: () -> Unit,
    onAddSession: () -> Unit
) {
    val sessionList by viewModel.sessionList.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val hostKeyRequest by viewModel.hostKeyVerification.collectAsState()
    val passwordRequest by viewModel.passwordRequest.collectAsState()
    val terminalColorScheme by settingsRepository.terminalColorScheme.collectAsState(initial = TerminalColorScheme.DEFAULT)

    var showCloseConfirm by remember { mutableStateOf(false) }
    var sessionToClose by remember { mutableStateOf<String?>(null) }
    val terminalViews = remember { mutableMapOf<String, TerminalAndroidView>() }

    // Get terminal colors from scheme
    val terminalColors = remember(terminalColorScheme) {
        TerminalColors.fromScheme(terminalColorScheme)
    }

    // Connect to initial host if provided
    LaunchedEffect(initialHostId) {
        if (initialHostId != null && initialHostId > 0 && sessionList.isEmpty()) {
            viewModel.connectToHost(initialHostId)
        }
    }

    // Handle terminal data
    LaunchedEffect(Unit) {
        viewModel.terminalDataForSession.collect { (sessionId, data) ->
            terminalViews[sessionId]?.processData(data)
        }
    }

    // Handle back press
    BackHandler {
        if (sessionList.isNotEmpty()) {
            showCloseConfirm = true
        } else {
            onExit()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Terminal") },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (sessionList.isNotEmpty()) {
                                showCloseConfirm = true
                            } else {
                                onExit()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Add new session button
                        IconButton(onClick = onAddSession) {
                            Icon(Icons.Default.Add, contentDescription = "New Session")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E1E1E),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )

                // Session tabs
                if (sessionList.isNotEmpty()) {
                    SessionTabBar(
                        sessions = sessionList,
                        activeSessionId = activeSessionId,
                        onSessionSelected = { viewModel.switchSession(it) },
                        onSessionClose = { sessionId ->
                            sessionToClose = sessionId
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (sessionList.isEmpty()) {
                // No sessions
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(terminalColors.background)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No active sessions", color = Color(terminalColors.foreground))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onAddSession) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New Session")
                        }
                    }
                }
            } else {
                // Show active session
                val activeSession = activeSessionId?.let { viewModel.getSession(it) }

                when (activeSession?.state) {
                    is TerminalSession.SessionState.Connecting -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(terminalColors.background)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(terminalColors.green))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Connecting to ${activeSession.hostName}...", color = Color(terminalColors.foreground))
                            }
                        }
                    }

                    is TerminalSession.SessionState.Connected -> {
                        // Terminal view
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(terminalColors.background))
                        ) {
                            TerminalView(
                                modifier = Modifier.fillMaxSize(),
                                colorScheme = terminalColorScheme,
                                onSendData = { data -> viewModel.sendData(data) },
                                onSizeChanged = { cols, rows ->
                                    viewModel.resizeTerminal(cols, rows)
                                },
                                onViewCreated = { view ->
                                    activeSessionId?.let { sessionId ->
                                        terminalViews[sessionId] = view
                                        // Restore terminal content from emulator
                                        viewModel.getSession(sessionId)?.terminalEmulator?.let { emulator ->
                                            view.setTerminalEmulator(emulator)
                                        }
                                    }
                                }
                            )
                        }

                        // Special keys toolbar
                        MultiSessionSpecialKeysToolbar(
                            onSendCtrl = { char ->
                                activeSessionId?.let { terminalViews[it]?.sendCtrl(char) }
                            },
                            onSendEscape = {
                                activeSessionId?.let { terminalViews[it]?.sendEscape() }
                            },
                            onSendTab = {
                                activeSessionId?.let { terminalViews[it]?.sendTab() }
                            },
                            onSendArrow = { dir ->
                                activeSessionId?.let { terminalViews[it]?.sendArrow(dir) }
                            },
                            onSendText = { text ->
                                viewModel.sendData(text.toByteArray(Charsets.UTF_8))
                            },
                            onShowKeyboard = {
                                activeSessionId?.let { terminalViews[it]?.requestFocus() }
                                activeSessionId?.let { terminalViews[it]?.showSoftKeyboard() }
                            }
                        )
                    }

                    is TerminalSession.SessionState.Disconnected -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(terminalColors.background)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Disconnected", color = Color(terminalColors.foreground))
                                Spacer(modifier = Modifier.height(16.dp))
                                Row {
                                    Button(onClick = {
                                        activeSession.hostId.let { viewModel.connectToHost(it) }
                                    }) {
                                        Text("Reconnect")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = {
                                        activeSessionId?.let { viewModel.closeSession(it) }
                                    }) {
                                        Text("Close")
                                    }
                                }
                            }
                        }
                    }

                    is TerminalSession.SessionState.Error -> {
                        val error = activeSession.state as TerminalSession.SessionState.Error
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(terminalColors.background)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Error", color = Color(terminalColors.red))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(error.message, color = Color(terminalColors.foreground))
                                Spacer(modifier = Modifier.height(16.dp))
                                Row {
                                    Button(onClick = {
                                        activeSession.hostId.let { viewModel.connectToHost(it) }
                                    }) {
                                        Text("Retry")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = {
                                        activeSessionId?.let { viewModel.closeSession(it) }
                                    }) {
                                        Text("Close")
                                    }
                                }
                            }
                        }
                    }

                    is TerminalSession.SessionState.Reconnecting -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(terminalColors.background)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(terminalColors.yellow))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Reconnecting to ${activeSession.hostName}...", color = Color(terminalColors.foreground))
                                Text("Attempt ${activeSession.reconnectAttempts}",
                                    color = Color(terminalColors.foreground).copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    null -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(terminalColors.background)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Select a session", color = Color(terminalColors.foreground))
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
                            "WARNING: The host key has changed!",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text("Host: ${request.hostname}:${request.port}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Algorithm: ${request.algorithm}", style = MaterialTheme.typography.bodySmall)
                    Text("Fingerprint:", style = MaterialTheme.typography.bodySmall)
                    Text(request.fingerprint, color = MaterialTheme.colorScheme.primary)
                    if (request.isKeyChanged && request.previousFingerprint != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Previous:", style = MaterialTheme.typography.bodySmall)
                        Text(request.previousFingerprint, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.acceptHostKey() },
                    colors = if (request.isKeyChanged) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else ButtonDefaults.buttonColors()
                ) {
                    Text(if (request.isKeyChanged) "Accept Anyway" else "Accept")
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

    // Close session confirmation
    sessionToClose?.let { sessionId ->
        val session = viewModel.getSession(sessionId)
        AlertDialog(
            onDismissRequest = { sessionToClose = null },
            title = { Text("Close Session?") },
            text = { Text("Close session to ${session?.hostName ?: "unknown"}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.closeSession(sessionId)
                        sessionToClose = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToClose = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Close all sessions confirmation
    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("Exit Terminal?") },
            text = { Text("This will close all ${sessionList.size} active session(s).") },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseConfirm = false
                        onExit()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SessionTabBar(
    sessions: List<TerminalSession>,
    activeSessionId: String?,
    onSessionSelected: (String) -> Unit,
    onSessionClose: (String) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = sessions.indexOfFirst { it.id == activeSessionId }.coerceAtLeast(0),
        containerColor = Color(0xFF2D2D2D),
        contentColor = Color.White,
        edgePadding = 0.dp
    ) {
        sessions.forEach { session ->
            val isSelected = session.id == activeSessionId
            Tab(
                selected = isSelected,
                onClick = { onSessionSelected(session.id) },
                modifier = Modifier.height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    // Status indicator
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (session.state) {
                                    is TerminalSession.SessionState.Connected -> Color.Green
                                    is TerminalSession.SessionState.Connecting -> Color.Yellow
                                    is TerminalSession.SessionState.Reconnecting -> Color(0xFFFFA500) // Orange
                                    is TerminalSession.SessionState.Error -> Color.Red
                                    is TerminalSession.SessionState.Disconnected -> Color.Gray
                                }
                            )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = session.hostName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelected) Color.White else Color.Gray
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Close button
                    IconButton(
                        onClick = { onSessionClose(session.id) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(14.dp),
                            tint = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiSessionSpecialKeysToolbar(
    onSendCtrl: (Char) -> Unit,
    onSendEscape: () -> Unit,
    onSendTab: () -> Unit,
    onSendArrow: (String) -> Unit,
    onSendText: (String) -> Unit,
    onShowKeyboard: () -> Unit = {}
) {
    var ctrlMode by remember { mutableStateOf(false) }
    var showSymbols by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2D2D2D))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Keyboard toggle button (for Japanese input)
            SpecialKeyBtn("あ") { onShowKeyboard() }

            // Toggle between control keys and symbols
            SpecialKeyBtn(if (showSymbols) "Ctrl" else "記号", showSymbols) {
                showSymbols = !showSymbols
            }

            Spacer(modifier = Modifier.width(4.dp))

            if (!showSymbols) {
                // Control keys row
                SpecialKeyBtn("ESC") { onSendEscape() }
                SpecialKeyBtn("TAB") { onSendTab() }
                SpecialKeyBtn("Ctrl", ctrlMode) { ctrlMode = !ctrlMode }
                SpecialKeyBtn("^C") { onSendCtrl('c') }
                SpecialKeyBtn("^D") { onSendCtrl('d') }
                SpecialKeyBtn("^Z") { onSendCtrl('z') }
                SpecialKeyBtn("^L") { onSendCtrl('l') }

                Spacer(modifier = Modifier.width(8.dp))

                // Arrow keys
                IconButton(onClick = { onSendArrow("up") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, "Up", tint = Color.White)
                }
                IconButton(onClick = { onSendArrow("down") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, "Down", tint = Color.White)
                }
                IconButton(onClick = { onSendArrow("left") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left", tint = Color.White)
                }
                IconButton(onClick = { onSendArrow("right") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right", tint = Color.White)
                }
            } else {
                // Symbol keys row
                listOf("/", "\\", "-", "_", ".", "~", "|", ">", "<", "&", "\"", "'", "`",
                    "(", ")", "[", "]", "{", "}", "\$", "*", "?", "#", "@", "=", ":", ";").forEach { char ->
                    SpecialKeyBtn(char) { onSendText(char) }
                }
            }
        }
    }
}

@Composable
private fun SpecialKeyBtn(
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
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

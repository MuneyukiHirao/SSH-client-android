package com.example.sshterminal.data.ssh

import android.util.Log
import com.example.sshterminal.data.local.dao.ActiveSessionDao
import com.example.sshterminal.data.repository.SettingsRepository
import com.example.sshterminal.domain.model.ActiveSession
import com.example.sshterminal.domain.model.Host
import com.example.sshterminal.domain.model.PortForward
import com.example.sshterminal.ui.components.TerminalEmulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a single terminal session with its own SSH connection and terminal emulator
 */
data class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    val hostId: Long,
    val hostName: String,
    val sshClient: SSHClient,
    val terminalEmulator: TerminalEmulator = TerminalEmulator(),
    var sshSession: SSHSession? = null,
    var state: SessionState = SessionState.Disconnected,
    var reconnectAttempts: Int = 0,
    var lastConnectedAt: Long = 0,
    var createdAt: Long = System.currentTimeMillis()
) {
    sealed class SessionState {
        object Disconnected : SessionState()
        object Connecting : SessionState()
        object Connected : SessionState()
        object Reconnecting : SessionState()
        data class Error(val message: String) : SessionState()
    }
}

/**
 * Manages multiple terminal sessions with keep-alive, auto-reconnection, and persistence
 */
class SessionManager(
    private val keyStoreManager: KeyStoreManager,
    private val activeSessionDao: ActiveSessionDao? = null,
    private val settingsRepository: SettingsRepository? = null
) {
    companion object {
        private const val TAG = "SessionManager"
        private const val MAX_SESSIONS = 10
        private const val KEEP_ALIVE_INTERVAL_MS = 30_000L // 30 seconds
        private const val KEEP_ALIVE_INTERVAL_BACKGROUND_MS = 60_000L // 60 seconds when in background
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_DELAY_MS = 5_000L // 5 seconds between reconnect attempts
        private const val PERSIST_INTERVAL_MS = 10_000L // Persist state every 10 seconds
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // All active sessions
    private val sessions = ConcurrentHashMap<String, TerminalSession>()
    private val readJobs = ConcurrentHashMap<String, Job>()

    // Current active session ID
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    // List of all sessions for UI
    private val _sessionList = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessionList: StateFlow<List<TerminalSession>> = _sessionList.asStateFlow()

    // Terminal data events (sessionId -> data)
    private val _terminalData = MutableSharedFlow<Pair<String, ByteArray>>()
    val terminalData: SharedFlow<Pair<String, ByteArray>> = _terminalData.asSharedFlow()

    // Session state changes
    private val _sessionStateChanged = MutableSharedFlow<Pair<String, TerminalSession.SessionState>>()
    val sessionStateChanged: SharedFlow<Pair<String, TerminalSession.SessionState>> = _sessionStateChanged.asSharedFlow()

    // Restored sessions that need reconnection (sessionId -> hostId)
    private val _restoredSessions = MutableSharedFlow<Pair<String, Long>>()
    val restoredSessions: SharedFlow<Pair<String, Long>> = _restoredSessions.asSharedFlow()

    // Keep-alive and network state
    private var keepAliveJob: Job? = null
    private var persistJob: Job? = null
    private var isInBackground = false
    private var isNetworkAvailable = true
    private val sessionsNeedingReconnect = ConcurrentHashMap<String, Boolean>()

    init {
        startKeepAliveLoop()
        startPersistLoop()
    }

    /**
     * Restore sessions from database after app restart
     */
    suspend fun restorePersistedSessions() {
        val dao = activeSessionDao ?: return

        try {
            val persistedSessions = dao.getAllSessionsOnce()
            Log.d(TAG, "Found ${persistedSessions.size} persisted sessions")

            for (persisted in persistedSessions) {
                // Create a new session for each persisted one
                val sshClient = SSHClient(keyStoreManager)
                val terminalEmulator = TerminalEmulator()

                // Restore terminal buffer if available
                if (persisted.terminalBuffer.isNotEmpty()) {
                    try {
                        terminalEmulator.restoreFromString(persisted.terminalBuffer)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to restore terminal buffer for ${persisted.sessionId}", e)
                    }
                }

                val session = TerminalSession(
                    id = persisted.sessionId,
                    hostId = persisted.hostId,
                    hostName = persisted.hostName,
                    sshClient = sshClient,
                    terminalEmulator = terminalEmulator,
                    state = TerminalSession.SessionState.Disconnected,
                    lastConnectedAt = persisted.lastConnectedAt,
                    createdAt = persisted.createdAt
                )

                // Set up connection lost callback
                sshClient.onConnectionLost = {
                    handleConnectionLost(session.id)
                }

                sessions[session.id] = session

                // Set as active if it was the active session
                if (persisted.isActive) {
                    _activeSessionId.value = session.id
                }

                Log.d(TAG, "Restored session ${session.id} for host ${session.hostName}")

                // Notify that this session needs reconnection
                _restoredSessions.emit(session.id to session.hostId)
            }

            // Set first session as active if none was marked
            if (_activeSessionId.value == null && sessions.isNotEmpty()) {
                _activeSessionId.value = sessions.keys.first()
            }

            updateSessionList()
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring persisted sessions", e)
        }
    }

    /**
     * Create a new session for a host
     */
    fun createSession(hostId: Long, hostName: String): TerminalSession? {
        if (sessions.size >= MAX_SESSIONS) {
            Log.w(TAG, "Maximum session limit reached")
            return null
        }

        val sshClient = SSHClient(keyStoreManager)
        val session = TerminalSession(
            hostId = hostId,
            hostName = hostName,
            sshClient = sshClient
        )

        // Set up connection lost callback for auto-reconnect
        sshClient.onConnectionLost = {
            handleConnectionLost(session.id)
        }

        sessions[session.id] = session
        updateSessionList()

        // Set as active if it's the first session
        if (_activeSessionId.value == null) {
            _activeSessionId.value = session.id
        }

        // Persist the new session
        persistSession(session)

        Log.d(TAG, "Created session ${session.id} for host $hostName")
        return session
    }

    /**
     * Connect a session
     */
    suspend fun connectSession(
        sessionId: String,
        host: Host,
        password: String? = null,
        portForwards: List<PortForward> = emptyList(),
        onHostKeyVerification: ((String, String) -> Boolean)? = null
    ): Result<Unit> {
        val session = sessions[sessionId] ?: return Result.failure(Exception("Session not found"))

        session.state = TerminalSession.SessionState.Connecting
        session.reconnectAttempts = 0
        updateSessionList()
        scope.launch { _sessionStateChanged.emit(sessionId to session.state) }

        session.sshClient.onHostKeyVerification = onHostKeyVerification

        session.sshClient.onStateChanged = { state ->
            when (state) {
                is SSHConnectionState.Connected -> {
                    session.sshSession = state.session
                    session.state = TerminalSession.SessionState.Connected
                    session.lastConnectedAt = System.currentTimeMillis()
                    session.reconnectAttempts = 0
                    sessionsNeedingReconnect.remove(sessionId)
                    updateSessionList()
                    scope.launch { _sessionStateChanged.emit(sessionId to session.state) }
                    startReading(sessionId, state.session)
                    persistSessionState(sessionId, true, null)

                    // Auto-tmux: send tmux command after connection
                    scope.launch {
                        sendAutoTmuxCommand(sessionId)
                    }
                }
                is SSHConnectionState.Disconnected -> {
                    session.state = TerminalSession.SessionState.Disconnected
                    updateSessionList()
                    scope.launch { _sessionStateChanged.emit(sessionId to session.state) }
                    stopReading(sessionId)
                    persistSessionState(sessionId, false, null)
                }
                is SSHConnectionState.Error -> {
                    session.state = TerminalSession.SessionState.Error(state.message)
                    updateSessionList()
                    scope.launch { _sessionStateChanged.emit(sessionId to session.state) }
                    stopReading(sessionId)
                    persistSessionState(sessionId, false, state.message)
                }
                is SSHConnectionState.Connecting -> {
                    session.state = TerminalSession.SessionState.Connecting
                    updateSessionList()
                    scope.launch { _sessionStateChanged.emit(sessionId to session.state) }
                }
            }
        }

        val result = session.sshClient.connect(host, password, portForwards)
        return result.map { }
    }

    /**
     * Handle connection lost event
     */
    private fun handleConnectionLost(sessionId: String) {
        Log.d(TAG, "Connection lost for session $sessionId")
        val session = sessions[sessionId] ?: return

        // Mark session as needing reconnect
        sessionsNeedingReconnect[sessionId] = true

        // Update state to show error
        session.state = TerminalSession.SessionState.Error("Connection lost")
        updateSessionList()
        scope.launch { _sessionStateChanged.emit(sessionId to session.state) }
        persistSessionState(sessionId, false, "Connection lost")

        // Try to reconnect if network is available
        if (isNetworkAvailable) {
            attemptReconnect(sessionId)
        }
    }

    /**
     * Attempt to reconnect a session
     */
    private fun attemptReconnect(sessionId: String) {
        val session = sessions[sessionId] ?: return

        if (session.reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached for session $sessionId")
            session.state = TerminalSession.SessionState.Error("Reconnection failed")
            sessionsNeedingReconnect.remove(sessionId)
            updateSessionList()
            scope.launch { _sessionStateChanged.emit(sessionId to session.state) }
            persistSessionState(sessionId, false, "Reconnection failed")
            return
        }

        if (!session.sshClient.canReconnect()) {
            Log.w(TAG, "Cannot reconnect session $sessionId - no connection info")
            return
        }

        session.reconnectAttempts++
        session.state = TerminalSession.SessionState.Reconnecting
        updateSessionList()
        scope.launch { _sessionStateChanged.emit(sessionId to session.state) }

        scope.launch(Dispatchers.IO) {
            delay(RECONNECT_DELAY_MS)

            if (!isNetworkAvailable) {
                Log.d(TAG, "Network not available, postponing reconnect")
                return@launch
            }

            Log.d(TAG, "Attempting reconnect for session $sessionId (attempt ${session.reconnectAttempts})")

            try {
                val result = session.sshClient.reconnect()
                if (result.isSuccess) {
                    Log.d(TAG, "Reconnect successful for session $sessionId")
                    sessionsNeedingReconnect.remove(sessionId)
                } else {
                    Log.w(TAG, "Reconnect failed for session $sessionId: ${result.exceptionOrNull()?.message}")
                    attemptReconnect(sessionId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reconnect exception for session $sessionId", e)
                attemptReconnect(sessionId)
            }
        }
    }

    /**
     * Called when network becomes available
     */
    fun onNetworkAvailable() {
        Log.d(TAG, "Network available")
        isNetworkAvailable = true

        // Attempt to reconnect sessions that need it
        for (sessionId in sessionsNeedingReconnect.keys) {
            attemptReconnect(sessionId)
        }
    }

    /**
     * Called when network is lost
     */
    fun onNetworkLost() {
        Log.d(TAG, "Network lost")
        isNetworkAvailable = false
    }

    /**
     * Start the keep-alive loop
     */
    private fun startKeepAliveLoop() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val interval = if (isInBackground) {
                    KEEP_ALIVE_INTERVAL_BACKGROUND_MS
                } else {
                    KEEP_ALIVE_INTERVAL_MS
                }

                delay(interval)

                // Send keep-alive to all connected sessions
                for ((sessionId, session) in sessions) {
                    if (session.state is TerminalSession.SessionState.Connected) {
                        val alive = session.sshClient.sendKeepAlive()
                        if (!alive) {
                            Log.w(TAG, "Keep-alive failed for session $sessionId")
                            // Connection lost callback will be triggered
                        }
                    }
                }
            }
        }
    }

    /**
     * Start the persistence loop
     */
    private fun startPersistLoop() {
        if (activeSessionDao == null) return

        persistJob?.cancel()
        persistJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(PERSIST_INTERVAL_MS)
                persistAllSessions()
            }
        }
    }

    /**
     * Persist a single session to database
     */
    private fun persistSession(session: TerminalSession) {
        val dao = activeSessionDao ?: return

        scope.launch(Dispatchers.IO) {
            try {
                val isConnected = session.state is TerminalSession.SessionState.Connected
                val errorMessage = (session.state as? TerminalSession.SessionState.Error)?.message

                val activeSession = ActiveSession(
                    sessionId = session.id,
                    hostId = session.hostId,
                    hostName = session.hostName,
                    terminalBuffer = session.terminalEmulator.saveToString(),
                    cursorX = session.terminalEmulator.cursorX,
                    cursorY = session.terminalEmulator.cursorY,
                    scrollbackLines = session.terminalEmulator.getScrollbackLines(),
                    isConnected = isConnected,
                    lastConnectedAt = session.lastConnectedAt,
                    lastError = errorMessage,
                    createdAt = session.createdAt,
                    isActive = _activeSessionId.value == session.id
                )
                dao.insertSession(activeSession)
            } catch (e: Exception) {
                Log.e(TAG, "Error persisting session ${session.id}", e)
            }
        }
    }

    /**
     * Update connection state in database
     */
    private fun persistSessionState(sessionId: String, isConnected: Boolean, error: String?) {
        val dao = activeSessionDao ?: return
        val session = sessions[sessionId] ?: return

        scope.launch(Dispatchers.IO) {
            try {
                dao.updateConnectionState(
                    sessionId = sessionId,
                    isConnected = isConnected,
                    lastConnectedAt = session.lastConnectedAt,
                    lastError = error
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error updating session state for $sessionId", e)
            }
        }
    }

    /**
     * Persist all sessions to database
     */
    private suspend fun persistAllSessions() {
        val dao = activeSessionDao ?: return

        try {
            val activeId = _activeSessionId.value

            val activeSessions = sessions.values.map { session ->
                val isConnected = session.state is TerminalSession.SessionState.Connected
                val errorMessage = (session.state as? TerminalSession.SessionState.Error)?.message

                ActiveSession(
                    sessionId = session.id,
                    hostId = session.hostId,
                    hostName = session.hostName,
                    terminalBuffer = session.terminalEmulator.saveToString(),
                    cursorX = session.terminalEmulator.cursorX,
                    cursorY = session.terminalEmulator.cursorY,
                    scrollbackLines = session.terminalEmulator.getScrollbackLines(),
                    isConnected = isConnected,
                    lastConnectedAt = session.lastConnectedAt,
                    lastError = errorMessage,
                    createdAt = session.createdAt,
                    isActive = session.id == activeId
                )
            }

            if (activeSessions.isNotEmpty()) {
                dao.insertSessions(activeSessions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting all sessions", e)
        }
    }

    /**
     * Set background mode (adjusts keep-alive interval)
     */
    fun setBackgroundMode(inBackground: Boolean) {
        isInBackground = inBackground
        Log.d(TAG, "Background mode: $inBackground")
    }

    /**
     * Switch to a different session
     */
    fun switchToSession(sessionId: String) {
        if (sessions.containsKey(sessionId)) {
            _activeSessionId.value = sessionId
            Log.d(TAG, "Switched to session $sessionId")

            // Update active flag in database
            activeSessionDao?.let { dao ->
                scope.launch(Dispatchers.IO) {
                    try {
                        dao.clearActiveFlag()
                        dao.setActiveSession(sessionId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating active session", e)
                    }
                }
            }
        }
    }

    /**
     * Get the current active session
     */
    fun getActiveSession(): TerminalSession? {
        return _activeSessionId.value?.let { sessions[it] }
    }

    /**
     * Get a session by ID
     */
    fun getSession(sessionId: String): TerminalSession? {
        return sessions[sessionId]
    }

    /**
     * Send data to a session
     */
    fun sendData(sessionId: String, data: ByteArray) {
        val session = sessions[sessionId]
        if (session == null) {
            Log.e(TAG, "sendData FAILED: session not found for $sessionId")
            return
        }
        val sshSession = session.sshSession
        if (sshSession == null) {
            Log.e(TAG, "sendData FAILED: sshSession is null for $sessionId, state=${session.state}")
            return
        }
        Log.d(TAG, "sendData: ${data.size} bytes to session $sessionId")
        scope.launch(Dispatchers.IO) {
            try {
                sshSession.outputStream.write(data)
                sshSession.outputStream.flush()
                Log.d(TAG, "sendData: sent successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending data to session $sessionId", e)
                session.state = TerminalSession.SessionState.Error("Write error: ${e.message}")
                updateSessionList()
                _sessionStateChanged.emit(sessionId to session.state)
            }
        }
    }

    /**
     * Resize terminal for a session
     */
    fun resizeTerminal(sessionId: String, cols: Int, rows: Int) {
        val session = sessions[sessionId] ?: return
        session.sshClient.resizeTerminal(cols, rows)
        session.terminalEmulator.resize(cols, rows)
    }

    /**
     * Disconnect and remove a session
     */
    fun closeSession(sessionId: String) {
        val session = sessions.remove(sessionId) ?: return

        stopReading(sessionId)
        sessionsNeedingReconnect.remove(sessionId)
        session.sshClient.disconnect()

        // Remove from database
        activeSessionDao?.let { dao ->
            scope.launch(Dispatchers.IO) {
                try {
                    dao.deleteSessionById(sessionId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting session from database", e)
                }
            }
        }

        updateSessionList()

        // Switch to another session if this was the active one
        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = sessions.keys.firstOrNull()
        }

        Log.d(TAG, "Closed session $sessionId")
    }

    /**
     * Disconnect all sessions
     */
    fun closeAllSessions() {
        val sessionIds = sessions.keys.toList()
        for (sessionId in sessionIds) {
            closeSession(sessionId)
        }
    }

    /**
     * Get session count
     */
    fun getSessionCount(): Int = sessions.size

    /**
     * Get all sessions
     */
    fun getAllSessions(): List<TerminalSession> = sessions.values.toList()

    private fun startReading(sessionId: String, sshSession: SSHSession) {
        stopReading(sessionId)

        val job = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(8192)
            val session = sessions[sessionId] ?: return@launch

            try {
                while (isActive) {
                    // Read stdout
                    val available = sshSession.inputStream.available()
                    if (available > 0) {
                        val read = sshSession.inputStream.read(buffer, 0, minOf(available, buffer.size))
                        if (read > 0) {
                            val data = buffer.copyOf(read)
                            // Process through terminal emulator
                            session.terminalEmulator.processBytes(data)
                            _terminalData.emit(sessionId to data)
                        }
                    } else {
                        delay(10)
                    }

                    // Read stderr
                    val errAvailable = sshSession.errorStream.available()
                    if (errAvailable > 0) {
                        val read = sshSession.errorStream.read(buffer, 0, minOf(errAvailable, buffer.size))
                        if (read > 0) {
                            val data = buffer.copyOf(read)
                            session.terminalEmulator.processBytes(data)
                            _terminalData.emit(sessionId to data)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Read error for session $sessionId", e)
                    session.state = TerminalSession.SessionState.Error("Connection lost: ${e.message}")
                    updateSessionList()
                    _sessionStateChanged.emit(sessionId to session.state)

                    // Trigger reconnect
                    handleConnectionLost(sessionId)
                }
            }
        }

        readJobs[sessionId] = job
    }

    private fun stopReading(sessionId: String) {
        readJobs.remove(sessionId)?.cancel()
    }

    private fun updateSessionList() {
        _sessionList.value = sessions.values.toList().sortedBy { it.hostName }
    }

    /**
     * Send tmux command if auto-tmux is enabled
     * Uses `tmux new-session -A -s <name>` which:
     * - Attaches to existing session if it exists
     * - Creates new session if it doesn't exist
     */
    private suspend fun sendAutoTmuxCommand(sessionId: String) {
        val settings = settingsRepository ?: return

        try {
            val autoTmux = settings.autoTmux.first()
            if (!autoTmux) {
                Log.d(TAG, "Auto-tmux disabled, skipping")
                return
            }

            val sessionName = settings.tmuxSessionName.first()
            Log.d(TAG, "Auto-tmux enabled, attaching/creating session: $sessionName")

            // Wait a bit for the shell to be ready
            delay(500)

            // Send tmux command
            // -A: attach if exists, otherwise create
            // -s: session name
            val tmuxCommand = "tmux new-session -A -s $sessionName\n"
            sendData(sessionId, tmuxCommand.toByteArray(Charsets.UTF_8))

            Log.d(TAG, "Sent tmux command for session $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending auto-tmux command", e)
        }
    }
}

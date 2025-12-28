package com.example.sshterminal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sshterminal.data.local.dao.HostDao
import com.example.sshterminal.data.local.dao.PortForwardDao
import com.example.sshterminal.data.ssh.SessionManager
import com.example.sshterminal.data.ssh.TerminalSession
import com.example.sshterminal.di.ServiceConnectionHolder
import com.example.sshterminal.domain.model.Host
import com.example.sshterminal.domain.model.HostKeyVerificationRequest
import com.example.sshterminal.domain.model.PasswordRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MultiSessionViewModel(
    private val hostDao: HostDao,
    private val portForwardDao: PortForwardDao,
    private val serviceConnectionHolder: ServiceConnectionHolder
) : ViewModel() {

    // Get SessionManager from service (may be null if service not connected)
    private val sessionManager: SessionManager?
        get() = serviceConnectionHolder.sessionManager

    // Service connection state
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    // Session list from SessionManager
    private val _sessionList = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessionList: StateFlow<List<TerminalSession>> = _sessionList.asStateFlow()

    // Active session ID
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    // Terminal data for UI updates
    private val _terminalDataForSession = MutableSharedFlow<Pair<String, ByteArray>>()
    val terminalDataForSession: SharedFlow<Pair<String, ByteArray>> = _terminalDataForSession.asSharedFlow()

    // Host key verification request
    private val _hostKeyVerification = MutableStateFlow<HostKeyVerificationRequest?>(null)
    val hostKeyVerification: StateFlow<HostKeyVerificationRequest?> = _hostKeyVerification.asStateFlow()

    // Password request
    private val _passwordRequest = MutableStateFlow<PasswordRequest?>(null)
    val passwordRequest: StateFlow<PasswordRequest?> = _passwordRequest.asStateFlow()

    // Pending connection info (for after password/host key verification)
    private var pendingHost: Host? = null
    private var pendingSessionId: String? = null

    init {
        // Wait for service connection and collect flows
        viewModelScope.launch {
            while (true) {
                val manager = sessionManager
                if (manager != null) {
                    _isServiceConnected.value = true

                    // Collect session list
                    launch {
                        manager.sessionList.collect { sessions ->
                            _sessionList.value = sessions
                        }
                    }

                    // Collect active session ID
                    launch {
                        manager.activeSessionId.collect { id ->
                            _activeSessionId.value = id
                        }
                    }

                    // Forward terminal data from session manager
                    launch {
                        manager.terminalData.collect { (sessionId, data) ->
                            _terminalDataForSession.emit(sessionId to data)
                        }
                    }

                    // Handle session state changes
                    launch {
                        manager.sessionStateChanged.collect { (sessionId, state) ->
                            // Additional handling if needed
                        }
                    }

                    break
                }
                delay(100) // Wait for service to connect
            }
        }
    }

    /**
     * Create a new session and connect to a host
     */
    fun connectToHost(hostId: Long) {
        val manager = sessionManager ?: return

        viewModelScope.launch {
            val host = hostDao.getHostById(hostId) ?: return@launch

            // Create new session
            val session = manager.createSession(hostId, host.name) ?: return@launch

            pendingHost = host
            pendingSessionId = session.id

            // Check if we need password
            if (host.keyAlias == null && host.usePassword) {
                _passwordRequest.value = PasswordRequest(
                    sessionId = session.id,
                    hostId = hostId,
                    hostname = host.hostname,
                    username = host.username
                )
                return@launch
            }

            performConnect(session.id, host, null)
        }
    }

    /**
     * Connect with password after user input
     */
    fun connectWithPassword(password: String) {
        viewModelScope.launch {
            val request = _passwordRequest.value ?: return@launch
            val sessionId = request.sessionId ?: return@launch
            _passwordRequest.value = null

            val host = pendingHost ?: return@launch
            performConnect(sessionId, host, password)
        }
    }

    /**
     * Cancel password request
     */
    fun cancelPasswordRequest() {
        val request = _passwordRequest.value ?: return
        _passwordRequest.value = null
        request.sessionId?.let { sessionManager?.closeSession(it) }
        pendingHost = null
        pendingSessionId = null
    }

    private suspend fun performConnect(sessionId: String, host: Host, password: String?) {
        val manager = sessionManager ?: return
        val portForwards = portForwardDao.getEnabledPortForwards(host.id)

        manager.connectSession(
            sessionId = sessionId,
            host = host,
            password = password,
            portForwards = portForwards,
            onHostKeyVerification = { fingerprint, algorithm ->
                // Determine if this is a key change
                val isKeyChanged = host.hostKeyFingerprint != null &&
                        host.hostKeyFingerprint != fingerprint

                if (host.hostKeyFingerprint != null && host.hostKeyFingerprint == fingerprint) {
                    true
                } else {
                    _hostKeyVerification.value = HostKeyVerificationRequest(
                        sessionId = sessionId,
                        fingerprint = fingerprint,
                        algorithm = algorithm,
                        hostId = host.id,
                        hostname = host.hostname,
                        port = host.port,
                        isKeyChanged = isKeyChanged,
                        previousFingerprint = host.hostKeyFingerprint
                    )
                    false
                }
            }
        )

        // Update last connected
        hostDao.updateLastConnected(host.id)
        pendingHost = null
        pendingSessionId = null
    }

    /**
     * Accept host key and reconnect
     */
    fun acceptHostKey() {
        viewModelScope.launch {
            val request = _hostKeyVerification.value ?: return@launch
            _hostKeyVerification.value = null

            // Save fingerprint
            hostDao.updateHostKeyFingerprint(request.hostId, request.fingerprint)

            // Close current session and reconnect
            request.sessionId?.let { sessionManager?.closeSession(it) }
            connectToHost(request.hostId)
        }
    }

    /**
     * Reject host key
     */
    fun rejectHostKey() {
        val request = _hostKeyVerification.value ?: return
        _hostKeyVerification.value = null
        request.sessionId?.let { sessionManager?.closeSession(it) }
    }

    /**
     * Switch to a different session
     */
    fun switchSession(sessionId: String) {
        sessionManager?.switchToSession(sessionId)
    }

    /**
     * Send data to the active session
     */
    fun sendData(data: ByteArray) {
        val sessionId = activeSessionId.value
        if (sessionId == null) {
            android.util.Log.e("MultiSessionVM", "sendData FAILED: no active session")
            return
        }
        val manager = sessionManager
        if (manager == null) {
            android.util.Log.e("MultiSessionVM", "sendData FAILED: sessionManager is null")
            return
        }
        android.util.Log.d("MultiSessionVM", "sendData: ${data.size} bytes to session $sessionId")
        manager.sendData(sessionId, data)
    }

    /**
     * Send data to a specific session
     */
    fun sendDataToSession(sessionId: String, data: ByteArray) {
        sessionManager?.sendData(sessionId, data)
    }

    /**
     * Resize terminal for active session
     */
    fun resizeTerminal(cols: Int, rows: Int) {
        val sessionId = activeSessionId.value ?: return
        sessionManager?.resizeTerminal(sessionId, cols, rows)
    }

    /**
     * Close the active session
     */
    fun closeActiveSession() {
        val sessionId = activeSessionId.value ?: return
        sessionManager?.closeSession(sessionId)
    }

    /**
     * Close a specific session
     */
    fun closeSession(sessionId: String) {
        sessionManager?.closeSession(sessionId)
    }

    /**
     * Get active session
     */
    fun getActiveSession(): TerminalSession? {
        return sessionManager?.getActiveSession()
    }

    /**
     * Get session by ID
     */
    fun getSession(sessionId: String): TerminalSession? {
        return sessionManager?.getSession(sessionId)
    }

    /**
     * Check if there are any active sessions
     */
    fun hasActiveSessions(): Boolean {
        return (sessionManager?.getSessionCount() ?: 0) > 0
    }

    // Note: We intentionally do NOT close sessions in onCleared()
    // Sessions are managed by the SSHConnectionService and should persist
    // even when the ViewModel is cleared (e.g., configuration change)
}

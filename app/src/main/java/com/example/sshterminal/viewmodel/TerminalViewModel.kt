package com.example.sshterminal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sshterminal.data.local.dao.HostDao
import com.example.sshterminal.data.local.dao.PortForwardDao
import com.example.sshterminal.data.ssh.KeyStoreManager
import com.example.sshterminal.data.ssh.SSHClient
import com.example.sshterminal.data.ssh.SSHConnectionState
import com.example.sshterminal.data.ssh.SSHSession
import com.example.sshterminal.domain.model.Host
import com.example.sshterminal.domain.model.HostKeyVerificationRequest
import com.example.sshterminal.domain.model.PasswordRequest
import com.example.sshterminal.domain.model.PortForward
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class TerminalState {
    object Disconnected : TerminalState()
    object Connecting : TerminalState()
    data class Connected(val hostName: String) : TerminalState()
    data class Error(val message: String) : TerminalState()
}

class TerminalViewModel(
    private val hostDao: HostDao,
    private val portForwardDao: PortForwardDao,
    private val keyStoreManager: KeyStoreManager
) : ViewModel() {

    private val sshClient = SSHClient(keyStoreManager)

    private val _state = MutableStateFlow<TerminalState>(TerminalState.Disconnected)
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private val _terminalData = MutableSharedFlow<ByteArray>()
    val terminalData: SharedFlow<ByteArray> = _terminalData.asSharedFlow()

    private val _hostKeyVerification = MutableStateFlow<HostKeyVerificationRequest?>(null)
    val hostKeyVerification: StateFlow<HostKeyVerificationRequest?> = _hostKeyVerification.asStateFlow()

    private val _passwordRequest = MutableStateFlow<PasswordRequest?>(null)
    val passwordRequest: StateFlow<PasswordRequest?> = _passwordRequest.asStateFlow()

    private var currentHost: Host? = null
    private var currentSession: SSHSession? = null
    private var readJob: Job? = null

    init {
        sshClient.onStateChanged = { state ->
            when (state) {
                is SSHConnectionState.Connecting -> _state.value = TerminalState.Connecting
                is SSHConnectionState.Connected -> {
                    currentSession = state.session
                    _state.value = TerminalState.Connected(currentHost?.name ?: "Unknown")
                    startReading(state.session)
                }
                is SSHConnectionState.Disconnected -> {
                    _state.value = TerminalState.Disconnected
                    stopReading()
                }
                is SSHConnectionState.Error -> {
                    _state.value = TerminalState.Error(state.message)
                    stopReading()
                }
            }
        }
    }

    fun connect(hostId: Long) {
        viewModelScope.launch {
            val host = hostDao.getHostById(hostId)
            if (host == null) {
                _state.value = TerminalState.Error("Host not found")
                return@launch
            }

            currentHost = host

            // Setup host key verification callback
            sshClient.onHostKeyVerification = { fingerprint, algorithm ->
                if (host.hostKeyFingerprint != null && host.hostKeyFingerprint == fingerprint) {
                    true
                } else {
                    // Determine if this is a key change or first connection
                    val isKeyChanged = host.hostKeyFingerprint != null

                    // Request user verification
                    _hostKeyVerification.value = HostKeyVerificationRequest(
                        fingerprint = fingerprint,
                        algorithm = algorithm,
                        hostId = hostId,
                        hostname = host.hostname,
                        port = host.port,
                        isKeyChanged = isKeyChanged,
                        previousFingerprint = host.hostKeyFingerprint
                    )
                    // Wait for user response (this is handled via acceptHostKey/rejectHostKey)
                    false
                }
            }

            // Check if we need password
            if (host.keyAlias == null && host.usePassword) {
                _passwordRequest.value = PasswordRequest(
                    hostId = host.id,
                    hostname = host.hostname,
                    username = host.username
                )
                return@launch
            }

            // Get port forwards
            val portForwards = portForwardDao.getEnabledPortForwards(hostId)

            performConnect(host, null, portForwards)
        }
    }

    fun connectWithPassword(password: String) {
        viewModelScope.launch {
            val host = currentHost ?: return@launch
            _passwordRequest.value = null

            val portForwards = portForwardDao.getEnabledPortForwards(host.id)
            performConnect(host, password, portForwards)
        }
    }

    private suspend fun performConnect(host: Host, password: String?, portForwards: List<PortForward>) {
        val result = sshClient.connect(host, password, portForwards)

        result.fold(
            onSuccess = {
                hostDao.updateLastConnected(host.id)
            },
            onFailure = { e ->
                _state.value = TerminalState.Error(e.message ?: "Connection failed")
            }
        )
    }

    fun acceptHostKey() {
        viewModelScope.launch {
            val request = _hostKeyVerification.value ?: return@launch
            _hostKeyVerification.value = null

            // Save fingerprint
            hostDao.updateHostKeyFingerprint(request.hostId, request.fingerprint)

            // Reconnect
            connect(request.hostId)
        }
    }

    fun rejectHostKey() {
        _hostKeyVerification.value = null
        _state.value = TerminalState.Disconnected
    }

    fun cancelPasswordRequest() {
        _passwordRequest.value = null
        _state.value = TerminalState.Disconnected
    }

    private fun startReading(session: SSHSession) {
        readJob = viewModelScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(8192)
            try {
                while (isActive) {
                    val available = session.inputStream.available()
                    if (available > 0) {
                        val read = session.inputStream.read(buffer, 0, minOf(available, buffer.size))
                        if (read > 0) {
                            val data = buffer.copyOf(read)
                            _terminalData.emit(data)
                        }
                    } else {
                        // Small delay to prevent busy waiting
                        kotlinx.coroutines.delay(10)
                    }

                    // Also check stderr
                    val errAvailable = session.errorStream.available()
                    if (errAvailable > 0) {
                        val read = session.errorStream.read(buffer, 0, minOf(errAvailable, buffer.size))
                        if (read > 0) {
                            val data = buffer.copyOf(read)
                            _terminalData.emit(data)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        _state.value = TerminalState.Error("Connection lost: ${e.message}")
                    }
                }
            }
        }
    }

    private fun stopReading() {
        readJob?.cancel()
        readJob = null
    }

    fun sendData(data: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                currentSession?.outputStream?.write(data)
                currentSession?.outputStream?.flush()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = TerminalState.Error("Write error: ${e.message}")
                }
            }
        }
    }

    fun resizeTerminal(cols: Int, rows: Int) {
        sshClient.resizeTerminal(cols, rows)
    }

    fun disconnect() {
        stopReading()
        sshClient.disconnect()
        currentHost = null
        currentSession = null
        _state.value = TerminalState.Disconnected
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}

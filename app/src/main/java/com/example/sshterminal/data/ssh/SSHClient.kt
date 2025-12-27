package com.example.sshterminal.data.ssh

import com.example.sshterminal.domain.model.Host
import com.example.sshterminal.domain.model.PortForward
import com.example.sshterminal.domain.model.PortForwardType
import com.trilead.ssh2.Connection
import com.trilead.ssh2.ConnectionInfo
import com.trilead.ssh2.InteractiveCallback
import com.trilead.ssh2.KnownHosts
import com.trilead.ssh2.LocalPortForwarder
import com.trilead.ssh2.DynamicPortForwarder
import com.trilead.ssh2.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPair
import android.util.Log

sealed class SSHConnectionState {
    object Disconnected : SSHConnectionState()
    object Connecting : SSHConnectionState()
    data class Connected(val session: SSHSession) : SSHConnectionState()
    data class Error(val message: String, val exception: Exception? = null) : SSHConnectionState()
}

data class SSHSession(
    val inputStream: InputStream,
    val outputStream: OutputStream,
    val errorStream: InputStream,
    val termWidth: Int = 80,
    val termHeight: Int = 24
) {
    fun resize(width: Int, height: Int) {
        // Will be called when terminal size changes
    }
}

class SSHClient(
    private val keyStoreManager: KeyStoreManager
) {
    companion object {
        private const val TAG = "SSHClient"
        private const val DEFAULT_KEEP_ALIVE_INTERVAL = 60 // seconds
    }

    private var connection: Connection? = null
    private var session: Session? = null
    private val localPortForwarders = mutableListOf<LocalPortForwarder>()
    private val dynamicPortForwarders = mutableListOf<DynamicPortForwarder>()

    var onStateChanged: ((SSHConnectionState) -> Unit)? = null
    var onHostKeyVerification: ((String, String) -> Boolean)? = null
    var onConnectionLost: (() -> Unit)? = null

    // Keep-alive settings
    private var keepAliveEnabled = true
    private var keepAliveInterval = DEFAULT_KEEP_ALIVE_INTERVAL
    private var lastKeepAliveTime = 0L

    private var currentState: SSHConnectionState = SSHConnectionState.Disconnected
        set(value) {
            field = value
            onStateChanged?.invoke(value)
        }

    // Store host info for reconnection
    private var lastHost: Host? = null
    private var lastPassword: String? = null
    private var lastPortForwards: List<PortForward> = emptyList()

    suspend fun connect(
        host: Host,
        password: String? = null,
        portForwards: List<PortForward> = emptyList()
    ): Result<SSHSession> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== SSH CONNECT START ===")
            Log.d(TAG, "Host: ${host.hostname}:${host.port}, User: ${host.username}")
            Log.d(TAG, "KeyAlias: ${host.keyAlias}, UsePassword: ${host.usePassword}")
            Log.d(TAG, "HostKeyFingerprint: ${host.hostKeyFingerprint}")
            currentState = SSHConnectionState.Connecting

            // Store for potential reconnection
            lastHost = host
            lastPassword = password
            lastPortForwards = portForwards

            val conn = Connection(host.hostname, host.port)
            connection = conn

            // Connect with timeout
            Log.d(TAG, "Connecting to ${host.hostname}:${host.port}...")
            val connectionInfo = conn.connect(
                { hostname, port, serverHostKeyAlgorithm, serverHostKey ->
                    // Host key verification
                    val fingerprint = KnownHosts.createHexFingerprint(serverHostKeyAlgorithm, serverHostKey)
                    Log.d(TAG, "Host key verification: algo=$serverHostKeyAlgorithm, fingerprint=$fingerprint")

                    if (host.hostKeyFingerprint != null) {
                        // Verify against stored fingerprint
                        val matches = host.hostKeyFingerprint == fingerprint
                        Log.d(TAG, "Stored fingerprint check: matches=$matches")
                        matches
                    } else {
                        // Ask user to verify
                        Log.d(TAG, "No stored fingerprint, asking user callback...")
                        val result = onHostKeyVerification?.invoke(fingerprint, serverHostKeyAlgorithm) ?: false
                        Log.d(TAG, "User verification result: $result")
                        result
                    }
                },
                30000,  // Connect timeout
                60000   // Key exchange timeout
            )
            Log.d(TAG, "Connection established")

            // Authenticate
            val authenticated = authenticate(conn, host, password)

            if (!authenticated) {
                conn.close()
                connection = null
                currentState = SSHConnectionState.Error("Authentication failed")
                return@withContext Result.failure(Exception("Authentication failed"))
            }

            // Setup port forwards
            setupPortForwards(conn, portForwards)

            // Open session
            val sess = conn.openSession()
            session = sess

            // Request PTY with xterm-256color
            sess.requestPTY(
                "xterm-256color",
                80,
                24,
                0,
                0,
                null
            )

            // Start shell
            sess.startShell()

            val sshSession = SSHSession(
                inputStream = sess.stdout,
                outputStream = sess.stdin,
                errorStream = sess.stderr
            )

            lastKeepAliveTime = System.currentTimeMillis()
            currentState = SSHConnectionState.Connected(sshSession)
            Result.success(sshSession)

        } catch (e: Exception) {
            connection?.close()
            connection = null
            currentState = SSHConnectionState.Error(e.message ?: "Connection failed", e)
            Result.failure(e)
        }
    }

    /**
     * Attempt to reconnect using stored connection info
     */
    suspend fun reconnect(): Result<SSHSession> {
        val host = lastHost ?: return Result.failure(Exception("No previous connection info"))
        return connect(host, lastPassword, lastPortForwards)
    }

    /**
     * Send keep-alive packet to maintain connection
     * Returns true if connection is still alive, false if connection was lost
     */
    fun sendKeepAlive(): Boolean {
        val conn = connection ?: return false

        if (!keepAliveEnabled) return true

        try {
            // Use SSH ping (global request that server ignores)
            conn.sendIgnorePacket()
            lastKeepAliveTime = System.currentTimeMillis()
            Log.d(TAG, "Keep-alive sent successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Keep-alive failed: ${e.message}")
            handleConnectionLost()
            return false
        }
    }

    /**
     * Check if keep-alive is due
     */
    fun isKeepAliveDue(): Boolean {
        if (!keepAliveEnabled) return false
        val elapsed = System.currentTimeMillis() - lastKeepAliveTime
        return elapsed >= keepAliveInterval * 1000
    }

    /**
     * Set keep-alive interval in seconds
     */
    fun setKeepAliveInterval(seconds: Int) {
        keepAliveInterval = seconds
    }

    /**
     * Enable or disable keep-alive
     */
    fun setKeepAliveEnabled(enabled: Boolean) {
        keepAliveEnabled = enabled
    }

    private fun handleConnectionLost() {
        Log.d(TAG, "Connection lost")
        currentState = SSHConnectionState.Error("Connection lost")
        onConnectionLost?.invoke()
    }

    private suspend fun authenticate(
        conn: Connection,
        host: Host,
        password: String?
    ): Boolean {
        // Try public key authentication first
        if (host.keyAlias != null) {
            Log.d(TAG, "Attempting public key auth with alias: ${host.keyAlias}")

            // Try PEM-based authentication for Ed25519 keys
            if (keyStoreManager.isEd25519Key(host.keyAlias)) {
                Log.d(TAG, "Using PEM format for Ed25519 key")
                val pemKey = keyStoreManager.getPrivateKeyPEM(host.keyAlias)
                if (pemKey != null) {
                    try {
                        val authenticated = conn.authenticateWithPublicKey(
                            host.username,
                            pemKey,
                            null  // No passphrase
                        )
                        Log.d(TAG, "Ed25519 PEM auth result: $authenticated")
                        if (authenticated) return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Ed25519 PEM auth failed: ${e.javaClass.name}: ${e.message}", e)
                    }
                } else {
                    Log.w(TAG, "PEM key is null for Ed25519 alias: ${host.keyAlias}")
                }
            } else {
                // Use KeyPair for RSA/ECDSA keys
                val keyPair = keyStoreManager.getKeyPair(host.keyAlias)
                if (keyPair != null) {
                    Log.d(TAG, "KeyPair found - Public: ${keyPair.public.javaClass.name}, Private: ${keyPair.private.javaClass.name}")
                    try {
                        val authenticated = conn.authenticateWithPublicKey(
                            host.username,
                            keyPair
                        )
                        Log.d(TAG, "Public key auth result: $authenticated")
                        if (authenticated) return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Public key auth failed: ${e.javaClass.name}: ${e.message}", e)
                    }
                } else {
                    Log.w(TAG, "KeyPair is null for alias: ${host.keyAlias}")
                }
            }
        }

        // Try password authentication
        if (password != null) {
            try {
                val authenticated = conn.authenticateWithPassword(host.username, password)
                if (authenticated) return true
            } catch (e: Exception) {
                // Authentication failed
            }
        }

        // Try keyboard-interactive
        if (password != null) {
            try {
                val authenticated = conn.authenticateWithKeyboardInteractive(
                    host.username,
                    object : InteractiveCallback {
                        override fun replyToChallenge(
                            name: String?,
                            instruction: String?,
                            numPrompts: Int,
                            prompt: Array<out String>?,
                            echo: BooleanArray?
                        ): Array<String> {
                            // Respond with password for all prompts
                            return Array(numPrompts) { password }
                        }
                    }
                )
                if (authenticated) return true
            } catch (e: Exception) {
                // Authentication failed
            }
        }

        return false
    }

    private fun setupPortForwards(conn: Connection, portForwards: List<PortForward>) {
        for (pf in portForwards) {
            if (!pf.enabled) continue

            try {
                when (pf.type) {
                    PortForwardType.LOCAL -> {
                        val forwarder = conn.createLocalPortForwarder(
                            pf.localPort,
                            pf.remoteHost,
                            pf.remotePort
                        )
                        localPortForwarders.add(forwarder)
                    }
                    PortForwardType.REMOTE -> {
                        conn.requestRemotePortForwarding(
                            "",  // Bind to all interfaces
                            pf.remotePort,
                            pf.remoteHost,
                            pf.localPort
                        )
                    }
                    PortForwardType.DYNAMIC -> {
                        val forwarder = conn.createDynamicPortForwarder(pf.localPort)
                        dynamicPortForwarders.add(forwarder)
                    }
                }
            } catch (e: Exception) {
                // Log port forward error but continue
                Log.e(TAG, "Port forward setup failed: ${e.message}")
            }
        }
    }

    fun resizeTerminal(width: Int, height: Int) {
        try {
            session?.resizePTY(width, height, 0, 0)
        } catch (e: Exception) {
            // Ignore resize errors
        }
    }

    fun sendData(data: ByteArray) {
        try {
            session?.stdin?.write(data)
            session?.stdin?.flush()
        } catch (e: Exception) {
            // Handle write error
            handleConnectionLost()
        }
    }

    fun sendText(text: String) {
        sendData(text.toByteArray(Charsets.UTF_8))
    }

    fun disconnect() {
        try {
            // Close port forwarders
            for (forwarder in localPortForwarders) {
                try {
                    forwarder.close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
            localPortForwarders.clear()

            for (forwarder in dynamicPortForwarders) {
                try {
                    forwarder.close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
            dynamicPortForwarders.clear()

            // Close session
            session?.close()
            session = null

            // Close connection
            connection?.close()
            connection = null

            currentState = SSHConnectionState.Disconnected
        } catch (e: Exception) {
            currentState = SSHConnectionState.Disconnected
        }
    }

    fun isConnected(): Boolean {
        return connection?.isAuthenticationComplete == true
    }

    /**
     * Check if connection can be reconnected
     */
    fun canReconnect(): Boolean {
        return lastHost != null
    }

    /**
     * Get the host info for this connection
     */
    fun getHostInfo(): Host? = lastHost
}

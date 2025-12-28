package com.example.sshterminal.domain.model

/**
 * Represents a host key verification request during SSH connection.
 * This is used when connecting to a new host or when host key has changed.
 *
 * @property sessionId The session ID (for multi-session support, null for single session)
 * @property fingerprint The fingerprint of the host key
 * @property algorithm The algorithm used for the key (e.g., "ssh-rsa", "ssh-ed25519")
 * @property hostId The database ID of the host
 * @property hostname The hostname or IP address being connected to
 * @property port The SSH port number
 * @property isKeyChanged True if the host key has changed from a previously saved one
 * @property previousFingerprint The previous fingerprint if this is a key change scenario
 */
data class HostKeyVerificationRequest(
    val sessionId: String? = null,
    val fingerprint: String,
    val algorithm: String,
    val hostId: Long,
    val hostname: String,
    val port: Int,
    val isKeyChanged: Boolean,
    val previousFingerprint: String? = null
)

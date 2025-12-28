package com.example.sshterminal.domain.model

/**
 * Represents a password request during SSH connection.
 * This is used when password authentication is required.
 *
 * @property sessionId The session ID (for multi-session support, null for single session)
 * @property hostId The database ID of the host
 * @property hostname The hostname or IP address being connected to
 * @property username The username for authentication
 */
data class PasswordRequest(
    val sessionId: String? = null,
    val hostId: Long = 0,
    val hostname: String,
    val username: String
)

package com.example.sshterminal.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a persisted terminal session for recovery after app restart.
 * This entity stores the minimum information needed to restore a session.
 */
@Entity(
    tableName = "active_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Host::class,
            parentColumns = ["id"],
            childColumns = ["hostId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("hostId")]
)
data class ActiveSession(
    @PrimaryKey
    val sessionId: String,

    val hostId: Long,

    val hostName: String,

    // Terminal state
    val terminalBuffer: String = "",
    val cursorX: Int = 0,
    val cursorY: Int = 0,
    val scrollbackLines: Int = 0,

    // Connection state
    val isConnected: Boolean = false,
    val lastConnectedAt: Long = 0,
    val lastError: String? = null,

    // Session order for UI
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false  // Whether this is the currently active session
)

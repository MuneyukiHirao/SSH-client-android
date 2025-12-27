package com.example.sshterminal.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PortForwardType {
    LOCAL,      // -L: local port forwarding
    REMOTE,     // -R: remote port forwarding
    DYNAMIC     // -D: dynamic port forwarding (SOCKS proxy)
}

@Entity(
    tableName = "port_forwards",
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
data class PortForward(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hostId: Long,
    val type: PortForwardType,
    val localPort: Int,
    val remoteHost: String = "localhost",  // Only for LOCAL/REMOTE
    val remotePort: Int = 0,               // Only for LOCAL/REMOTE
    val enabled: Boolean = true,
    val name: String? = null
)

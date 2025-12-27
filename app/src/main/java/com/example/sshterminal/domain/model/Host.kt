package com.example.sshterminal.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hosts")
data class Host(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val keyAlias: String? = null,  // Reference to key in Android Keystore
    val usePassword: Boolean = false,
    val hostKeyFingerprint: String? = null,
    val lastConnected: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

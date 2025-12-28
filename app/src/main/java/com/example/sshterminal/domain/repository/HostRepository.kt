package com.example.sshterminal.domain.repository

import com.example.sshterminal.domain.model.Host
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Host entity operations.
 * Provides abstraction over the data layer for clean architecture.
 */
interface HostRepository {

    /**
     * Get all hosts as a Flow, ordered by last connected time.
     * @return Flow emitting list of all hosts
     */
    fun getAllHosts(): Flow<List<Host>>

    /**
     * Get a specific host by its ID.
     * @param id The host ID
     * @return The host if found, null otherwise
     */
    suspend fun getHostById(id: Long): Host?

    /**
     * Insert a new host or replace existing one.
     * @param host The host to insert
     * @return The ID of the inserted host
     */
    suspend fun insertHost(host: Host): Long

    /**
     * Update an existing host.
     * @param host The host to update
     */
    suspend fun updateHost(host: Host)

    /**
     * Delete a host.
     * @param host The host to delete
     */
    suspend fun deleteHost(host: Host)

    /**
     * Update the last connected timestamp for a host.
     * @param hostId The host ID
     */
    suspend fun updateLastConnected(hostId: Long)

    /**
     * Update the host key fingerprint for verification.
     * @param hostId The host ID
     * @param fingerprint The SSH host key fingerprint
     */
    suspend fun updateHostKeyFingerprint(hostId: Long, fingerprint: String)
}

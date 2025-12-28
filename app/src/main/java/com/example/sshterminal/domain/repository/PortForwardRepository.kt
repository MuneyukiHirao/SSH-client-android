package com.example.sshterminal.domain.repository

import com.example.sshterminal.domain.model.PortForward
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for PortForward entity operations.
 * Provides abstraction over the data layer for clean architecture.
 */
interface PortForwardRepository {

    /**
     * Get all port forwards for a specific host as a Flow.
     * @param hostId The host ID
     * @return Flow emitting list of port forwards for the host
     */
    fun getPortForwardsForHost(hostId: Long): Flow<List<PortForward>>

    /**
     * Get all enabled port forwards for a specific host.
     * @param hostId The host ID
     * @return List of enabled port forwards
     */
    suspend fun getEnabledPortForwards(hostId: Long): List<PortForward>

    /**
     * Insert a new port forward configuration.
     * @param portForward The port forward to insert
     */
    suspend fun insertPortForward(portForward: PortForward)

    /**
     * Update an existing port forward configuration.
     * @param portForward The port forward to update
     */
    suspend fun updatePortForward(portForward: PortForward)

    /**
     * Delete a port forward configuration.
     * @param portForward The port forward to delete
     */
    suspend fun deletePortForward(portForward: PortForward)
}

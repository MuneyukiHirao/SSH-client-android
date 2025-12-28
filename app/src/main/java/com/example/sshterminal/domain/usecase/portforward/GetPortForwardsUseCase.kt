package com.example.sshterminal.domain.usecase.portforward

import com.example.sshterminal.domain.model.PortForward
import com.example.sshterminal.domain.repository.PortForwardRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case to get port forwards for a host.
 */
class GetPortForwardsUseCase(
    private val portForwardRepository: PortForwardRepository
) {
    operator fun invoke(hostId: Long): Flow<List<PortForward>> =
        portForwardRepository.getPortForwardsForHost(hostId)
}

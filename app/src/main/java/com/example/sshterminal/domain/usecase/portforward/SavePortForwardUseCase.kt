package com.example.sshterminal.domain.usecase.portforward

import com.example.sshterminal.domain.model.PortForward
import com.example.sshterminal.domain.repository.PortForwardRepository

/**
 * Use case to save (insert or update) a port forward.
 */
class SavePortForwardUseCase(
    private val portForwardRepository: PortForwardRepository
) {
    suspend operator fun invoke(portForward: PortForward) {
        if (portForward.id == 0L) {
            portForwardRepository.insertPortForward(portForward)
        } else {
            portForwardRepository.updatePortForward(portForward)
        }
    }
}

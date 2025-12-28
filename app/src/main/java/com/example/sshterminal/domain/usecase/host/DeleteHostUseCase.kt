package com.example.sshterminal.domain.usecase.host

import com.example.sshterminal.domain.model.Host
import com.example.sshterminal.domain.repository.HostRepository

/**
 * Use case to delete a host.
 */
class DeleteHostUseCase(
    private val hostRepository: HostRepository
) {
    suspend operator fun invoke(host: Host) = hostRepository.deleteHost(host)
}

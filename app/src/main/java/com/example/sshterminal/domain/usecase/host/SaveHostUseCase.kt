package com.example.sshterminal.domain.usecase.host

import com.example.sshterminal.domain.model.Host
import com.example.sshterminal.domain.repository.HostRepository

/**
 * Use case to save (insert or update) a host.
 */
class SaveHostUseCase(
    private val hostRepository: HostRepository
) {
    suspend operator fun invoke(host: Host): Result<Long> = runCatching {
        if (host.id == 0L) {
            hostRepository.insertHost(host)
        } else {
            hostRepository.updateHost(host)
            host.id
        }
    }
}

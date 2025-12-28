package com.example.sshterminal.domain.usecase.host

import com.example.sshterminal.domain.model.Host
import com.example.sshterminal.domain.repository.HostRepository

/**
 * Use case to get a host by ID.
 */
class GetHostByIdUseCase(
    private val hostRepository: HostRepository
) {
    suspend operator fun invoke(id: Long): Host? = hostRepository.getHostById(id)
}

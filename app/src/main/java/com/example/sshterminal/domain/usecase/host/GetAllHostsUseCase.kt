package com.example.sshterminal.domain.usecase.host

import com.example.sshterminal.domain.model.Host
import com.example.sshterminal.domain.repository.HostRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case to get all hosts.
 */
class GetAllHostsUseCase(
    private val hostRepository: HostRepository
) {
    operator fun invoke(): Flow<List<Host>> = hostRepository.getAllHosts()
}

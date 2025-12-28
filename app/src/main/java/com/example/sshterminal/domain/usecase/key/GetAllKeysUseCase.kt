package com.example.sshterminal.domain.usecase.key

import com.example.sshterminal.domain.model.SSHKey
import com.example.sshterminal.domain.repository.KeyRepository

/**
 * Use case to get all SSH keys.
 */
class GetAllKeysUseCase(
    private val keyRepository: KeyRepository
) {
    suspend operator fun invoke(): List<SSHKey> = keyRepository.getAllKeys()
}

package com.example.sshterminal.domain.usecase.key

import com.example.sshterminal.domain.model.SSHKey
import com.example.sshterminal.domain.repository.KeyRepository

/**
 * Use case to generate a new SSH key.
 */
class GenerateKeyUseCase(
    private val keyRepository: KeyRepository
) {
    suspend operator fun invoke(
        name: String,
        keyType: String,
        keySize: Int
    ): Result<SSHKey> = keyRepository.generateKey(name, keyType, keySize)
}

package com.example.sshterminal.domain.usecase.key

import com.example.sshterminal.domain.repository.KeyRepository

/**
 * Use case to delete an SSH key.
 */
class DeleteKeyUseCase(
    private val keyRepository: KeyRepository
) {
    suspend operator fun invoke(alias: String): Boolean = keyRepository.deleteKey(alias)
}

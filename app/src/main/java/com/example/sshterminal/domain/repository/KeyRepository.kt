package com.example.sshterminal.domain.repository

import com.example.sshterminal.domain.model.SSHKey
import java.security.KeyPair

/**
 * Repository interface for SSH key operations.
 * Provides abstraction over KeyStoreManager for clean architecture.
 */
interface KeyRepository {

    /**
     * Get all SSH keys stored in the keystore.
     * @return List of all SSH keys
     */
    suspend fun getAllKeys(): List<SSHKey>

    /**
     * Generate a new SSH key pair.
     * @param name The name/alias for the key
     * @param keyType The type of key (RSA, Ed25519, ECDSA)
     * @param keySize The key size in bits
     * @return Result containing the generated SSHKey or an error
     */
    suspend fun generateKey(name: String, keyType: String, keySize: Int): Result<SSHKey>

    /**
     * Delete an SSH key from the keystore.
     * @param alias The key alias to delete
     * @return true if deletion was successful, false otherwise
     */
    suspend fun deleteKey(alias: String): Boolean

    /**
     * Get the KeyPair for a specific key alias.
     * @param alias The key alias
     * @return The KeyPair if found, null otherwise
     */
    fun getKeyPair(alias: String): KeyPair?

    /**
     * Get the public key in OpenSSH PEM format.
     * @param alias The key alias
     * @return The public key string in OpenSSH format, null if not found
     */
    fun getPublicKeyPEM(alias: String): String?

    /**
     * Get the private key in PEM format.
     * @param alias The key alias
     * @param password Optional password for encrypted keys (currently unused)
     * @return The private key as CharArray, null if not found
     */
    fun getPrivateKeyPEM(alias: String, password: String? = null): CharArray?

    /**
     * Check if a key is an Ed25519 key.
     * Ed25519 keys are stored differently than RSA/ECDSA keys.
     * @param alias The key alias
     * @return true if the key is Ed25519, false otherwise
     */
    fun isEd25519Key(alias: String): Boolean
}

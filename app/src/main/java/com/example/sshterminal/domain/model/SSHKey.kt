package com.example.sshterminal.domain.model

enum class KeyType(val displayName: String, val algorithmName: String) {
    RSA_2048("RSA 2048-bit", "RSA"),
    RSA_4096("RSA 4096-bit", "RSA"),
    ED25519("Ed25519", "Ed25519"),
    ECDSA_256("ECDSA P-256", "EC"),
    ECDSA_384("ECDSA P-384", "EC"),
    ECDSA_521("ECDSA P-521", "EC");

    val keySize: Int
        get() = when (this) {
            RSA_2048 -> 2048
            RSA_4096 -> 4096
            ED25519 -> 256
            ECDSA_256 -> 256
            ECDSA_384 -> 384
            ECDSA_521 -> 521
        }
}

data class SSHKey(
    val alias: String,
    val type: KeyType,
    val publicKeyOpenSSH: String,
    val fingerprint: String,
    val createdAt: Long = System.currentTimeMillis()
)

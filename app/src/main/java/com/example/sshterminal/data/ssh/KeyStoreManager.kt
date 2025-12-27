package com.example.sshterminal.data.ssh

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.sshterminal.domain.model.KeyType
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.example.sshterminal.domain.model.SSHKey
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECParameterSpec
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.util.io.pem.PemObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.StringWriter
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KeyStoreManager(private val context: Context) {

    companion object {
        private const val TAG = "KeyStoreManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX = "ssh_key_"
        private const val PREFS_NAME = "ed25519_keys"
        private const val KEY_PRIVATE_SUFFIX = "_private"
        private const val KEY_PUBLIC_SUFFIX = "_public"
        private const val MASTER_KEY_ALIAS = "ssh_master_key"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    // Regular SharedPreferences for storing encrypted Ed25519 keys
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Get or create master key for encrypting Ed25519 keys
    private fun getMasterKey(): SecretKey {
        val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null)
        if (entry is KeyStore.SecretKeyEntry) {
            return entry.secretKey
        }

        // Generate new master key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(data: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())

        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)

        // Combine IV + encrypted data
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedBase64: String): ByteArray {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), spec)

        return cipher.doFinal(encrypted)
    }

    // Cache for Ed25519 keys (loaded from encrypted storage)
    private val ed25519Keys = mutableMapOf<String, KeyPair>()

    suspend fun generateKey(name: String, keyType: KeyType): Result<SSHKey> = withContext(Dispatchers.Default) {
        try {
            val alias = KEY_ALIAS_PREFIX + name.replace(Regex("[^a-zA-Z0-9_]"), "_")

            val keyPair = when (keyType) {
                KeyType.RSA_2048, KeyType.RSA_4096 -> generateRSAKey(alias, keyType.keySize)
                KeyType.ED25519 -> generateEd25519Key(alias)
                KeyType.ECDSA_256 -> generateECDSAKey(alias, "secp256r1")
                KeyType.ECDSA_384 -> generateECDSAKey(alias, "secp384r1")
                KeyType.ECDSA_521 -> generateECDSAKey(alias, "secp521r1")
            }

            val publicKeyOpenSSH = formatPublicKeyOpenSSH(keyPair.public, keyType, name)
            val fingerprint = calculateFingerprint(keyPair.public)

            Result.success(
                SSHKey(
                    alias = alias,
                    type = keyType,
                    publicKeyOpenSSH = publicKeyOpenSSH,
                    fingerprint = fingerprint
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateRSAKey(alias: String, keySize: Int): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        )

        val paramSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(keySize, RSAKeyGenParameterSpec.F4))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setUserAuthenticationRequired(false)
            .build()

        keyPairGenerator.initialize(paramSpec)
        return keyPairGenerator.generateKeyPair()
    }

    private fun generateECDSAKey(alias: String, curveName: String): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )

        val paramSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(curveName))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
            .setUserAuthenticationRequired(false)
            .build()

        keyPairGenerator.initialize(paramSpec)
        return keyPairGenerator.generateKeyPair()
    }

    private fun generateEd25519Key(alias: String): KeyPair {
        Log.d(TAG, "Generating Ed25519 key with alias: $alias")
        // Ed25519 is not supported by Android Keystore, use BouncyCastle
        val keyPairGenerator = KeyPairGenerator.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
        keyPairGenerator.initialize(256, SecureRandom())
        val keyPair = keyPairGenerator.generateKeyPair()

        // Store in encrypted storage
        saveEd25519Key(alias, keyPair)

        // Cache in memory
        ed25519Keys[alias] = keyPair

        Log.d(TAG, "Ed25519 key generated and saved: $alias")
        return keyPair
    }

    private fun saveEd25519Key(alias: String, keyPair: KeyPair) {
        // Encrypt the private and public keys
        val encryptedPrivate = encrypt(keyPair.private.encoded)
        val encryptedPublic = encrypt(keyPair.public.encoded)

        prefs.edit()
            .putString(alias + KEY_PRIVATE_SUFFIX, encryptedPrivate)
            .putString(alias + KEY_PUBLIC_SUFFIX, encryptedPublic)
            .apply()
    }

    private fun loadEd25519Key(alias: String): KeyPair? {
        val encryptedPrivate = prefs.getString(alias + KEY_PRIVATE_SUFFIX, null) ?: return null
        val encryptedPublic = prefs.getString(alias + KEY_PUBLIC_SUFFIX, null) ?: return null

        return try {
            val keyFactory = KeyFactory.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
            val privateKeyBytes = decrypt(encryptedPrivate)
            val publicKeyBytes = decrypt(encryptedPublic)

            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))

            KeyPair(publicKey, privateKey)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Ed25519 key: $alias", e)
            null
        }
    }

    private fun getEd25519Aliases(): List<String> {
        return prefs.all.keys
            .filter { it.endsWith(KEY_PRIVATE_SUFFIX) }
            .map { it.removeSuffix(KEY_PRIVATE_SUFFIX) }
    }

    fun getPrivateKey(alias: String): PrivateKey? {
        // Check Ed25519 cache first
        ed25519Keys[alias]?.let { return it.private }

        // Try to load Ed25519 from encrypted storage
        loadEd25519Key(alias)?.let { keyPair ->
            ed25519Keys[alias] = keyPair
            return keyPair.private
        }

        // Check Android Keystore
        return try {
            keyStore.getKey(alias, null) as? PrivateKey
        } catch (e: Exception) {
            null
        }
    }

    fun getPublicKey(alias: String): PublicKey? {
        // Check Ed25519 cache first
        ed25519Keys[alias]?.let { return it.public }

        // Try to load Ed25519 from encrypted storage
        loadEd25519Key(alias)?.let { keyPair ->
            ed25519Keys[alias] = keyPair
            return keyPair.public
        }

        // Check Android Keystore
        return try {
            keyStore.getCertificate(alias)?.publicKey
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAllKeys(): List<SSHKey> = withContext(Dispatchers.IO) {
        val keys = mutableListOf<SSHKey>()

        // Get keys from Android Keystore
        try {
            val aliases = keyStore.aliases().toList()
            for (alias in aliases) {
                if (alias.startsWith(KEY_ALIAS_PREFIX)) {
                    val publicKey = keyStore.getCertificate(alias)?.publicKey ?: continue
                    val keyType = determineKeyType(publicKey)
                    val name = alias.removePrefix(KEY_ALIAS_PREFIX)

                    keys.add(
                        SSHKey(
                            alias = alias,
                            type = keyType,
                            publicKeyOpenSSH = formatPublicKeyOpenSSH(publicKey, keyType, name),
                            fingerprint = calculateFingerprint(publicKey)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading keys from Android Keystore", e)
        }

        // Get Ed25519 keys from encrypted storage
        try {
            val ed25519Aliases = getEd25519Aliases()
            for (alias in ed25519Aliases) {
                val keyPair = ed25519Keys[alias] ?: loadEd25519Key(alias)
                if (keyPair != null) {
                    ed25519Keys[alias] = keyPair
                    val name = alias.removePrefix(KEY_ALIAS_PREFIX)
                    keys.add(
                        SSHKey(
                            alias = alias,
                            type = KeyType.ED25519,
                            publicKeyOpenSSH = formatPublicKeyOpenSSH(keyPair.public, KeyType.ED25519, name),
                            fingerprint = calculateFingerprint(keyPair.public)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Ed25519 keys", e)
        }

        keys
    }

    suspend fun deleteKey(alias: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Remove from Ed25519 memory cache
            ed25519Keys.remove(alias)

            // Remove from encrypted storage
            prefs.edit()
                .remove(alias + KEY_PRIVATE_SUFFIX)
                .remove(alias + KEY_PUBLIC_SUFFIX)
                .apply()

            // Remove from Android Keystore
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting key: $alias", e)
            false
        }
    }

    private fun determineKeyType(publicKey: PublicKey): KeyType {
        return when (publicKey) {
            is RSAPublicKey -> {
                if (publicKey.modulus.bitLength() >= 4096) KeyType.RSA_4096 else KeyType.RSA_2048
            }
            is ECPublicKey -> {
                when (publicKey.params.order.bitLength()) {
                    256 -> KeyType.ECDSA_256
                    384 -> KeyType.ECDSA_384
                    else -> KeyType.ECDSA_521
                }
            }
            else -> KeyType.ED25519
        }
    }

    private fun formatPublicKeyOpenSSH(publicKey: PublicKey, keyType: KeyType, comment: String): String {
        val keyData = when (keyType) {
            KeyType.RSA_2048, KeyType.RSA_4096 -> formatRSAPublicKey(publicKey as RSAPublicKey)
            KeyType.ED25519 -> formatEd25519PublicKey(publicKey)
            KeyType.ECDSA_256, KeyType.ECDSA_384, KeyType.ECDSA_521 -> formatECDSAPublicKey(publicKey as ECPublicKey, keyType)
        }

        val keyTypeString = when (keyType) {
            KeyType.RSA_2048, KeyType.RSA_4096 -> "ssh-rsa"
            KeyType.ED25519 -> "ssh-ed25519"
            KeyType.ECDSA_256 -> "ecdsa-sha2-nistp256"
            KeyType.ECDSA_384 -> "ecdsa-sha2-nistp384"
            KeyType.ECDSA_521 -> "ecdsa-sha2-nistp521"
        }

        val base64Key = Base64.encodeToString(keyData, Base64.NO_WRAP)
        return "$keyTypeString $base64Key $comment"
    }

    private fun formatRSAPublicKey(publicKey: RSAPublicKey): ByteArray {
        val out = ByteArrayOutputStream()

        // Write key type
        writeString(out, "ssh-rsa")

        // Write public exponent
        writeMPInt(out, publicKey.publicExponent)

        // Write modulus
        writeMPInt(out, publicKey.modulus)

        return out.toByteArray()
    }

    private fun formatEd25519PublicKey(publicKey: PublicKey): ByteArray {
        val out = ByteArrayOutputStream()

        // Write key type
        writeString(out, "ssh-ed25519")

        // Get raw key bytes
        val rawKey = try {
            // Try to get the raw key from BouncyCastle
            val keyBytes = publicKey.encoded
            // Ed25519 public key is 32 bytes, but encoded form has header
            if (keyBytes.size == 44) {
                keyBytes.copyOfRange(12, 44)
            } else {
                keyBytes
            }
        } catch (e: Exception) {
            ByteArray(32)
        }

        writeBytes(out, rawKey)

        return out.toByteArray()
    }

    private fun formatECDSAPublicKey(publicKey: ECPublicKey, keyType: KeyType): ByteArray {
        val out = ByteArrayOutputStream()

        val curveName = when (keyType) {
            KeyType.ECDSA_256 -> "nistp256"
            KeyType.ECDSA_384 -> "nistp384"
            KeyType.ECDSA_521 -> "nistp521"
            else -> throw IllegalArgumentException("Not an ECDSA key type")
        }

        val keyTypeString = "ecdsa-sha2-$curveName"

        // Write key type
        writeString(out, keyTypeString)

        // Write curve name
        writeString(out, curveName)

        // Write point (uncompressed format)
        val point = publicKey.w
        val fieldSize = (publicKey.params.order.bitLength() + 7) / 8

        val x = point.affineX.toByteArray().let { bytes ->
            if (bytes.size > fieldSize) bytes.copyOfRange(bytes.size - fieldSize, bytes.size)
            else if (bytes.size < fieldSize) ByteArray(fieldSize - bytes.size) + bytes
            else bytes
        }

        val y = point.affineY.toByteArray().let { bytes ->
            if (bytes.size > fieldSize) bytes.copyOfRange(bytes.size - fieldSize, bytes.size)
            else if (bytes.size < fieldSize) ByteArray(fieldSize - bytes.size) + bytes
            else bytes
        }

        val pointBytes = byteArrayOf(0x04) + x + y
        writeBytes(out, pointBytes)

        return out.toByteArray()
    }

    private fun writeString(out: ByteArrayOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        writeInt(out, bytes.size)
        out.write(bytes)
    }

    private fun writeBytes(out: ByteArrayOutputStream, bytes: ByteArray) {
        writeInt(out, bytes.size)
        out.write(bytes)
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 24) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeMPInt(out: ByteArrayOutputStream, value: BigInteger) {
        var bytes = value.toByteArray()
        // Remove leading zero if present and not needed
        if (bytes.size > 1 && bytes[0] == 0.toByte() && (bytes[1].toInt() and 0x80) == 0) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        // Add leading zero if high bit is set (to ensure positive interpretation)
        if ((bytes[0].toInt() and 0x80) != 0) {
            bytes = byteArrayOf(0) + bytes
        }
        writeBytes(out, bytes)
    }

    private fun calculateFingerprint(publicKey: PublicKey): String {
        val keyBytes = publicKey.encoded
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(keyBytes)

        return "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun getKeyPair(alias: String): KeyPair? {
        val privateKey = getPrivateKey(alias) ?: return null
        val publicKey = getPublicKey(alias) ?: return null
        return KeyPair(publicKey, privateKey)
    }

    /**
     * Get private key in PEM format for SSH authentication
     * Generates OpenSSH format for Ed25519 keys
     */
    fun getPrivateKeyPEM(alias: String): CharArray? {
        val keyPair = getKeyPair(alias) ?: return null
        val privateKey = keyPair.private
        val publicKey = keyPair.public

        return try {
            // For Ed25519 keys, generate OpenSSH format
            if (privateKey.algorithm == "Ed25519" || privateKey.algorithm == "EdDSA") {
                generateOpenSSHPrivateKey(privateKey, publicKey).toCharArray()
            } else {
                // For other keys, use standard PEM format
                val stringWriter = StringWriter()
                JcaPEMWriter(stringWriter).use { pemWriter ->
                    pemWriter.writeObject(privateKey)
                }
                stringWriter.toString().toCharArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert private key to PEM: $alias", e)
            null
        }
    }

    /**
     * Generate OpenSSH format private key for Ed25519
     */
    private fun generateOpenSSHPrivateKey(privateKey: PrivateKey, publicKey: PublicKey): String {
        // Extract the 32-byte seed from PKCS8 encoded private key
        val privateKeyInfo = org.bouncycastle.asn1.pkcs.PrivateKeyInfo.getInstance(privateKey.encoded)
        val privateKeyOctets = org.bouncycastle.asn1.ASN1OctetString.getInstance(privateKeyInfo.parsePrivateKey()).octets
        val bcPrivateKey = Ed25519PrivateKeyParameters(privateKeyOctets, 0)
        val bcPublicKey = bcPrivateKey.generatePublicKey()

        val privateKeyBytes = bcPrivateKey.encoded  // 32 bytes seed
        val publicKeyBytes = bcPublicKey.encoded   // 32 bytes public

        // Build OpenSSH format
        val baos = ByteArrayOutputStream()

        // Magic bytes "openssh-key-v1" + null terminator
        baos.write("openssh-key-v1\u0000".toByteArray())

        // Cipher name (none for unencrypted)
        writeOpenSSHString(baos, "none")
        // KDF name
        writeOpenSSHString(baos, "none")
        // KDF options (empty)
        writeOpenSSHBytes(baos, ByteArray(0))

        // Number of keys (big endian)
        writeOpenSSHInt(baos, 1)

        // Public key section
        val pubKeyBaos = ByteArrayOutputStream()
        writeOpenSSHString(pubKeyBaos, "ssh-ed25519")
        writeOpenSSHBytes(pubKeyBaos, publicKeyBytes)
        val pubKeyData = pubKeyBaos.toByteArray()
        writeOpenSSHBytes(baos, pubKeyData)

        // Private key section (includes public for verification)
        val privKeyBaos = ByteArrayOutputStream()

        // Check bytes (random, must match each other)
        val checkInt = (System.currentTimeMillis() and 0xFFFFFFFFL).toInt()
        writeOpenSSHInt(privKeyBaos, checkInt)
        writeOpenSSHInt(privKeyBaos, checkInt)

        // Key type
        writeOpenSSHString(privKeyBaos, "ssh-ed25519")
        // Public key
        writeOpenSSHBytes(privKeyBaos, publicKeyBytes)
        // Private key (64 bytes: 32 seed + 32 public)
        val fullPrivate = ByteArray(64)
        System.arraycopy(privateKeyBytes, 0, fullPrivate, 0, 32)
        System.arraycopy(publicKeyBytes, 0, fullPrivate, 32, 32)
        writeOpenSSHBytes(privKeyBaos, fullPrivate)
        // Comment (empty)
        writeOpenSSHString(privKeyBaos, "")

        // Padding to block size (8 bytes)
        var padByte = 1
        while (privKeyBaos.size() % 8 != 0) {
            privKeyBaos.write(padByte++)
        }

        val privKeyData = privKeyBaos.toByteArray()
        writeOpenSSHBytes(baos, privKeyData)

        val keyData = baos.toByteArray()
        val base64Data = Base64.encodeToString(keyData, Base64.NO_WRAP)

        // Format with 70 char line length
        val sb = StringBuilder()
        sb.append("-----BEGIN OPENSSH PRIVATE KEY-----\n")
        var i = 0
        while (i < base64Data.length) {
            sb.append(base64Data.substring(i, minOf(i + 70, base64Data.length)))
            sb.append("\n")
            i += 70
        }
        sb.append("-----END OPENSSH PRIVATE KEY-----\n")

        return sb.toString()
    }

    private fun writeOpenSSHInt(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 24) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeOpenSSHString(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeOpenSSHInt(out, bytes.size)
        out.write(bytes)
    }

    private fun writeOpenSSHBytes(out: ByteArrayOutputStream, bytes: ByteArray) {
        writeOpenSSHInt(out, bytes.size)
        out.write(bytes)
    }

    /**
     * Check if the key is an Ed25519 key
     */
    fun isEd25519Key(alias: String): Boolean {
        return prefs.contains(alias + KEY_PRIVATE_SUFFIX)
    }
}

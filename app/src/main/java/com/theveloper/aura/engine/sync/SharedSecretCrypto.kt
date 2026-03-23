package com.theveloper.aura.engine.sync

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM encryption using a shared secret derived during device pairing.
 * Used for direct phone-to-desktop sync over WebSocket.
 *
 * Unlike [CryptoHelper] which uses Android Keystore, this implementation
 * works with a key that both the phone and desktop know — the pairing shared secret.
 */
class SharedSecretCrypto(sharedSecret: String) : SyncCrypto {

    private val secretKey: SecretKey = deriveKey(sharedSecret)

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BIT_LENGTH, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    override fun decrypt(ciphertext: String): String {
        val combined = Base64.decode(ciphertext, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, IV_SIZE)
        val encrypted = combined.copyOfRange(IV_SIZE, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BIT_LENGTH, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun deriveKey(secret: String): SecretKey {
        // SHA-256 hash of the shared secret to get a 256-bit AES key.
        // In production, use HKDF or PBKDF2 for proper key derivation.
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(secret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BIT_LENGTH = 128
    }
}

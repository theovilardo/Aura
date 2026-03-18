package com.theveloper.aura.engine.sync

/**
 * Abstraction over encryption/decryption for sync payloads.
 *
 * Implementations:
 * - [CryptoHelper] uses Android Keystore (for Supabase cloud sync)
 * - [SharedSecretCrypto] uses a shared secret from device pairing (for direct desktop sync)
 */
interface SyncCrypto {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

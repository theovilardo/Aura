package com.theveloper.aura.engine.ecosystem

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrustTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun putToken(deviceId: String, trustToken: String) {
        prefs.edit().putString(tokenKey(deviceId), trustToken).apply()
    }

    fun getToken(deviceId: String): String? =
        prefs.getString(tokenKey(deviceId), null)

    fun removeToken(deviceId: String) {
        prefs.edit().remove(tokenKey(deviceId)).apply()
    }

    private fun tokenKey(deviceId: String): String = "trust_token_$deviceId"

    private companion object {
        const val PREFS_NAME = "aura_secure_prefs"
    }
}

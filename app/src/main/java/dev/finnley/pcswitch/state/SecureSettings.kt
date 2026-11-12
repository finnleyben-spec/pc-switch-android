package dev.finnley.pcswitch.state

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Log

/**
 * Secure settings store using EncryptedSharedPreferences for sensitive data.
 * The HA access token is encrypted at rest, preventing extraction from app data
 * on rooted devices or via backup (allowBackup="false" for encrypted prefs).
 *
 * Migration: On first access, if a plaintext token exists in legacy preferences,
 * it is copied to encrypted storage and removed from the legacy store.
 */
class SecureSettings(context: Context) {
    private val legacyPrefs: SharedPreferences
    private val encryptedPrefs: SharedPreferences

    companion object {
        const val KEY_TOKEN = "ha_token"
        const val KEY_TOKEN_MIGRATED = "token_migrated"
    }

    init {
        legacyPrefs = context.getSharedPreferences("dev.finnley.pcswitch.preferences", Context.MODE_PRIVATE)

        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            "dev.finnley.pcswitch.encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Get the HA token from encrypted storage.
     * Migrates from legacy plaintext storage if needed.
     */
    fun getToken(): String {
        // Check encrypted storage first
        val encrypted = encryptedPrefs.getString(KEY_TOKEN, null)
        if (encrypted != null) {
            return encrypted
        }

        // Migration: copy from legacy plaintext storage
        val migrated = legacyPrefs.getBoolean(KEY_TOKEN_MIGRATED, false)
        if (!migrated) {
            val legacy = legacyPrefs.getString(KEY_TOKEN, null)
            if (legacy != null && legacy.isNotBlank()) {
                Log.i("SecureSettings", "Migrating token from legacy storage")
                encryptedPrefs.edit().putString(KEY_TOKEN, legacy).apply()
                legacyPrefs.edit()
                    .remove(KEY_TOKEN)
                    .putBoolean(KEY_TOKEN_MIGRATED, true)
                    .apply()
            } else {
                legacyPrefs.edit().putBoolean(KEY_TOKEN_MIGRATED, true).apply()
            }
            return legacyPrefs.getString(KEY_TOKEN, "") ?: ""
        }

        return ""
    }

    /**
     * Save the HA token to encrypted storage.
     */
    fun setToken(token: String) {
        encryptedPrefs.edit().putString(KEY_TOKEN, token).apply()
        // Also clear from legacy storage if present (security cleanup)
        if (legacyPrefs.contains(KEY_TOKEN)) {
            legacyPrefs.edit().remove(KEY_TOKEN).apply()
        }
        legacyPrefs.edit().putBoolean(KEY_TOKEN_MIGRATED, true).apply()
    }

    /**
     * Check if the token is configured.
     */
    fun hasToken(): Boolean {
        return getToken().isNotBlank()
    }
}
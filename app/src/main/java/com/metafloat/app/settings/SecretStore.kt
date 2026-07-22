package com.metafloat.app.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal interface SecretStorage {
    fun readSecret(): String
    fun writeSecret(secret: String)
}

class SecretStore(context: Context) : SecretStorage {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun readSecret(): String = prefs.getString(KEY_SECRET, "") ?: ""

    override fun writeSecret(secret: String) {
        prefs.edit().putString(KEY_SECRET, secret).apply()
    }

    companion object {
        private const val FILE_NAME = "secret_store"
        private const val KEY_SECRET = "secret"
    }
}

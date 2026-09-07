package com.dailythread.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "daily_thread_session")

class TokenStore(private val context: Context) {
    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val userKey = stringPreferencesKey("user_id")
    private val expiresAtKey = longPreferencesKey("expires_at_ms")
    private val cipher = TokenCipher()

    suspend fun save(access: String, refresh: String, userId: String, expiresInSeconds: Long) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        val encryptedAccess = cipher.encrypt(access)
        val encryptedRefresh = cipher.encrypt(refresh)
        context.dataStore.edit {
            it[accessKey] = encryptedAccess
            it[refreshKey] = encryptedRefresh
            it[userKey] = userId
            it[expiresAtKey] = expiresAt
        }
    }

    fun userIdFlow(): Flow<String?> = context.dataStore.data
        .map { it[userKey] }
        .distinctUntilChanged()

    suspend fun accessToken(): String? = readToken(accessKey)
    suspend fun refreshToken(): String? = readToken(refreshKey)
    suspend fun userId(): String? = context.dataStore.data.first()[userKey]
    suspend fun expiresAtMs(): Long = context.dataStore.data.first()[expiresAtKey] ?: 0L
    suspend fun hasOfflineSession(): Boolean = userId() != null
    suspend fun clear() = context.dataStore.edit { it.clear() }

    private suspend fun readToken(key: Preferences.Key<String>): String? {
        val stored = context.dataStore.data.first()[key]
        if (stored.isNullOrBlank()) return null
        val plain = runCatching { cipher.decrypt(stored) }.getOrNull() ?: return null

        // Existing RC installs may still contain the pre-Keystore plaintext value.
        // Re-wrap it immediately on first successful read after upgrade.
        if (!stored.startsWith("v1:")) {
            val encrypted = runCatching { cipher.encrypt(plain) }.getOrNull()
            if (encrypted != null) {
                context.dataStore.edit { it[key] = encrypted }
            }
        }
        return plain
    }
}

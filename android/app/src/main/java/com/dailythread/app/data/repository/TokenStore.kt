package com.dailythread.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "daily_thread_session")

class TokenStore(private val context: Context) {
    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val userKey = stringPreferencesKey("user_id")
    private val emailKey = stringPreferencesKey("user_email")
    private val expiresAtKey = longPreferencesKey("expires_at_ms")
    private val displayNameKey = stringPreferencesKey("profile_display_name")
    private val targetFocusKey = intPreferencesKey("profile_target_focus_minutes")
    private val profileDirtyKey = booleanPreferencesKey("profile_dirty")
    private val cipher = TokenCipher()

    suspend fun save(access: String, refresh: String, userId: String, expiresInSeconds: Long, email: String? = null) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        context.dataStore.edit {
            it[accessKey] = cipher.encrypt(access)
            it[refreshKey] = cipher.encrypt(refresh)
            it[userKey] = userId
            it[expiresAtKey] = expiresAt
            if (!email.isNullOrBlank()) it[emailKey] = email.trim()
        }
    }

    fun userIdFlow(): Flow<String?> = context.dataStore.data.map { it[userKey] }.distinctUntilChanged()
    fun emailFlow(): Flow<String> = context.dataStore.data.map { it[emailKey].orEmpty() }.distinctUntilChanged()
    fun displayNameFlow(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[displayNameKey]?.takeIf { it.isNotBlank() }
            ?: prefs[emailKey]?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "Pengguna"
    }.distinctUntilChanged()
    fun targetFocusFlow(): Flow<Int> = context.dataStore.data.map { (it[targetFocusKey] ?: 120).coerceIn(15, 720) }.distinctUntilChanged()

    suspend fun accessToken(): String? = readToken(accessKey)
    suspend fun refreshToken(): String? = readToken(refreshKey)
    suspend fun userId(): String? = context.dataStore.data.first()[userKey]
    suspend fun email(): String? = context.dataStore.data.first()[emailKey]
    suspend fun expiresAtMs(): Long = context.dataStore.data.first()[expiresAtKey] ?: 0L
    suspend fun hasOfflineSession(): Boolean = userId() != null

    suspend fun ensureLocalUserId(seed: String? = null): String {
        val existing = userId()
        if (!existing.isNullOrBlank()) return existing
        val generated = seed?.takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: UUID.randomUUID().toString()
        context.dataStore.edit { prefs ->
            prefs[userKey] = generated
            if (prefs[displayNameKey].isNullOrBlank()) prefs[displayNameKey] = "Pengguna"
            if (prefs[targetFocusKey] == null) prefs[targetFocusKey] = 120
        }
        return generated
    }

    suspend fun hasCloudSession(): Boolean = !accessToken().isNullOrBlank() && !refreshToken().isNullOrBlank()

    suspend fun saveLocalProfile(displayName: String, targetFocusMinutes: Int) {
        context.dataStore.edit {
            it[displayNameKey] = displayName.trim()
            it[targetFocusKey] = targetFocusMinutes.coerceIn(15, 720)
            it[profileDirtyKey] = true
        }
    }

    suspend fun cacheServerProfile(displayName: String, targetFocusMinutes: Int) {
        context.dataStore.edit {
            it[displayNameKey] = displayName.trim()
            it[targetFocusKey] = targetFocusMinutes.coerceIn(15, 720)
            it[profileDirtyKey] = false
        }
    }

    suspend fun markProfileSynced() { context.dataStore.edit { it[profileDirtyKey] = false } }
    suspend fun profileDirty(): Boolean = context.dataStore.data.first()[profileDirtyKey] ?: false
    suspend fun displayName(): String = context.dataStore.data.first()[displayNameKey].orEmpty()
    suspend fun targetFocusMinutes(): Int = (context.dataStore.data.first()[targetFocusKey] ?: 120).coerceIn(15, 720)
    suspend fun clear() = context.dataStore.edit { it.clear() }

    private suspend fun readToken(key: Preferences.Key<String>): String? {
        val stored = context.dataStore.data.first()[key]
        if (stored.isNullOrBlank()) return null
        val plain = runCatching { cipher.decrypt(stored) }.getOrNull() ?: return null
        if (!stored.startsWith("v1:")) {
            val encrypted = runCatching { cipher.encrypt(plain) }.getOrNull()
            if (encrypted != null) context.dataStore.edit { it[key] = encrypted }
        }
        return plain
    }
}

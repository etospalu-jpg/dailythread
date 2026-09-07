package com.dailythread.app.data.repository

import android.content.Context
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

    suspend fun save(access: String, refresh: String, userId: String, expiresInSeconds: Long) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        context.dataStore.edit {
            it[accessKey] = access
            it[refreshKey] = refresh
            it[userKey] = userId
            it[expiresAtKey] = expiresAt
        }
    }

    fun userIdFlow(): Flow<String?> = context.dataStore.data
        .map { it[userKey] }
        .distinctUntilChanged()

    suspend fun accessToken(): String? = context.dataStore.data.first()[accessKey]
    suspend fun refreshToken(): String? = context.dataStore.data.first()[refreshKey]
    suspend fun userId(): String? = context.dataStore.data.first()[userKey]
    suspend fun expiresAtMs(): Long = context.dataStore.data.first()[expiresAtKey] ?: 0L
    suspend fun hasOfflineSession(): Boolean = userId() != null
    suspend fun clear() = context.dataStore.edit { it.clear() }
}

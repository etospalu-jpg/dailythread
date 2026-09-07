package com.dailythread.app.data.repository

import com.dailythread.app.data.network.ApiFactory
import com.dailythread.app.data.network.ProfilePatch

class ProfileRepository(private val tokenStore: TokenStore) {
    private val auth = AuthRepository(tokenStore)

    suspend fun hydrateFromCloud(): Result<Unit> {
        if (tokenStore.profileDirty()) return Result.success(Unit)
        return runCatching {
            val userId = requireNotNull(tokenStore.userId()) { "Login required" }
            val token = requireNotNull(auth.validAccessToken()) { "Session unavailable" }
            val row = ApiFactory.profile.getProfile(authorization = "Bearer $token", idFilter = "eq.$userId").firstOrNull() ?: return@runCatching
            tokenStore.cacheServerProfile(row.displayName, row.targetFocusMinutes)
        }
    }

    suspend fun save(displayName: String, targetFocusMinutes: Int): Result<Boolean> {
        val local = runCatching {
            val cleanName = displayName.trim()
            require(cleanName.length <= 80) { "Nama terlalu panjang." }
            tokenStore.saveLocalProfile(cleanName, targetFocusMinutes.coerceIn(15, 720))
        }
        if (local.isFailure) return Result.failure(requireNotNull(local.exceptionOrNull()))
        return syncDirty()
    }

    suspend fun syncDirty(): Result<Boolean> {
        if (!tokenStore.profileDirty()) return Result.success(true)
        return runCatching {
            val userId = requireNotNull(tokenStore.userId()) { "Login required" }
            val token = requireNotNull(auth.validAccessToken()) { "Session unavailable" }
            val response = ApiFactory.profile.updateProfile(
                authorization = "Bearer $token",
                idFilter = "eq.$userId",
                patch = ProfilePatch(tokenStore.displayName(), tokenStore.targetFocusMinutes())
            )
            if (response.isNotEmpty()) tokenStore.markProfileSynced()
            response.isNotEmpty()
        }
    }
}

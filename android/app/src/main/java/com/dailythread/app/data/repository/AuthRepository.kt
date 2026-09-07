package com.dailythread.app.data.repository

import com.dailythread.app.data.network.ApiFactory
import com.dailythread.app.data.network.LoginRequest
import com.dailythread.app.data.network.RefreshRequest

class AuthRepository(private val tokenStore: TokenStore) {
    suspend fun login(email: String, password: String): Result<String> = runCatching {
        val res = ApiFactory.auth.login(request = LoginRequest(email.trim(), password))
        tokenStore.save(res.accessToken, res.refreshToken, res.user.id, res.expiresIn)
        res.user.id
    }

    suspend fun validAccessToken(): String? {
        val current = tokenStore.accessToken() ?: return null
        if (System.currentTimeMillis() < tokenStore.expiresAtMs() - 60_000L) return current
        val refresh = tokenStore.refreshToken() ?: return current
        return runCatching {
            val res = ApiFactory.auth.refresh(request = RefreshRequest(refresh))
            tokenStore.save(res.accessToken, res.refreshToken, res.user.id, res.expiresIn)
            res.accessToken
        }.getOrElse { current }
    }
}

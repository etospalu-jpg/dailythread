package com.dailythread.app.data.repository

import com.dailythread.app.data.network.ApiFactory
import com.dailythread.app.data.network.LoginRequest
import com.dailythread.app.data.network.RefreshRequest
import com.dailythread.app.data.network.SignupRequest

data class SignupResult(
    val userId: String,
    val sessionReady: Boolean
)

class AuthRepository(private val tokenStore: TokenStore) {
    suspend fun login(email: String, password: String): Result<String> = runCatching {
        val res = ApiFactory.auth.login(request = LoginRequest(email.trim(), password))
        tokenStore.save(res.accessToken, res.refreshToken, res.user.id, res.expiresIn)
        res.user.id
    }

    suspend fun signup(email: String, password: String): Result<SignupResult> = runCatching {
        val cleanEmail = email.trim()
        require(cleanEmail.isNotEmpty()) { "Email wajib diisi." }
        require(password.length >= 6) { "Password minimal 6 karakter." }

        val res = ApiFactory.auth.signup(request = SignupRequest(cleanEmail, password))
        val user = requireNotNull(res.user) { "Akun tidak berhasil dibuat." }
        val access = res.accessToken
        val refresh = res.refreshToken
        val expires = res.expiresIn
        val sessionReady = !access.isNullOrBlank() && !refresh.isNullOrBlank() && expires != null

        if (sessionReady) {
            tokenStore.save(
                access = requireNotNull(access),
                refresh = requireNotNull(refresh),
                userId = user.id,
                expiresInSeconds = requireNotNull(expires)
            )
        }
        SignupResult(user.id, sessionReady)
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

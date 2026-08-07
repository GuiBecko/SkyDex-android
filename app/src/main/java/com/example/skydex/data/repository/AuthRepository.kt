package com.example.skydex.data.repository

import com.example.skydex.data.remote.SkyDexApi
import com.example.skydex.data.remote.dto.LoginRequest
import com.example.skydex.data.remote.dto.RegisterRequest
import com.example.skydex.data.session.Session
import com.example.skydex.data.session.SessionStore
import kotlinx.coroutines.flow.Flow

class AuthRepository(
    private val api: SkyDexApi,
    private val sessionStore: SessionStore
) {

    val session: Flow<Session?> = sessionStore.session

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val response = api.login(LoginRequest(email.trim(), password))
        sessionStore.save(response.token, response.userId)
    }

    suspend fun register(name: String, email: String, password: String): Result<Unit> = runCatching {
        api.register(RegisterRequest(name.trim(), email.trim(), password))
    }

    suspend fun logout() {
        sessionStore.clear()
    }
}

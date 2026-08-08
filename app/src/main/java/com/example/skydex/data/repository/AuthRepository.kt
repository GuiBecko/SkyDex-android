package com.example.skydex.data.repository

import com.example.skydex.data.remote.SkyDexApi
import com.example.skydex.data.remote.dto.LoginRequest
import com.example.skydex.data.remote.dto.RegisterRequest
import com.example.skydex.data.session.Session
import com.example.skydex.data.session.SessionStore
import com.example.skydex.ui.auth.AuthGateway
import kotlinx.coroutines.flow.Flow

class AuthRepository(
    private val api: SkyDexApi,
    private val sessionStore: SessionStore
) : AuthGateway {

    val session: Flow<Session?> = sessionStore.session

    override suspend fun login(email: String, password: String): Result<Unit> = resultOf {
        val response = api.login(LoginRequest(email.trim(), password))
        sessionStore.save(response.token, response.userId)
    }

    override suspend fun register(name: String, email: String, password: String): Result<Unit> = resultOf {
        api.register(RegisterRequest(name.trim(), email.trim(), password))
    }

    override suspend fun logout() {
        sessionStore.clear()
    }
}

package com.example.skydex.ui.auth

/**
 * What the auth ViewModels need from [com.example.skydex.data.repository.AuthRepository], stated
 * as an interface so a test can hand them a hand-written fake instead of a real network stack.
 */
interface AuthGateway {
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun register(name: String, email: String, password: String): Result<Unit>
    suspend fun logout()
}

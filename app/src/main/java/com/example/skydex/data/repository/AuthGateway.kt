package com.example.skydex.data.repository

/**
 * What the auth ViewModels need from [AuthRepository], stated as an interface so a test can hand
 * them a hand-written fake instead of a real network stack.
 *
 * It lives in the data layer, next to its only implementation, rather than beside its consumers in
 * `ui.auth`: an interface owned by the presentation package would make `data` depend on `ui` and
 * point the dependency arrow backwards for every package that copies this layout.
 */
interface AuthGateway {
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun register(name: String, email: String, password: String): Result<Unit>
    suspend fun logout()
}

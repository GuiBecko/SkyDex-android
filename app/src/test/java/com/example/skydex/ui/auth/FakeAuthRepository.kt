package com.example.skydex.ui.auth

/**
 * [com.example.skydex.data.repository.AuthRepository] is a final class, so this fake subclasses
 * nothing — it re-implements the three methods the auth ViewModels touch behind the
 * [AuthGateway] interface. That interface is the whole reason these ViewModels are testable on
 * the JVM without a mocking framework.
 */
class FakeAuthRepository(private val result: Result<Unit>) : AuthGateway {

    var loginCalls = 0
        private set

    var registerCalls = 0
        private set

    /** The arguments of the last [register] call, so tests can assert what was forwarded. */
    var lastRegistration: Triple<String, String, String>? = null
        private set

    override suspend fun login(email: String, password: String): Result<Unit> {
        loginCalls++
        return result
    }

    override suspend fun register(name: String, email: String, password: String): Result<Unit> {
        registerCalls++
        lastRegistration = Triple(name, email, password)
        return result
    }

    override suspend fun logout() = Unit
}

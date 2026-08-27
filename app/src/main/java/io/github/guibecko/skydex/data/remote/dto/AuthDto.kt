package io.github.guibecko.skydex.data.remote.dto

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val userId: String,
    val name: String
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

data class UserResponse(
    val id: String,
    val name: String,
    val email: String,
    val joinedAt: String
)

data class ErrorResponse(
    val error: String
)

package com.example.skydex.dto

//
data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse (
    val mensagem: String,
    val tokenGerado: String,
    val userId: String
)


data class RegisterRequest(
    val nome: String,
    val email: String,
    val password: String
)
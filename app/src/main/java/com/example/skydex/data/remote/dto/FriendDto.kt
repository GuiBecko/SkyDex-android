package com.example.skydex.data.remote.dto

data class FriendRequestBody(val email: String)

data class FriendRequestResponse(
    val id: String,
    val requesterId: String,
    val requesterName: String,
    val requesterEmail: String,
    val createdAt: String
)

data class FriendResponse(
    val userId: String,
    val name: String,
    val email: String,
    val friendsSince: String
)

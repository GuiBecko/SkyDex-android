package com.example.skydex.ui.social

import com.example.skydex.data.remote.dto.FriendRequestResponse
import com.example.skydex.data.remote.dto.FriendResponse
import com.example.skydex.data.remote.dto.WeatherEventResponse

interface SocialGateway {
    suspend fun sendRequest(email: String): Result<Unit>
    suspend fun incomingRequests(): Result<List<FriendRequestResponse>>
    suspend fun accept(requestId: String): Result<Unit>
    suspend fun decline(requestId: String): Result<Unit>
    suspend fun friends(): Result<List<FriendResponse>>
    suspend fun feed(page: Int, size: Int): Result<List<WeatherEventResponse>>
}

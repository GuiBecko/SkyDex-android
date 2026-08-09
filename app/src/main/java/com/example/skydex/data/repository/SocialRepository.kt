package com.example.skydex.data.repository

import com.example.skydex.data.remote.SkyDexApi
import com.example.skydex.data.remote.dto.FriendRequestBody
import com.example.skydex.data.remote.dto.FriendRequestResponse
import com.example.skydex.data.remote.dto.FriendResponse
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.ui.social.SocialGateway

class SocialRepository(private val api: SkyDexApi) : SocialGateway {

    override suspend fun sendRequest(email: String): Result<Unit> =
        resultOf { api.sendFriendRequest(FriendRequestBody(email.trim())) }.map { }

    override suspend fun incomingRequests(): Result<List<FriendRequestResponse>> =
        resultOf { api.incomingFriendRequests() }

    override suspend fun accept(requestId: String): Result<Unit> =
        resultOf { api.acceptFriendRequest(requestId) }.map { }

    override suspend fun decline(requestId: String): Result<Unit> =
        resultOf { api.declineFriendRequest(requestId) }

    override suspend fun friends(): Result<List<FriendResponse>> =
        resultOf { api.friends() }

    override suspend fun feed(page: Int, size: Int): Result<List<WeatherEventResponse>> =
        resultOf { api.feed(page, size) }
}

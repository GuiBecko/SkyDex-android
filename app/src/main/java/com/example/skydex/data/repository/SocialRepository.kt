package com.example.skydex.data.repository

import com.example.skydex.data.remote.SkyDexApi
import com.example.skydex.data.remote.dto.FriendRequestBody
import com.example.skydex.data.remote.dto.FriendRequestResponse
import com.example.skydex.data.remote.dto.FriendResponse
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.ui.social.SocialGateway
import retrofit2.HttpException

class SocialRepository(private val api: SkyDexApi) : SocialGateway {

    override suspend fun sendRequest(email: String): Result<Unit> =
        resultOf { api.sendFriendRequest(FriendRequestBody(email.trim())) }.map { }

    override suspend fun incomingRequests(): Result<List<FriendRequestResponse>> =
        resultOf { api.incomingFriendRequests() }

    override suspend fun accept(requestId: String): Result<Unit> =
        resultOf { api.acceptFriendRequest(requestId) }.map { }

    /**
     * Mirrors `CaptureRepository.delete`, and for the same two reasons.
     *
     * `declineFriendRequest` returns the raw [retrofit2.Response] because Retrofit 2.9.0 cannot map
     * the backend's empty 204 onto a non-null `Unit`. That in turn means an unsuccessful status
     * arrives here as an ordinary value, so [resultOf] alone would report a 403 or a 404 as a
     * *successful* decline. Convert it to a failure explicitly.
     */
    override suspend fun decline(requestId: String): Result<Unit> = resultOf {
        val response = api.declineFriendRequest(requestId)
        if (!response.isSuccessful) throw HttpException(response)
    }

    override suspend fun friends(): Result<List<FriendResponse>> =
        resultOf { api.friends() }

    override suspend fun feed(page: Int, size: Int): Result<List<WeatherEventResponse>> =
        resultOf { api.feed(page, size) }
}

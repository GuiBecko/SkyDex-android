package com.example.skydex.ui.social

import com.example.skydex.data.remote.dto.FriendRequestResponse
import com.example.skydex.data.remote.dto.FriendResponse
import com.example.skydex.data.remote.dto.WeatherEventResponse

class FakeSocialGateway(
    var friends: List<FriendResponse> = emptyList(),
    var requests: List<FriendRequestResponse> = emptyList(),
    private val sendResult: Result<Unit> = Result.success(Unit),
    var feedResult: Result<List<WeatherEventResponse>> = Result.success(emptyList())
) : SocialGateway {

    val sentTo = mutableListOf<String>()
    val accepted = mutableListOf<String>()

    /** Every `(page, size)` pair the ViewModel asked for, in order. */
    val feedCalls = mutableListOf<Pair<Int, Int>>()

    override suspend fun sendRequest(email: String): Result<Unit> {
        if (sendResult.isSuccess) sentTo += email
        return sendResult
    }

    override suspend fun incomingRequests(): Result<List<FriendRequestResponse>> = Result.success(requests)

    override suspend fun accept(requestId: String): Result<Unit> {
        accepted += requestId
        return Result.success(Unit)
    }

    override suspend fun decline(requestId: String): Result<Unit> = Result.success(Unit)

    override suspend fun friends(): Result<List<FriendResponse>> = Result.success(friends)

    override suspend fun feed(page: Int, size: Int): Result<List<WeatherEventResponse>> {
        feedCalls += page to size
        return feedResult
    }
}

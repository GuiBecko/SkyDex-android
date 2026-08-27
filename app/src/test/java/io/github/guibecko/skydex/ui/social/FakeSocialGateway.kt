package io.github.guibecko.skydex.ui.social

import io.github.guibecko.skydex.data.remote.dto.FriendRequestResponse
import io.github.guibecko.skydex.data.remote.dto.FriendResponse
import io.github.guibecko.skydex.data.remote.dto.WeatherEventResponse

class FakeSocialGateway(
    var friends: List<FriendResponse> = emptyList(),
    var requests: List<FriendRequestResponse> = emptyList(),
    private val sendResult: Result<Unit> = Result.success(Unit),
    /**
     * Configurable like [sendResult], and it has to be. This was hardcoded to
     * `Result.success(Unit)`, which made every decline test unfailable — sitting exactly where a
     * real, permanent decline failure was living unnoticed (Retrofit could not map the backend's
     * empty 204 onto `Unit`, so every decline reported failure while succeeding).
     *
     * Covers unfriending too, since both go through [removeFriendship] — one endpoint, one row.
     */
    var declineResult: Result<Unit> = Result.success(Unit),
    var feedResult: Result<List<WeatherEventResponse>> = Result.success(emptyList()),
    var pendingCountResult: Result<Int> = Result.success(0)
) : SocialGateway {

    val sentTo = mutableListOf<String>()
    val accepted = mutableListOf<String>()
    /** Every id handed to [removeFriendship], declines and unfriends alike, in order. */
    val declined = mutableListOf<String>()

    var pendingCountCalls = 0
        private set

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

    override suspend fun removeFriendship(id: String): Result<Unit> {
        declined += id
        return declineResult
    }

    override suspend fun friends(): Result<List<FriendResponse>> = Result.success(friends)

    override suspend fun pendingRequestCount(): Result<Int> {
        pendingCountCalls++
        return pendingCountResult
    }

    override suspend fun feed(page: Int, size: Int): Result<List<WeatherEventResponse>> {
        feedCalls += page to size
        return feedResult
    }
}

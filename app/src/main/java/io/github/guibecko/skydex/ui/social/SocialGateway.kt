package io.github.guibecko.skydex.ui.social

import io.github.guibecko.skydex.data.remote.dto.FriendRequestResponse
import io.github.guibecko.skydex.data.remote.dto.FriendResponse
import io.github.guibecko.skydex.data.remote.dto.WeatherEventResponse

interface SocialGateway {
    suspend fun sendRequest(email: String): Result<Unit>
    suspend fun incomingRequests(): Result<List<FriendRequestResponse>>
    suspend fun accept(requestId: String): Result<Unit>

    /**
     * Deletes one `friendships` row: **both** declining a pending invite and removing an accepted
     * friend. Named for the row rather than for either verb because the backend has one endpoint
     * and one operation here — `decline` was the old name, and it hid the fact that the same call
     * unfriends. See `FriendsViewModel.decline` and `FriendsViewModel.unfriend`, which are the two
     * verbs, and `FriendResponse.friendshipId`, which is where the second one gets its id.
     */
    suspend fun removeFriendship(id: String): Result<Unit>

    suspend fun friends(): Result<List<FriendResponse>>

    /** The number behind the invite badge. */
    suspend fun pendingRequestCount(): Result<Int>
    suspend fun feed(page: Int, size: Int): Result<List<WeatherEventResponse>>
}

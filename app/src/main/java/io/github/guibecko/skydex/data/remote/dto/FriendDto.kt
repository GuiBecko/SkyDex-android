package io.github.guibecko.skydex.data.remote.dto

data class FriendRequestBody(val email: String)

data class FriendRequestResponse(
    val id: String,
    val requesterId: String,
    val requesterName: String,
    val requesterEmail: String,
    val createdAt: String
)

/** How many invites are waiting for the signed-in user to answer. Feeds the invite badge. */
data class PendingRequestCountResponse(val count: Int)

/**
 * [friendshipId] is the id of the relationship, **not** of the friend — it is what
 * `DELETE api/friends/requests/{id}` takes, so it is the only reason the app can remove a friend at
 * all. Do not pass [userId] to that route: it will 404, because no `friendships` row has that id.
 */
data class FriendResponse(
    val friendshipId: String,
    val userId: String,
    val name: String,
    val email: String,
    val friendsSince: String
)

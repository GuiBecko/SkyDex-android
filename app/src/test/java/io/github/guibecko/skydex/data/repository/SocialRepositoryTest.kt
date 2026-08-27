package io.github.guibecko.skydex.data.repository

import io.github.guibecko.skydex.data.remote.FakeSkyDexApi
import io.github.guibecko.skydex.data.remote.dto.PendingRequestCountResponse
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Covers the delete path's error mapping, which is the half of the 204 fix that the wire-level
 * test in `SkyDexApiTest` cannot see.
 *
 * Faked at the API boundary, so the real [SocialRepository] — including the `isSuccessful` check
 * that turns a refused decline into a failure — runs for real rather than being stubbed away.
 */
class SocialRepositoryTest {

    private val api = FakeSkyDexApi()
    private val repository = SocialRepository(api)

    /** The backend answers 204 with an empty body, which Retrofit hands over as a null payload. */
    @Test
    fun `removing a friendship succeeds on an empty 204`() = runBlocking {
        api.declineFriendRequestResponse = { Response.success<Unit>(null) }

        val result = repository.removeFriendship("r1")

        assertTrue(
            "a 204 is how the backend says the request was declined",
            result.isSuccess
        )
        assertEquals(listOf("r1"), api.declinedIds)
    }

    /**
     * `declineFriendRequest` returns the raw [Response], so an unsuccessful status arrives as a
     * perfectly ordinary value. Without the explicit check in the repository, declining a request
     * that is not yours — or one that no longer exists — would be reported to the user as success,
     * and `FriendsViewModel` would refresh a list that still contains it.
     */
    @Test
    fun `removing a friendship fails when the backend refuses`() = runBlocking {
        api.declineFriendRequestResponse = { Response.error(403, "".toResponseBody(null)) }

        val result = repository.removeFriendship("not-mine")

        assertTrue("a 403 must not read as a successful decline", result.isFailure)
        assertEquals(403, (result.exceptionOrNull() as HttpException).code())
    }

    @Test
    fun `the pending count is unwrapped to a plain number`() = runBlocking {
        api.pendingFriendRequestCountResponse = { PendingRequestCountResponse(count = 4) }

        // The badge wants an Int, not a wrapper — the object exists on the wire so the endpoint can
        // gain fields without breaking the parser, and it stops at this layer.
        assertEquals(4, repository.pendingRequestCount().getOrNull())
    }

    @Test
    fun `a failed count request is a failure, not a zero`() = runBlocking {
        api.pendingFriendRequestCountResponse = { throw IOException("offline") }

        // Deliberately not `Result.success(0)`: `PendingInvitesStore` keeps its last known count on
        // failure, and it can only do that if the failure reaches it as one. Collapsing this to zero
        // here would blink the badge off on every dropped request.
        assertTrue(repository.pendingRequestCount().isFailure)
    }
}

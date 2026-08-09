package com.example.skydex.data.repository

import com.example.skydex.data.remote.FakeSkyDexApi
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Covers the decline path's error mapping, which is the half of the 204 fix that the wire-level
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
    fun `decline succeeds on an empty 204`() = runBlocking {
        api.declineFriendRequestResponse = { Response.success<Unit>(null) }

        val result = repository.decline("r1")

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
    fun `decline fails when the backend refuses`() = runBlocking {
        api.declineFriendRequestResponse = { Response.error(403, "".toResponseBody(null)) }

        val result = repository.decline("not-mine")

        assertTrue("a 403 must not read as a successful decline", result.isFailure)
        assertEquals(403, (result.exceptionOrNull() as HttpException).code())
    }
}

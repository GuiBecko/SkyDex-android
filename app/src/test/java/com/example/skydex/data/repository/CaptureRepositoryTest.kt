package com.example.skydex.data.repository

import com.example.skydex.data.remote.FakeSkyDexApi
import com.example.skydex.data.remote.dto.CreateWeatherEventRequest
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
import com.example.skydex.data.remote.dto.WeatherEventResponse
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class CaptureRepositoryTest {

    private val api = FakeSkyDexApi()
    private val repository = CaptureRepository(api)

    private val capture = WeatherEventResponse(
        id = "1",
        title = "Cumulonimbus",
        description = "Uma torre de nuvens",
        photoUrl = "https://example.test/cb.jpg",
        capturedAt = "2026-08-07T10:00:00Z",
        latitude = -23.55,
        longitude = -46.63,
        userId = "u1",
        authorName = "Pilot"
    )

    @Test
    fun `myCaptures forwards the api result`() = runBlocking {
        api.myCapturesResponse = { listOf(capture) }

        assertEquals(listOf(capture), repository.myCaptures().getOrNull())
    }

    @Test
    fun `a network error becomes a failed Result instead of an exception`() = runBlocking {
        api.myCapturesResponse = { throw IOException("offline") }

        val result = repository.myCaptures()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `nearby passes the coordinates through`() = runBlocking {
        val phenomenon = NearbyPhenomenonResponse("Tempestade", "2026-08-07T10:00", 21.5, "Perigo")
        api.nearbyResponse = { listOf(phenomenon) }

        val result = repository.nearby(-23.55, -46.63)

        assertEquals(listOf(phenomenon), result.getOrNull())
        assertEquals(listOf(-23.55 to -46.63), api.nearbyCalls)
    }

    @Test
    fun `create forwards the request body`() = runBlocking {
        // TODO(task-10): replace with the device's real position
        val request = CreateWeatherEventRequest("Cumulonimbus", "Uma torre de nuvens", "https://example.test/cb.jpg", 0.0, 0.0)
        api.createResponse = { capture }

        assertEquals(capture, repository.create(request).getOrNull())
        // The point of the test: the body reaching the api is the one the caller handed over,
        // field for field. Asserting only the return value would pass with the body dropped.
        assertEquals(listOf(request), api.createdRequests)
    }

    /** The backend answers 204 with an empty body, which Retrofit hands over as a null payload. */
    @Test
    fun `delete succeeds on an empty 204`() = runBlocking {
        api.deleteResponse = { Response.success<Unit>(null) }

        val result = repository.delete("1")

        assertTrue(result.isSuccess)
        assertEquals(listOf("1"), api.deletedIds)
    }

    /**
     * `deleteCapture` returns the raw [Response], so an unsuccessful status arrives as a perfectly
     * ordinary value. Without the explicit check in the repository, deleting somebody else's
     * capture would be reported to the user as a success.
     */
    @Test
    fun `delete fails when the backend refuses`() = runBlocking {
        api.deleteResponse = { Response.error(403, "".toResponseBody(null)) }

        val result = repository.delete("someone-elses-capture")

        assertTrue("a 403 must not read as a successful delete", result.isFailure)
        assertEquals(403, (result.exceptionOrNull() as HttpException).code())
    }
}

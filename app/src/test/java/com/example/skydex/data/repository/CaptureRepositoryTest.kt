package com.example.skydex.data.repository

import com.example.skydex.data.remote.FakeSkyDexApi
import com.example.skydex.data.remote.dto.CreateWeatherEventRequest
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
import com.example.skydex.data.remote.dto.PhotoUploadResponse
import com.example.skydex.data.remote.dto.WeatherEventResponse
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class CaptureRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
        // 0.0, 0.0 is a perfectly valid opaque fixture here: this unit test has no device to
        // supply a real position, it only checks that the repository forwards whatever it is given.
        val request = CreateWeatherEventRequest(
            title = "Cumulonimbus",
            description = "Uma torre de nuvens",
            photoUrl = "https://example.test/cb.jpg",
            latitude = 0.0,
            longitude = 0.0
        )
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

    /**
     * The backend binds the upload with `@RequestParam("file")` and picks the stored extension
     * from the part's content type, so both are part of the contract rather than cosmetic: a
     * differently named part is a 400 and a missing content type is rejected as a non-image.
     * Asserting only the returned URL would pass with either of them wrong.
     */
    @Test
    fun `uploadPhoto sends the file as a jpeg part named file and returns the stored url`() = runBlocking {
        val file = temporaryFolder.newFile("storm.jpg")
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        api.uploadPhotoResponse = { PhotoUploadResponse("/api/photos/abc.jpg") }

        val result = repository.uploadPhoto(file)

        assertEquals("/api/photos/abc.jpg", result.getOrNull())
        val part = api.uploadedParts.single()
        assertEquals(
            "form-data; name=\"file\"; filename=\"storm.jpg\"",
            part.headers?.get("Content-Disposition")
        )
        assertEquals("image/jpeg", part.body.contentType().toString())
        assertEquals(4L, part.body.contentLength())
    }
}

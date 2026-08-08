package com.example.skydex.ui.capture

import com.example.skydex.data.remote.dto.CreateWeatherEventRequest
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.util.Coordinates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun jpeg(): File = tempFolder.newFile("photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test
    fun `a complete capture uploads the photo then creates the event`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177) }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("Raios sobre o bairro")
        viewModel.onPhotoTaken(jpeg())
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.saved)
        assertNull(viewModel.state.value.errorMessage)
        assertEquals(1, gateway.uploadedFiles.size)

        val sent = gateway.createdRequests.single()
        assertEquals("Tempestade", sent.title)
        // Relative, and it must be exactly what uploadPhoto returned. Task 7 constrains this
        // field server-side to `^/api/photos/[A-Za-z0-9._-]+\\.(jpg|png)$`, so a screen that
        // invented or rewrote the URL would be rejected at the API rather than here.
        assertEquals("/api/photos/uploaded.jpg", sent.photoUrl)
        assertEquals(-30.0346, sent.latitude, 0.00001)
        // No capturedAt assertion: the request carries no such field. The server stamps the
        // capture time (Task 6), which is what stops a client backdating to yesterday's storm.
    }

    @Test
    fun `refuses to submit without a photo`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177) }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("Raios")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Tire uma foto do fenômeno antes de salvar.", viewModel.state.value.errorMessage)
        assertEquals(0, gateway.createdRequests.size)
    }

    @Test
    fun `refuses to submit without a position`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val viewModel = CaptureViewModel(gateway) { null }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("Raios")
        viewModel.onPhotoTaken(jpeg())
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(
            "Não foi possível obter sua localização. Ative o GPS e tente de novo.",
            viewModel.state.value.errorMessage
        )
        assertEquals(0, gateway.createdRequests.size)
    }

    @Test
    fun `does not create the event when the upload fails`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(uploadResult = Result.failure(IOException("no network")))
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177) }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("Raios")
        viewModel.onPhotoTaken(jpeg())
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, gateway.createdRequests.size)
        assertNotNull(viewModel.state.value.errorMessage)
        assertEquals(false, viewModel.state.value.saved)
    }
}

class FakeCaptureGateway(
    // A relative path, because that is what the real endpoint returns. A fake that hands back
    // an absolute CDN URL would let the ViewModel pass a test it fails against the server,
    // which rejects anything outside `^/api/photos/...` (Task 7).
    private val uploadResult: Result<String> = Result.success("/api/photos/uploaded.jpg")
) : CaptureGateway {

    val uploadedFiles = mutableListOf<File>()
    val createdRequests = mutableListOf<CreateWeatherEventRequest>()

    override suspend fun uploadPhoto(file: File): Result<String> {
        uploadedFiles += file
        return uploadResult
    }

    override suspend fun create(request: CreateWeatherEventRequest): Result<WeatherEventResponse> {
        createdRequests += request
        return Result.success(
            WeatherEventResponse(
                id = "00000000-0000-0000-0000-000000000001",
                title = request.title,
                description = request.description,
                photoUrl = request.photoUrl,
                capturedAt = "2026-08-07T17:42:10Z",   // server-stamped; the fake just picks one
                latitude = request.latitude,
                longitude = request.longitude,
                userId = "00000000-0000-0000-0000-000000000002",
                authorName = "Test Pilot"
            )
        )
    }
}

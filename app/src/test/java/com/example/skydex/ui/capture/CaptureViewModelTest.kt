package com.example.skydex.ui.capture

import com.example.skydex.data.remote.dto.CreateWeatherEventRequest
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.util.Coordinates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun jpeg(name: String = "photo.jpg"): File =
        tempFolder.newFile(name).apply { writeBytes(byteArrayOf(1, 2, 3)) }

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
        assertEquals("Raios sobre o bairro", sent.description)
        // Relative, and it must be exactly what uploadPhoto returned. Task 7 constrains this
        // field server-side to `^/api/photos/[A-Za-z0-9._-]+\\.(jpg|png)$`, so a screen that
        // invented or rewrote the URL would be rejected at the API rather than here.
        assertEquals("/api/photos/uploaded.jpg", sent.photoUrl)
        // Both coordinates, and deliberately far apart in magnitude and both negative-but-distinct
        // (-30 vs -51): a latitude/longitude transposition has to change the number, so it cannot
        // slip through on a fixture where the two happen to coincide. Getting this wrong pins every
        // capture to the wrong meridian, and Phase 3's Open-Meteo validation would then score the
        // event against a place the user was never standing in — silently, with no error anywhere.
        assertEquals(-30.0346, sent.latitude, 0.00001)
        assertEquals(-51.2177, sent.longitude, 0.00001)
        // No capturedAt assertion: the request carries no such field. The server stamps the
        // capture time (Task 6), which is what stops a client backdating to yesterday's storm.
    }

    @Test
    fun `refuses to submit without a title`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177) }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("   ")
        viewModel.onDescriptionChanged("Raios")
        viewModel.onPhotoTaken(jpeg())
        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Preencha o título e a descrição.", viewModel.state.value.errorMessage)
        assertEquals(0, gateway.uploadedFiles.size)
        assertEquals(0, gateway.createdRequests.size)
    }

    /**
     * A separate test from the one above on purpose: the guard is `title.isBlank() ||
     * description.isBlank()`, two independent operands, and a test that only ever leaves the title
     * empty keeps passing after the description half is deleted.
     */
    @Test
    fun `refuses to submit without a description`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177) }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("   ")
        viewModel.onPhotoTaken(jpeg())
        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Preencha o título e a descrição.", viewModel.state.value.errorMessage)
        assertEquals(0, gateway.uploadedFiles.size)
        assertEquals(0, gateway.createdRequests.size)
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

    /**
     * The Button's `enabled = !state.submitting` is not a guard, it is a hint: it only takes effect
     * at the next recomposition, so two taps inside one frame are both dispatched against a Button
     * that is still enabled. Without a check inside `submit()` that is two uploads, two rows in the
     * feed and two JPEGs on the server for one storm.
     */
    @Test
    fun `two taps in one frame produce exactly one capture`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177) }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("Raios")
        viewModel.onPhotoTaken(jpeg())

        // No advanceUntilIdle between them: this is the double tap, not two deliberate saves.
        viewModel.submit()
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, gateway.uploadedFiles.size)
        assertEquals(1, gateway.createdRequests.size)
        assertTrue(viewModel.state.value.saved)
    }

    /**
     * The `saved` half of the same guard, tested separately from the `submitting` half because they
     * are independent operands. Navigation away from the screen is driven by a `LaunchedEffect` on
     * `state.saved`, so there is a real window in which the capture is stored and the Button is
     * enabled again — `submitting` is already false by then and would not stop a tap.
     */
    @Test
    fun `submitting again after a successful save does nothing`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177) }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("Raios")
        viewModel.onPhotoTaken(jpeg())
        viewModel.submit()
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, gateway.uploadedFiles.size)
        assertEquals(1, gateway.createdRequests.size)
    }

    /**
     * Upload succeeds, create fails: the JPEG is on the server referenced by nothing. That first
     * orphan is not fixable from here (there is no delete-photo endpoint), but the *compounding*
     * is: a retry that re-uploads turns one user tapping Save three times into three orphans.
     */
    @Test
    fun `a retry after a failed create reuses the photo already uploaded`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(createFailure = IOException("create rejected"))
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177) }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("Raios")
        viewModel.onPhotoTaken(jpeg())

        viewModel.submit()
        advanceUntilIdle()
        assertEquals("Falha ao salvar o registro. Tente de novo.", viewModel.state.value.errorMessage)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, gateway.uploadedFiles.size)
        assertEquals(2, gateway.createdRequests.size)
        // And the reused path is still the one the server handed back, not a locally invented one.
        assertEquals("/api/photos/uploaded.jpg", gateway.createdRequests.last().photoUrl)
    }

    /** The retry succeeds on the second attempt without a second upload. */
    @Test
    fun `a successful retry saves the capture with the cached photo`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(createFailure = IOException("create rejected"))
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177) }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("Raios")
        viewModel.onPhotoTaken(jpeg())
        viewModel.submit()
        advanceUntilIdle()

        gateway.createFailure = null
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.saved)
        assertNull(viewModel.state.value.errorMessage)
        assertEquals(1, gateway.uploadedFiles.size)
    }

    /**
     * The other edge of the cache: reusing the uploaded path is only correct while the photo has
     * not changed. If the user retakes the shot after a failed create, the cached path points at
     * the picture they just replaced — caching it blindly would file the capture under the wrong
     * image, which is worse than the orphan the cache exists to prevent.
     */
    @Test
    fun `retaking the photo after a failed create uploads the new one`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(createFailure = IOException("create rejected"))
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177) }
        val first = jpeg("first.jpg")
        val second = jpeg("second.jpg")

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("Raios")
        viewModel.onPhotoTaken(first)
        viewModel.submit()
        advanceUntilIdle()

        viewModel.onPhotoTaken(second)
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(listOf(first, second), gateway.uploadedFiles)
    }

    /**
     * The retake that interleaves with the upload rather than following it. `FakeCaptureGateway`
     * structurally cannot express this — its `uploadPhoto` returns without ever suspending, so
     * nothing can happen "during" it — hence [SuspendingUploadGateway].
     *
     * The upload was started for `first.jpg`. By the time it answers, the user has taken
     * `second.jpg`, and the state that `onPhotoTaken` cleared is about to be written back by a
     * coroutine that only knows about the old file. Caching that path would file the next attempt
     * under the picture the user just discarded — silently, because the preview shows the new one.
     */
    @Test
    fun `a retake while the upload is in flight does not cache the discarded photo`() = runTest(dispatcher) {
        val gateway = SuspendingUploadGateway(FakeCaptureGateway(createFailure = IOException("create rejected")))
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177) }
        val first = jpeg("first.jpg")
        val second = jpeg("second.jpg")

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("Raios")
        viewModel.onPhotoTaken(first)
        viewModel.submit()
        advanceUntilIdle() // the upload is now parked inside the gateway

        viewModel.onPhotoTaken(second)
        gateway.firstUpload.complete(Unit)
        advanceUntilIdle()

        assertEquals(second, viewModel.state.value.photoFile)
        assertNull(viewModel.state.value.uploadedPhotoUrl)
    }

    /**
     * The same interleaving with the `create` succeeding, which is the worse outcome: nothing fails,
     * the screen navigates away, and the capture is filed under a photo the user replaced and can
     * no longer see. The submit that was already in flight must not save at all — its snapshot is
     * stale — and the next tap must save the photo actually on screen.
     */
    @Test
    fun `a capture is never saved with a photo the user replaced mid-upload`() = runTest(dispatcher) {
        val gateway = SuspendingUploadGateway()
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177) }
        val first = jpeg("first.jpg")
        val second = jpeg("second.jpg")

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("Raios")
        viewModel.onPhotoTaken(first)
        viewModel.submit()
        advanceUntilIdle()

        viewModel.onPhotoTaken(second)
        gateway.firstUpload.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.saved)
        assertEquals(0, gateway.createdRequests.size)
        assertEquals(
            "A foto foi trocada durante o envio. Toque em Salvar de novo.",
            viewModel.state.value.errorMessage
        )

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.saved)
        assertEquals(listOf(first, second), gateway.uploadedFiles)
        assertEquals("/api/photos/second.jpg", gateway.createdRequests.single().photoUrl)
    }

    /**
     * Same shape as HomeScreen's: `CaptureScreen`'s `LaunchedEffect(Unit)` re-runs on every Activity
     * recreation, so without this latch a rotation re-launches the permission request and re-runs a
     * GPS fix on a ViewModel that already has a position.
     */
    @Test
    fun `the initial location request is claimed once per view model`() = runTest(dispatcher) {
        val viewModel = CaptureViewModel(FakeCaptureGateway()) { Coordinates(-30.0346, -51.2177) }

        assertTrue(viewModel.shouldRequestInitialLocation())
        assertFalse(viewModel.shouldRequestInitialLocation())
    }

    /** The latch must not disable the "Tentar novamente" button. */
    @Test
    fun `refreshLocation still works after the initial request was claimed`() = runTest(dispatcher) {
        var fixes = 0
        val viewModel = CaptureViewModel(FakeCaptureGateway()) {
            fixes++
            Coordinates(-30.0346, -51.2177)
        }

        viewModel.shouldRequestInitialLocation()
        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.refreshLocation()
        advanceUntilIdle()

        assertEquals(2, fixes)
    }
}

class FakeCaptureGateway(
    // A relative path, because that is what the real endpoint returns. A fake that hands back
    // an absolute CDN URL would let the ViewModel pass a test it fails against the server,
    // which rejects anything outside `^/api/photos/...` (Task 7).
    private val uploadResult: Result<String> = Result.success("/api/photos/uploaded.jpg"),
    // Set to open the window that orphans a JPEG: the upload lands, the create does not, and the
    // photo on the server is now referenced by nothing. `var` so a test can also close the window
    // again and watch the retry succeed.
    var createFailure: Throwable? = null
) : CaptureGateway {

    val uploadedFiles = mutableListOf<File>()
    val createdRequests = mutableListOf<CreateWeatherEventRequest>()

    override suspend fun uploadPhoto(file: File): Result<String> {
        uploadedFiles += file
        return uploadResult
    }

    override suspend fun create(request: CreateWeatherEventRequest): Result<WeatherEventResponse> {
        createdRequests += request
        createFailure?.let { return Result.failure(it) }
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

/**
 * A gateway whose upload really suspends, so a test can act *while* it is in flight.
 *
 * [FakeCaptureGateway] cannot: its `uploadPhoto` returns without ever hitting a suspension point,
 * so from the ViewModel's side the upload is instantaneous and no retake can interleave with it —
 * an entire class of races is invisible to a suite built only on it. Here the first upload parks on
 * [firstUpload] until the test releases it; later uploads (the retry path) return immediately, so a
 * test can drive the whole sequence with a single gate.
 *
 * The returned path names the file (`/api/photos/second.jpg`) rather than being a constant, because
 * these tests are about *which* photo a capture was filed under — a fixed URL would make the wrong
 * photo and the right one indistinguishable in the assertion.
 */
private class SuspendingUploadGateway(
    private val creates: FakeCaptureGateway = FakeCaptureGateway()
) : CaptureGateway {

    val firstUpload = CompletableDeferred<Unit>()
    val uploadedFiles = mutableListOf<File>()
    val createdRequests: List<CreateWeatherEventRequest> get() = creates.createdRequests

    override suspend fun uploadPhoto(file: File): Result<String> {
        uploadedFiles += file
        if (uploadedFiles.size == 1) firstUpload.await()
        return Result.success("/api/photos/${file.name}")
    }

    override suspend fun create(request: CreateWeatherEventRequest): Result<WeatherEventResponse> =
        creates.create(request)
}

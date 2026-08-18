package com.example.skydex.ui.capture

import androidx.lifecycle.ViewModelStore
import com.example.skydex.data.remote.dto.BadgeResponse
import com.example.skydex.data.remote.dto.CreateWeatherEventRequest
import com.example.skydex.data.remote.dto.ProfileResponse
import com.example.skydex.data.remote.dto.UserSummary
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.ui.common.LogWarning
import com.example.skydex.ui.common.RecordingLogWarning
import com.example.skydex.ui.common.noLogging
import com.example.skydex.util.Coordinates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
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
import retrofit2.HttpException
import retrofit2.Response
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

    // -----------------------------------------------------------------------------------------
    // The eager upload (Task 8): fired the instant the shutter closes, not at Save time.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `uploads the photo as soon as it is taken`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0, -51.0) }

        viewModel.onPhotoTaken(jpeg())
        advanceUntilIdle()

        // Before submit is ever called. The user spends the next several seconds typing, and the
        // model's forward pass happens inside that time instead of after it.
        assertEquals(1, gateway.uploadedFiles.size)
        assertNotNull(viewModel.state.value.uploadedPhotoUrl)
    }

    @Test
    fun `submit reuses the eager upload instead of sending a second copy`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0, -51.0) }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onPhotoTaken(jpeg())
        advanceUntilIdle()
        viewModel.onTitleChanged("t")
        viewModel.onDescriptionChanged("d")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, gateway.uploadedFiles.size)
        assertTrue(viewModel.state.value.saved)
    }

    @Test
    fun `submit waits for an upload still in flight`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val gate = CompletableDeferred<Unit>()
        gateway.uploadGate = gate
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0, -51.0) }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onPhotoTaken(jpeg())
        viewModel.onTitleChanged("t")
        viewModel.onDescriptionChanged("d")
        viewModel.submit()
        advanceUntilIdle()

        // Still waiting on the upload, so nothing has been created and the button is still busy.
        assertTrue(viewModel.state.value.submitting)
        assertTrue(gateway.createdRequests.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.saved)
        assertEquals(1, gateway.uploadedFiles.size)
    }

    @Test
    fun `an eager upload that fails surfaces its message without blocking a retake`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        gateway.uploadResult = Result.failure(
            HttpException(Response.error<Any>(422, """{"error":"This photo does not look like the sky"}"""
                .toResponseBody("application/json".toMediaType())))
        )
        // A real android.util.Log.w is not mocked in this unit test, and the eager upload's
        // failure path logs the throwable it caught — same reason every other test in this file
        // that can reach a `logWarning` call supplies a fake one.
        val viewModel = CaptureViewModel(gateway, logWarning = noLogging) { Coordinates(-30.0, -51.0) }

        viewModel.onPhotoTaken(jpeg())
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.errorMessage)
        assertNull(viewModel.state.value.uploadedPhotoUrl)
        // Not submitting, not saved — the user can simply take another photo.
        assertFalse(viewModel.state.value.submitting)
    }

    @Test
    fun `a retake abandons the previous upload`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val gate = CompletableDeferred<Unit>()
        gateway.uploadGate = gate
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0, -51.0) }

        val first = jpeg("first.jpg")
        viewModel.onPhotoTaken(first)
        val second = jpeg("second.jpg")
        viewModel.onPhotoTaken(second)
        gate.complete(Unit)
        advanceUntilIdle()

        // The path from the abandoned upload must never be cached against the photo on screen:
        // saving it would file the capture under an image the user cannot see any more.
        assertEquals(second, viewModel.state.value.photoFile)
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

        assertEquals("Falta o título e a descrição", viewModel.state.value.errorMessage?.title)
        // Task 8: the eager upload fires from onPhotoTaken alone, before submit's own validation
        // ever runs, so it happens here regardless of the missing title. What submit's guard
        // still owns — and what this test is really about — is that no event is ever created.
        assertEquals(1, gateway.uploadedFiles.size)
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

        assertEquals("Falta o título e a descrição", viewModel.state.value.errorMessage?.title)
        // Task 8: same reasoning as the title test above — the eager upload does not care about
        // form validity, only submit's guard does.
        assertEquals(1, gateway.uploadedFiles.size)
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

        assertEquals("Falta a foto", viewModel.state.value.errorMessage?.title)
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

        assertEquals("Não achamos onde você está", viewModel.state.value.errorMessage?.title)
        assertEquals(0, gateway.createdRequests.size)
    }

    @Test
    fun `does not create the event when the upload fails`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(uploadResult = Result.failure(IOException("no network")))
        // Task 8: onPhotoTaken's eager upload fails here too, and its failure path logs the
        // throwable — a real android.util.Log.w is not mocked in this unit test, so this needs
        // the same fake logger every other test that can reach a `logWarning` call already uses.
        val viewModel = CaptureViewModel(gateway, logWarning = noLogging) { Coordinates(-30.0346, -51.2177) }

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
        assertEquals("Sem conexão", viewModel.state.value.errorMessage?.title)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, gateway.uploadedFiles.size)
        assertEquals(2, gateway.createdRequests.size)
        // And the reused path is still the one the server handed back, not a locally invented one.
        assertEquals("/api/photos/uploaded.jpg", gateway.createdRequests.last().photoUrl)
    }

    /**
     * The backend designed five distinct, actionable 400 messages across Tasks 12b and 12c —
     * "This photo has already been used for a capture", "Photo has expired; take a new one", and
     * so on — and each one implies a different next step, so one blanket string was wrong for all
     * of them: it told the user to retry, which for most of the five cannot work.
     *
     * The client used to fix that by forwarding the backend's own sentence — which put English in
     * a pt-BR app (audit finding B1). It keeps the distinction and answers in our words.
     */
    @Test
    fun `a 400 becomes our own pt-BR message, never the backend's English`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(
            createFailure = httpError(400, """{"error":"Photo has expired; take a new one"}""")
        )
        val viewModel = readyToSubmit(gateway)

        viewModel.submit()
        advanceUntilIdle()

        val message = viewModel.state.value.errorMessage!!
        assertEquals("Essa foto expirou", message.title)
        assertEquals("Tire uma nova foto para registrar.", message.body)
        assertFalse(
            "the backend's English must never reach the screen",
            "${message.title} ${message.body}".contains("Photo has expired")
        )
    }

    /**
     * The leak the audit named explicitly: the server appends the enum to the message, so
     * forwarding it put `THUNDERSTORM` — an internal domain constant — in front of the user.
     */
    @Test
    fun `an unknown phenomenon never leaks the enum name`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(
            createFailure = httpError(400, """{"error":"Unknown phenomenon: THUNDERSTORM"}""")
        )
        val viewModel = readyToSubmit(gateway)

        viewModel.submit()
        advanceUntilIdle()

        val message = viewModel.state.value.errorMessage!!
        assertEquals("Não reconhecemos esse fenômeno", message.title)
        assertFalse(
            "the domain enum must never reach the screen",
            "${message.title} ${message.body}".contains("THUNDERSTORM")
        )
    }

    /**
     * The trap three locally-correct decisions composed into.
     *
     * Task 10 built this retry cache so a retry reuses the JPEG already uploaded. Task 12b then
     * made photos single-use — `consume` stamps `consumed_at` in the same transaction as the
     * insert. Nobody revisited the cache. So whenever the server commits but the client sees a
     * failure, the cached path names a spent photo and every retry is refused with
     * "This photo has already been used for a capture", forever. The same applies once the photo
     * passes the 30-minute MAX_AGE.
     *
     * A 400 is the signal that this photo will never be accepted again, so the cache must be
     * dropped and the next attempt must upload afresh. That costs one orphaned JPEG — the
     * server-side sweep's problem — instead of a capture the user can never complete.
     */
    @Test
    fun `a 400 clears the cached photo so the next attempt uploads a new one`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(
            createFailure = httpError(400, """{"error":"This photo has already been used for a capture"}""")
        )
        val viewModel = readyToSubmit(gateway)

        viewModel.submit()
        advanceUntilIdle()
        assertNull(
            "a photo the server has rejected must not stay cached",
            viewModel.state.value.uploadedPhotoUrl
        )

        gateway.createFailure = null
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(2, gateway.uploadedFiles.size)
        assertTrue(viewModel.state.value.saved)
    }

    /**
     * The error body is attacker-adjacent input as far as this code is concerned: it can be absent,
     * truncated, HTML from a proxy, or JSON of the wrong shape. None of those may crash the app,
     * and none may leave the user with no message at all.
     */
    @Test
    fun `a 400 whose body is not the error envelope falls back to the generic message`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(
            createFailure = httpError(400, "<html><body>502 Bad Gateway</body></html>")
        )
        val viewModel = readyToSubmit(gateway)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(
            "Não deu para salvar esse registro",
            viewModel.state.value.errorMessage?.title
        )
    }

    /** An empty error body is the other shape of the same hazard. */
    @Test
    fun `a 400 with an empty body falls back to the generic message`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(createFailure = httpError(400, ""))
        val viewModel = readyToSubmit(gateway)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(
            "Não deu para salvar esse registro",
            viewModel.state.value.errorMessage?.title
        )
    }

    /**
     * The other side of the cache rule. A dropped connection says nothing about whether the photo
     * is still usable — most likely the request never arrived — so the cache must survive, exactly
     * as Task 10 intended. Only an explicit 400 clears it.
     */
    @Test
    fun `a network failure keeps the cached photo and the generic message`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(createFailure = IOException("offline"))
        val viewModel = readyToSubmit(gateway)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Sem conexão", viewModel.state.value.errorMessage?.title)
        assertEquals("/api/photos/uploaded.jpg", viewModel.state.value.uploadedPhotoUrl)
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
        // Task 8: `onPhotoTaken(second)` starts its own eager upload, and `SuspendingUploadGateway`
        // only parks the *first* call it ever sees — so this second upload resolves immediately,
        // within the same advanceUntilIdle, to its own (correct) path. That is not the stale
        // "first.jpg" result leaking through; it is what the second, legitimate eager upload
        // actually earned. What must never happen — and what the rest of this test's assertions
        // guard — is `first.jpg`'s path landing here or a create firing for it.
        assertEquals("/api/photos/second.jpg", viewModel.state.value.uploadedPhotoUrl)
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
            "A foto mudou durante o envio",
            viewModel.state.value.errorMessage?.title
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

    @Test
    fun `the create request carries no phenomenon`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177) }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("Raios sobre a cidade")
        viewModel.onPhotoTaken(jpeg())
        advanceUntilIdle()
        viewModel.submit()
        advanceUntilIdle()

        // Nothing in the request names a species. The server reads the weather itself, and a
        // field the client fills in is a field a modified client can lie in.
        val sent = gateway.createdRequests.single()
        assertEquals("Tempestade", sent.title)
        assertEquals(-30.0346, sent.latitude, 0.0)
    }

    @Test
    fun `submits without the user ever choosing a species`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177) }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Céu")
        viewModel.onDescriptionChanged("Sem nuvem nenhuma")
        viewModel.onPhotoTaken(jpeg())
        advanceUntilIdle()
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.saved)
        assertNull(viewModel.state.value.errorMessage)
    }

    /**
     * The load-bearing test for `locationIsMock`: the server defaults that field to `false`, so a
     * client that hardcodes `false` at the call site compiles and passes every other test here
     * while silently disabling the anti-cheat check for every real user. Only a fixture whose
     * position is actually flagged as mocked, asserted against the request that reaches the
     * gateway, can catch that — see task-14-report.md for the mutation probe that confirms it does.
     */
    @Test
    fun `a mocked position is flagged on the created request`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val viewModel = CaptureViewModel(gateway) { Coordinates(-30.0346, -51.2177, isMock = true) }

        viewModel.refreshLocation()
        advanceUntilIdle()
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("Raios")
        viewModel.onPhotoTaken(jpeg())
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(gateway.createdRequests.single().locationIsMock)
    }

    // -----------------------------------------------------------------------------------------
    // The reward moment (audit finding B6)
    // -----------------------------------------------------------------------------------------

    /**
     * The whole point of B6: the capture response used to be discarded and the screen navigated
     * away on `saved` alone, so the XP only ever surfaced later as a number in a list.
     *
     * Every field asserted here comes off `WeatherEventResponse` and nothing else — no second
     * request has happened yet at this point, which is what makes the celebration immediate.
     */
    @Test
    fun `a confirmed capture produces a reward straight from the capture response`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(rarity = "LEGENDARY")
        val viewModel = readyToSubmit(gateway)

        viewModel.submit()
        advanceUntilIdle()

        val reward = viewModel.state.value.reward!!
        assertTrue(reward.confirmed)
        assertEquals(400, reward.xpAwarded)
        assertEquals("LEGENDARY", reward.rarity)
        assertEquals("Tempestade com Trovões", reward.phenomenonName)
        // No profile reader was wired, so there is nothing to enrich it with — and the reward is
        // complete and showable regardless.
        assertNull(reward.bonus)
        // `saved` keeps its old meaning; it is the re-submit guard, not the overlay's trigger.
        assertTrue(viewModel.state.value.saved)
    }

    /**
     * The branch that must NOT celebrate.
     *
     * The backend cross-checks the photo's phenomenon against the region's real weather, and an
     * UNCONFIRMED verdict awards zero — but it is not an accusation either (an Open-Meteo outage
     * lands here too), and the row and the photo are kept. So the reward still exists, still offers
     * both ways forward, and simply carries no XP for the overlay to count up to.
     */
    @Test
    fun `an unconfirmed capture yields a reward with no XP`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(confirmed = false)
        val viewModel = readyToSubmit(gateway)

        viewModel.submit()
        advanceUntilIdle()

        val reward = viewModel.state.value.reward!!
        assertFalse(reward.confirmed)
        assertEquals(0, reward.xpAwarded)
        // Still a saved capture: the user has a row and a photo, and the flow must let them out.
        assertTrue(viewModel.state.value.saved)
        assertNull(viewModel.state.value.errorMessage)
    }

    // -----------------------------------------------------------------------------------------
    // The silent discard of an unconfirmed capture
    // -----------------------------------------------------------------------------------------

    /**
     * An unconfirmed capture must not stay on the server.
     *
     * The backend stores every capture and only *then* reports its verdict, so an UNCONFIRMED one
     * exists as a row worth no XP, counting towards no species, that the backend will never
     * re-validate. Leaving it would put a permanent, unexplainable entry in Meus Registros next to
     * the captures the user actually earned, so the client takes it back immediately.
     *
     * Asserted on the id off the create response, not on a count alone: deleting *something* is not
     * the requirement, deleting the capture that was just refused is.
     */
    @Test
    fun `an unconfirmed capture is deleted immediately`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(confirmed = false)
        val viewModel = readyToSubmit(gateway)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(
            listOf("00000000-0000-0000-0000-000000000001"),
            gateway.deletedIds
        )
    }

    /**
     * The other half of the rule, and the one that would be catastrophic to get wrong: a CONFIRMED
     * capture is the thing the whole app exists to produce. A discard that fired on the wrong branch
     * would delete the user's collection one entry at a time, silently, with a celebration on screen
     * while it happened.
     */
    @Test
    fun `a confirmed capture is never deleted`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway(confirmed = true)
        val viewModel = readyToSubmit(gateway)

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(
            "a confirmed capture must survive",
            gateway.deletedIds.isEmpty()
        )
    }

    /**
     * Silent means silent. The discard is a request the user never made, about a record they were
     * never shown, and there is nothing they could do about either outcome — so neither its success
     * nor its failure may reach the screen.
     *
     * The failure path is the strict one: the DELETE is refused, the record survives on the server
     * (the accepted degradation), and the reward the user is looking at must be untouched — no
     * error notice, no lost XP line, no state change at all. The cause goes to `logWarning` and
     * nowhere else.
     */
    @Test
    fun `a failed discard changes nothing the user can see`() = runTest(dispatcher) {
        val logWarning = RecordingLogWarning()
        val gateway = FakeCaptureGateway(confirmed = false).apply {
            deleteFailure = IOException("delete rejected")
        }
        val viewModel = readyToSubmit(gateway, logWarning = logWarning)

        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull("a failed discard must not surface an error", state.errorMessage)
        assertTrue("the capture really was stored; the guard stays latched", state.saved)
        assertNotNull("the reward moment must survive a failed discard", state.reward)
        assertFalse(state.reward!!.confirmed)
        assertEquals(0, state.reward!!.xpAwarded)
        assertFalse("no spinner may be left behind", state.submitting)

        // It was attempted exactly once — no retry loop behind a screen the user cannot act on.
        assertEquals(1, gateway.deletedIds.size)

        // And the cause is not lost, it is just not the user's problem.
        val warning = logWarning.warnings.single()
        assertEquals("delete rejected", warning.cause.message)
        // `LogWarning`'s contract: name the operation, never its subject. An id in logcat is PII
        // adjacent and adds nothing a throwable does not already say.
        assertFalse(
            "the capture id must not reach logcat",
            warning.message.contains("00000000-0000-0000-0000-000000000001")
        )
    }

    /**
     * The discard must not stand between the user and the overlay. It is launched after the state
     * update that puts the reward on screen, so even a DELETE that never answers leaves the peak
     * moment fully rendered — the same non-blocking contract the profile enrichment has.
     */
    @Test
    fun `the reward is on screen before the discard answers`() = runTest(dispatcher) {
        val gateway = GatedDeleteGateway(FakeCaptureGateway(confirmed = false))
        val viewModel = readyToSubmit(gateway)

        viewModel.submit()
        advanceUntilIdle() // the delete is now parked inside the gateway

        val reward = viewModel.state.value.reward
        assertNotNull("the celebration must not wait on the discard", reward)
        assertFalse(reward!!.confirmed)
        assertNull(viewModel.state.value.errorMessage)

        gateway.releaseDelete()
        advanceUntilIdle()

        assertEquals(1, gateway.deletedIds.size)
    }

    /**
     * The coroutine-scope decision, made testable.
     *
     * The discard fires at the exact instant the screen becomes dismissable: the overlay is up, and
     * "Ver meus registros" (or the back gesture) pops the Capture destination, clearing this
     * ViewModel and cancelling `viewModelScope`. A plain `viewModelScope.launch` would therefore
     * drop the DELETE precisely on the fast tap — the most likely case, not an edge one.
     *
     * `CaptureViewModel.discardUnconfirmed` detaches the job with [kotlinx.coroutines.NonCancellable]
     * so the request outlives the screen. This is that guarantee, driven through the real
     * [ViewModelStore.clear] rather than a stand-in, because `ViewModel.clear()` is what actually
     * cancels the scope on device.
     */
    @Test
    fun `the discard survives the user leaving the screen`() = runTest(dispatcher) {
        val gateway = GatedDeleteGateway(FakeCaptureGateway(confirmed = false))
        val viewModel = readyToSubmit(gateway)

        viewModel.submit()
        advanceUntilIdle() // the delete is in flight, parked inside the gateway

        // Navigating away: the Capture destination is popped and its ViewModel cleared.
        ViewModelStore().apply { put("capture", viewModel) }.clear()

        gateway.releaseDelete()
        advanceUntilIdle()

        assertEquals(
            "the record must still be taken back after the screen is gone",
            listOf("00000000-0000-0000-0000-000000000001"),
            gateway.deletedIds
        )
    }

    /**
     * The level-up line, and the only way it may ever be produced: a profile read taken before the
     * capture, diffed against one taken after. `POST /api/captures` returns no level and no badge
     * list, so a single after-the-fact read cannot tell "level 3" from "level 3 already".
     */
    @Test
    fun `a verified level-up and a new badge fill in the reward bonus`() = runTest(dispatcher) {
        val profiles = FakeProfileReader(
            before = profile(level = 2, unlocked = setOf("FIRST_CAPTURE")),
            after = profile(level = 3, unlocked = setOf("FIRST_CAPTURE", "THREE_CAPTURES"))
        )
        val viewModel = readyToSubmit(FakeCaptureGateway(), profiles)

        viewModel.submit()
        advanceUntilIdle()

        val bonus = viewModel.state.value.reward!!.bonus!!
        assertEquals(3, bonus.newLevel)
        // The display name, matched by the stable `achievement` enum rather than by the copy.
        assertEquals(listOf("Conquista THREE_CAPTURES"), bonus.newBadges)
    }

    /**
     * The rule the contract is strictest about: never show a level-up you did not verify.
     *
     * The user really is level 3 after the capture — but they may well have been level 3 before it
     * too, and with no baseline there is no way to know. The number is checkable on the very next
     * screen, so a wrong one is a lie the user catches immediately. Silence is the only honest
     * answer, and the celebration itself is untouched.
     */
    @Test
    fun `no level-up is claimed when the baseline profile read failed`() = runTest(dispatcher) {
        val profiles = FakeProfileReader(
            before = null,
            after = profile(level = 3, unlocked = setOf("FIRST_CAPTURE"))
        )
        val viewModel = readyToSubmit(FakeCaptureGateway(), profiles)

        viewModel.submit()
        advanceUntilIdle()

        val reward = viewModel.state.value.reward!!
        assertNull("an unverifiable level-up must not be shown", reward.bonus)
        // And the reward the capture response paid for is intact.
        assertEquals(60, reward.xpAwarded)
        assertTrue(reward.confirmed)
    }

    /**
     * The non-blocking contract, from the other side: the enrichment call fails outright and the
     * user still gets their celebration, in full, with the XP the capture response carried.
     *
     * If this ever regresses into an error state or a missing reward, the peak moment has been made
     * to depend on a second network round-trip — which is exactly what it must never do.
     */
    @Test
    fun `a failed profile read still leaves a complete reward`() = runTest(dispatcher) {
        val profiles = FakeProfileReader(before = profile(level = 2), after = null)
        val viewModel = readyToSubmit(FakeCaptureGateway(), profiles)

        viewModel.submit()
        advanceUntilIdle()

        val reward = viewModel.state.value.reward!!
        assertNull(reward.bonus)
        assertEquals(60, reward.xpAwarded)
        assertTrue(reward.confirmed)
        assertNull(viewModel.state.value.errorMessage)
    }

    /** Same level on both reads is not a promotion, and must produce no bonus at all. */
    @Test
    fun `an unchanged profile produces no bonus`() = runTest(dispatcher) {
        val profiles = FakeProfileReader(
            before = profile(level = 4, unlocked = setOf("FIRST_CAPTURE")),
            after = profile(level = 4, unlocked = setOf("FIRST_CAPTURE"))
        )
        val viewModel = readyToSubmit(FakeCaptureGateway(), profiles)

        viewModel.submit()
        advanceUntilIdle()

        assertNull(viewModel.state.value.reward!!.bonus)
    }

    /**
     * "Registrar outro" — the reward overlay's second way out. The form empties, the overlay goes,
     * and crucially `saved` is released: leaving it latched would leave the re-submit guard closed
     * and the Save button inert for the rest of the session.
     *
     * The position survives on purpose. The user has not moved.
     */
    @Test
    fun `starting a new capture clears the reward and lets the user save again`() = runTest(dispatcher) {
        val gateway = FakeCaptureGateway()
        val viewModel = readyToSubmit(gateway)

        viewModel.submit()
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.reward)

        viewModel.startNewCapture()

        val state = viewModel.state.value
        assertNull(state.reward)
        assertFalse(state.saved)
        assertEquals("", state.title)
        assertEquals("", state.description)
        assertNull(state.photoFile)
        // The photo the server has already consumed must not be cited again — citing it is a
        // guaranteed 400 ("This photo has already been used for a capture").
        assertNull(state.uploadedPhotoUrl)
        assertNotNull("the fix is still good; do not make the user wait for it again", state.coordinates)

        // And the guard really is open: a second, complete capture goes through.
        viewModel.onTitleChanged("Neve")
        viewModel.onDescriptionChanged("Flocos grossos")
        viewModel.onPhotoTaken(jpeg("second.jpg"))
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(2, gateway.createdRequests.size)
        assertTrue(viewModel.state.value.saved)
    }

    /**
     * The race the enrichment opens: the profile read is in flight while the user is already
     * looking at the overlay, and "Registrar outro" is one tap away. If the late answer wrote into
     * a cleared state it would resurrect the celebration on top of a fresh, empty form.
     */
    @Test
    fun `a late profile answer does not resurrect a dismissed reward`() = runTest(dispatcher) {
        val profiles = FakeProfileReader(
            before = profile(level = 2),
            after = profile(level = 3),
            gateAfter = true
        )
        val viewModel = readyToSubmit(FakeCaptureGateway(), profiles)

        viewModel.submit()
        advanceUntilIdle() // capture stored, overlay up, second profile read parked

        viewModel.startNewCapture()
        profiles.releaseAfter()
        advanceUntilIdle()

        assertNull("the dismissed reward must stay dismissed", viewModel.state.value.reward)
    }

    /** A ready-to-submit ViewModel: located, titled, described, phenomenon chosen, photo taken. */
    private fun TestScope.readyToSubmit(
        gateway: CaptureGateway,
        profiles: FakeProfileReader? = null,
        logWarning: LogWarning = noLogging
    ): CaptureViewModel {
        val viewModel = CaptureViewModel(
            captures = gateway,
            locationProvider = { Coordinates(-30.0346, -51.2177) },
            profile = profiles?.let { { it.read() } },
            logWarning = logWarning
        )
        viewModel.refreshLocation()
        advanceUntilIdle() // also lets the baseline profile read land, when there is one
        viewModel.onTitleChanged("Tempestade")
        viewModel.onDescriptionChanged("Raios")
        viewModel.onPhotoTaken(jpeg())
        return viewModel
    }

    /**
     * A [ProfileResponse] with only the three fields the reward diff reads filled in meaningfully.
     * The rest are zeroes: asserting on them would be asserting on the fixture.
     */
    private fun profile(level: Int, unlocked: Set<String> = emptySet()): ProfileResponse =
        ProfileResponse(
            user = UserSummary("u", "Test Pilot", "pilot@skydex.app", "2026-01-01T00:00:00Z"),
            level = level,
            totalXp = 0,
            xpToNextLevel = 0,
            confirmedCaptures = 0,
            totalCaptures = 0,
            capturedSpecies = 0,
            totalSpecies = 9,
            friends = 0,
            unlockedBadges = unlocked.size,
            totalBadges = 12,
            // The real endpoint returns every achievement, locked ones included, so the diff has to
            // filter on `unlocked` rather than on presence. A fixture that only listed the unlocked
            // ones would hide a diff that got that wrong.
            badges = listOf("FIRST_CAPTURE", "THREE_CAPTURES", "TEN_CAPTURES").map { achievement ->
                BadgeResponse(
                    achievement = achievement,
                    displayName = "Conquista $achievement",
                    description = "",
                    unlocked = achievement in unlocked,
                    unlockedAt = if (achievement in unlocked) "2026-08-07T17:00:00Z" else null
                )
            }
        )

    /** An [HttpException] shaped like a real one, carrying [body] as the error payload. */
    private fun httpError(code: Int, body: String): HttpException = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType()))
    )

}

class FakeCaptureGateway(
    // A relative path, because that is what the real endpoint returns. A fake that hands back
    // an absolute CDN URL would let the ViewModel pass a test it fails against the server,
    // which rejects anything outside `^/api/photos/...` (Task 7). `var` so a test can swap in a
    // failure after construction, for an eager upload that fails once the photo is taken.
    var uploadResult: Result<String> = Result.success("/api/photos/uploaded.jpg"),
    // Set to open the window that orphans a JPEG: the upload lands, the create does not, and the
    // photo on the server is now referenced by nothing. `var` so a test can also close the window
    // again and watch the retry succeed.
    var createFailure: Throwable? = null,
    /**
     * The verdict the server reaches, and what it is worth.
     *
     * Not two independent knobs: the backend awards `rarity.xp` on CONFIRMED and **exactly zero**
     * on every UNCONFIRMED path (`CaptureValidationService` returns
     * `ValidationResult(UNCONFIRMED, code, 0)` five times over, and `CaptureCommitService.commit`
     * re-zeroes it when the locked travel re-check downgrades a confirmed one). A fake that let a
     * test say "unconfirmed, 60 XP" would let the reward overlay pass on a combination the server
     * cannot produce.
     */
    var confirmed: Boolean = true,
    var rarity: String = "RARE"
) : CaptureGateway {

    val uploadedFiles = mutableListOf<File>()
    val createdRequests = mutableListOf<CreateWeatherEventRequest>()

    /** Ids handed to [delete], in order. Empty is the assertion that nothing was taken back. */
    val deletedIds = mutableListOf<String>()

    /** Set to make the silent discard fail. The record then survives on the server. */
    var deleteFailure: Throwable? = null

    /**
     * Parks [uploadPhoto] until a test completes it, so a retake can be driven *while* the eager
     * upload this fake would otherwise resolve instantly is still in flight.
     */
    var uploadGate: CompletableDeferred<Unit>? = null

    override suspend fun uploadPhoto(file: File): Result<String> {
        uploadedFiles += file
        uploadGate?.await()
        return uploadResult
    }

    override suspend fun delete(id: String): Result<Unit> {
        deletedIds += id
        return deleteFailure?.let { Result.failure(it) } ?: Result.success(Unit)
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
                authorName = "Test Pilot",
                // The server reads the weather itself now (Task 7): the request carries no
                // phenomenon for the fake to echo back, so this fixture picks one fixed species,
                // matching the fixed `phenomenonName` below.
                phenomenon = "THUNDERSTORM",
                phenomenonName = "Tempestade com Trovões",
                rarity = rarity,
                validationStatus = if (confirmed) "CONFIRMED" else "UNCONFIRMED",
                xpAwarded = if (confirmed) XP_BY_RARITY.getValue(rarity) else 0
            )
        )
    }

    private companion object {
        /** `Rarity(val xp: Int)` on the server, mirrored so the fake cannot invent an award. */
        val XP_BY_RARITY = mapOf(
            "COMMON" to 10,
            "UNCOMMON" to 25,
            "RARE" to 60,
            "EPIC" to 150,
            "LEGENDARY" to 400
        )
    }
}

/**
 * The optional profile reader, answering differently on the first call than on the second.
 *
 * That asymmetry is the whole point: the reward's level-up and badge lines come from diffing the
 * profile as it stood *before* the capture against the profile *after* it, so a fake that returned
 * one fixed value could never distinguish "levelled up" from "was already there" — which is the
 * exact mistake the diff exists to prevent.
 *
 * A `null` in either slot is that read failing. Both failure modes must leave the celebration
 * intact and simply produce no bonus.
 *
 * @param gateAfter parks the second read until [releaseAfter], so a test can act on the overlay
 *   while the enrichment is still in flight — the window in which the user can dismiss it.
 */
private class FakeProfileReader(
    private val before: ProfileResponse?,
    private val after: ProfileResponse?,
    gateAfter: Boolean = false
) {
    private val gate = if (gateAfter) CompletableDeferred() else CompletableDeferred(Unit)
    private var reads = 0

    fun releaseAfter() = gate.complete(Unit)

    suspend fun read(): Result<ProfileResponse> {
        val first = reads++ == 0
        if (!first) gate.await()
        val answer = if (first) before else after
        return answer?.let { Result.success(it) }
            ?: Result.failure(IOException("profile unavailable"))
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

    override suspend fun delete(id: String): Result<Unit> = creates.delete(id)
}

/**
 * A gateway whose `delete` really suspends, so a test can look at the screen *while* the silent
 * discard is still in flight.
 *
 * [FakeCaptureGateway.delete] answers without ever hitting a suspension point, which makes the
 * discard indistinguishable from a blocking one from the ViewModel's side — exactly the property
 * under test here.
 */
private class GatedDeleteGateway(
    private val delegate: FakeCaptureGateway
) : CaptureGateway by delegate {

    private val gate = CompletableDeferred<Unit>()

    val deletedIds: List<String> get() = delegate.deletedIds

    fun releaseDelete() = gate.complete(Unit)

    override suspend fun delete(id: String): Result<Unit> {
        gate.await()
        return delegate.delete(id)
    }
}

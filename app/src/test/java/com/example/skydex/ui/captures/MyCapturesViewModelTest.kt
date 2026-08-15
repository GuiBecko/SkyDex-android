package com.example.skydex.ui.captures

import com.example.skydex.data.remote.FakeSkyDexApi
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.data.repository.CaptureRepository
import com.example.skydex.ui.common.RecordingLogWarning
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.common.noLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MyCapturesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
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
        authorName = "Pilot",
        phenomenon = "THUNDERSTORM",
        phenomenonName = "Tempestade com Trovões",
        rarity = "RARE",
        validationStatus = "CONFIRMED",
        xpAwarded = 60
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the captures are loaded as soon as the view model exists`() = runTest(dispatcher) {
        api.myCapturesResponse = { listOf(capture) }

        val viewModel = MyCapturesViewModel(repository, noLogging)
        assertEquals(UiState.Loading, viewModel.state.value)

        advanceUntilIdle()
        assertEquals(UiState.Success(listOf(capture)), viewModel.state.value)
    }

    /**
     * The FIRST load. Nothing is on screen, so the failure takes the whole area — `UiState.Error`
     * carries no data and can only be drawn as the full-area `SkyDexNoticeState`. Unchanged by A4.
     */
    @Test
    fun `a failure becomes an error message and logs the cause`() = runTest(dispatcher) {
        val cause = IOException("offline")
        api.myCapturesResponse = { throw cause }
        val logWarning = RecordingLogWarning()

        val viewModel = MyCapturesViewModel(repository, logWarning)
        advanceUntilIdle()

        assertEquals(
            "Sem conexão",
            (viewModel.state.value as UiState.Error).message.title
        )
        assertEquals(cause, logWarning.warnings.single().cause)
    }

    @Test
    fun `refresh asks the api again`() = runTest(dispatcher) {
        api.myCapturesResponse = { throw IOException("offline") }
        val viewModel = MyCapturesViewModel(repository, noLogging)
        advanceUntilIdle()

        api.myCapturesResponse = { listOf(capture) }
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(UiState.Success(listOf(capture)), viewModel.state.value)
    }

    /**
     * Finding A4. The registers the user was reading are not deleted because a later reload failed:
     * the list stays in `Success` and the failure travels as `staleMessage`, which the screen draws
     * as an inline banner pinned above it.
     */
    @Test
    fun `a failed refresh keeps the registers already on screen`() = runTest(dispatcher) {
        api.myCapturesResponse = { listOf(capture) }
        val viewModel = MyCapturesViewModel(repository, noLogging)
        advanceUntilIdle()

        api.myCapturesResponse = { throw IOException("offline") }
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Success)
        assertEquals(listOf(capture), (state as UiState.Success).data)
        assertEquals("Sem conexão", state.staleMessage?.title)
    }

    /** The cause still has to reach logcat when the failure is only a banner. */
    @Test
    fun `a failed refresh still logs its cause`() = runTest(dispatcher) {
        api.myCapturesResponse = { listOf(capture) }
        val logWarning = RecordingLogWarning()
        val viewModel = MyCapturesViewModel(repository, logWarning)
        advanceUntilIdle()

        val cause = IOException("offline")
        api.myCapturesResponse = { throw cause }
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(cause, logWarning.warnings.single().cause)
    }

    /** A retry that works takes the banner away on its own. */
    @Test
    fun `a successful refresh clears the message`() = runTest(dispatcher) {
        api.myCapturesResponse = { listOf(capture) }
        val viewModel = MyCapturesViewModel(repository, noLogging)
        advanceUntilIdle()

        api.myCapturesResponse = { throw IOException("offline") }
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals("Sem conexão", (viewModel.state.value as UiState.Success).staleMessage?.title)

        api.myCapturesResponse = { listOf(capture) }
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(UiState.Success(listOf(capture)), viewModel.state.value)
    }

    /** Dismissing drops the banner and nothing else. */
    @Test
    fun `dismissing the message keeps the registers`() = runTest(dispatcher) {
        api.myCapturesResponse = { listOf(capture) }
        val viewModel = MyCapturesViewModel(repository, noLogging)
        advanceUntilIdle()

        api.myCapturesResponse = { throw IOException("offline") }
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.dismissMessage()

        assertEquals(UiState.Success(listOf(capture)), viewModel.state.value)
    }

    /** Entering a reload must not blank a list already on screen — the other half of A4. */
    @Test
    fun `a refresh over a loaded list does not fall back to Loading`() = runTest(dispatcher) {
        api.myCapturesResponse = { listOf(capture) }
        val viewModel = MyCapturesViewModel(repository, noLogging)
        advanceUntilIdle()

        viewModel.refresh()

        assertEquals(UiState.Success(listOf(capture)), viewModel.state.value)
    }
}

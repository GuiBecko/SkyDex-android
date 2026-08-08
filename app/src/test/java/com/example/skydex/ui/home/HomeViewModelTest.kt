package com.example.skydex.ui.home

import com.example.skydex.data.remote.FakeSkyDexApi
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSkyDexApi()
    private val repository = CaptureRepository(api)

    private val storm = NearbyPhenomenonResponse(
        phenomenon = "Tempestade",
        time = "2026-08-07T10:00",
        temperatureCelsius = 21.5,
        alertLevel = "Perigo"
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the screen starts loading before anything comes back`() = runTest(dispatcher) {
        api.nearbyResponse = { listOf(storm) }

        val viewModel = HomeViewModel(repository, noLogging)

        assertEquals(UiState.Loading, viewModel.state.value)
    }

    /**
     * The fetch is started by the ViewModel, not by the screen: a `LaunchedEffect` in the
     * composable would re-run on every rotation and throw the list away.
     */
    @Test
    fun `the nearby list is loaded without the screen asking for it`() = runTest(dispatcher) {
        api.nearbyResponse = { listOf(storm) }

        val viewModel = HomeViewModel(repository, noLogging)
        advanceUntilIdle()

        assertEquals(UiState.Success(listOf(storm)), viewModel.state.value)
        assertEquals(
            listOf(HomeViewModel.DEFAULT_LATITUDE to HomeViewModel.DEFAULT_LONGITUDE),
            api.nearbyCalls
        )
    }

    @Test
    fun `a failure becomes an error message and logs the cause without the coordinates`() = runTest(dispatcher) {
        val cause = IOException("offline")
        api.nearbyResponse = { throw cause }
        val logWarning = RecordingLogWarning()

        val viewModel = HomeViewModel(repository, logWarning)
        advanceUntilIdle()

        assertEquals(
            UiState.Error("Não foi possível carregar os eventos próximos."),
            viewModel.state.value
        )

        val warning = logWarning.warnings.single()
        assertEquals(cause, warning.cause)
        assertFalse(
            "the user's position must not reach logcat",
            warning.message.contains(HomeViewModel.DEFAULT_LATITUDE.toString()) ||
                warning.message.contains(HomeViewModel.DEFAULT_LONGITUDE.toString())
        )
    }

    @Test
    fun `load asks the api for the coordinates it was given`() = runTest(dispatcher) {
        api.nearbyResponse = { emptyList() }
        val viewModel = HomeViewModel(repository, noLogging)
        advanceUntilIdle()

        viewModel.load(latitude = -30.03, longitude = -51.23)
        advanceUntilIdle()

        assertEquals(-30.03 to -51.23, api.nearbyCalls.last())
        assertEquals(UiState.Success(emptyList<NearbyPhenomenonResponse>()), viewModel.state.value)
    }

    /** A retry after an error must show the spinner again rather than the stale error. */
    @Test
    fun `load returns to Loading while it is in flight`() = runTest(dispatcher) {
        api.nearbyResponse = { throw IOException("offline") }
        val viewModel = HomeViewModel(repository, noLogging)
        advanceUntilIdle()

        api.nearbyResponse = { listOf(storm) }
        viewModel.load(latitude = -30.03, longitude = -51.23)

        assertEquals(UiState.Loading, viewModel.state.value)
    }
}

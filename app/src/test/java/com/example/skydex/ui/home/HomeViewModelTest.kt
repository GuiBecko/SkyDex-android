package com.example.skydex.ui.home

import com.example.skydex.data.remote.FakeSkyDexApi
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
import com.example.skydex.data.repository.CaptureRepository
import com.example.skydex.ui.common.UiState
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
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Task 10 changed [HomeViewModel]'s shape entirely: it now takes a `locationProvider` lambda
 * instead of loading a hardcoded placeholder position from `init`, and its state carries the
 * [HomeData] wrapper (coordinates alongside the phenomena) rather than a bare list. These tests
 * replace the Task 9 suite, which asserted the old constructor, `load(lat, lon)`, and the
 * `DEFAULT_LATITUDE`/`DEFAULT_LONGITUDE` placeholders — none of which compile against the new
 * ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSkyDexApi()
    private val repository = CaptureRepository(api)

    private val coordinates = Coordinates(-30.0346, -51.2177)

    private val storm = NearbyPhenomenonResponse(
        phenomenon = "Tempestade",
        time = "2026-08-07T10:00",
        temperatureCelsius = 21.5,
        alertLevel = "Perigo"
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts in Loading before anything comes back`() = runTest(dispatcher) {
        api.nearbyResponse = { listOf(storm) }

        val viewModel = HomeViewModel(repository) { coordinates }

        assertEquals(UiState.Loading, viewModel.state.value)
    }

    @Test
    fun `loads the nearby phenomena for the device's current position`() = runTest(dispatcher) {
        api.nearbyResponse = { listOf(storm) }
        val viewModel = HomeViewModel(repository) { coordinates }

        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        assertEquals(
            UiState.Success(HomeData(coordinates, listOf(storm))),
            viewModel.state.value
        )
        assertEquals(listOf(coordinates.latitude to coordinates.longitude), api.nearbyCalls)
    }

    @Test
    fun `a missing fix becomes a GPS prompt instead of calling the api`() = runTest(dispatcher) {
        val viewModel = HomeViewModel(repository) { null }

        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        assertEquals(
            UiState.Error("Ative o GPS para ver os fenômenos da sua região."),
            viewModel.state.value
        )
        assertEquals(emptyList<Pair<Double, Double>>(), api.nearbyCalls)
    }

    @Test
    fun `a nearby lookup failure becomes a generic error message`() = runTest(dispatcher) {
        api.nearbyResponse = { throw IOException("offline") }
        val viewModel = HomeViewModel(repository) { coordinates }

        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        assertEquals(
            UiState.Error("Não foi possível carregar os eventos próximos."),
            viewModel.state.value
        )
    }

    /** A retry after an error must show the spinner again rather than the stale error. */
    @Test
    fun `loadForCurrentPosition returns to Loading while it is in flight`() = runTest(dispatcher) {
        api.nearbyResponse = { throw IOException("offline") }
        val viewModel = HomeViewModel(repository) { coordinates }
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        api.nearbyResponse = { listOf(storm) }
        viewModel.loadForCurrentPosition()

        assertEquals(UiState.Loading, viewModel.state.value)
    }
}

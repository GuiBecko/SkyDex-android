package com.example.skydex.ui.home

import com.example.skydex.data.remote.FakeSkyDexApi
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
import com.example.skydex.data.repository.CaptureRepository
import com.example.skydex.ui.common.RecordingLogWarning
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.common.noLogging
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

        val viewModel = HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)

        assertEquals(UiState.Loading, viewModel.state.value)
    }

    @Test
    fun `loads the nearby phenomena for the device's current position`() = runTest(dispatcher) {
        api.nearbyResponse = { listOf(storm) }
        val viewModel = HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)

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
        val viewModel = HomeViewModel(repository, locationProvider = { null }, logWarning = noLogging)

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
        val viewModel = HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)

        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        assertEquals(
            UiState.Error("Não foi possível carregar os eventos próximos."),
            viewModel.state.value
        )
    }

    /**
     * The cause has to reach logcat — the user-facing copy is deliberately generic, so offline, an
     * expired token and a parse failure are indistinguishable without it.
     *
     * The coordinates must **not**. `LogWarning`'s contract says messages name the operation and
     * never its subject, and Home is the one place where the subject is the user's real GPS
     * position rather than an e-mail address: they are in scope three lines from the call site, and
     * interpolating them would be a one-character mistake that ships a location trail into every
     * bug report. The assertion is what keeps that contract enforced rather than merely intended.
     */
    @Test
    fun `a nearby lookup failure logs its cause without leaking the position`() = runTest(dispatcher) {
        val cause = IOException("offline")
        api.nearbyResponse = { throw cause }
        val logWarning = RecordingLogWarning()
        val viewModel =
            HomeViewModel(repository, locationProvider = { coordinates }, logWarning = logWarning)

        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        val warning = logWarning.warnings.single()
        assertEquals(cause, warning.cause)
        assertFalse(warning.message.contains(coordinates.latitude.toString()))
        assertFalse(warning.message.contains(coordinates.longitude.toString()))
    }

    /**
     * The fetch is started by the ViewModel's own bookkeeping, not by the composable: HomeScreen's
     * `LaunchedEffect(Unit)` re-runs on every Activity recreation (the manifest declares no
     * `configChanges`), so a rotation would re-launch the permission request, re-run a GPS fix and
     * re-hit the network — throwing away a list the surviving ViewModel already holds.
     */
    @Test
    fun `the initial load is claimed once per view model`() = runTest(dispatcher) {
        val viewModel =
            HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)

        assertTrue(viewModel.shouldLoadOnEntry())
        assertFalse(viewModel.shouldLoadOnEntry())
    }

    /** The list the latch protects: a successful load must survive re-entry untouched. */
    @Test
    fun `re-entering the screen after a successful load does not reload`() = runTest(dispatcher) {
        api.nearbyResponse = { listOf(storm) }
        val viewModel =
            HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)

        assertTrue(viewModel.shouldLoadOnEntry())
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        assertFalse(viewModel.shouldLoadOnEntry())
        assertEquals(1, api.nearbyCalls.size)
    }

    /**
     * The other half, and the one that makes the latch safe: an `Error` is a dead end, not a result
     * worth protecting. This ViewModel outlives the composable *and* the bottom bar's
     * `popUpTo(HOME) { saveState = true }` + `restoreState`, so a one-shot latch would keep a
     * failed load — a phone that was offline for one second — on screen for the whole process
     * lifetime, with leaving the tab and coming back changing nothing.
     */
    @Test
    fun `re-entering the screen after a failed load starts a new one`() = runTest(dispatcher) {
        api.nearbyResponse = { throw IOException("offline") }
        val viewModel =
            HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)

        assertTrue(viewModel.shouldLoadOnEntry())
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()
        assertEquals(UiState.Error("Não foi possível carregar os eventos próximos."), viewModel.state.value)

        assertTrue(viewModel.shouldLoadOnEntry())
    }

    /** A missing fix is the same dead end, reached through the other branch of the load. */
    @Test
    fun `re-entering the screen after a missing fix starts a new load`() = runTest(dispatcher) {
        val viewModel =
            HomeViewModel(repository, locationProvider = { null }, logWarning = noLogging)

        assertTrue(viewModel.shouldLoadOnEntry())
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        assertTrue(viewModel.shouldLoadOnEntry())
    }

    /**
     * And re-entry must not restart a load that is still running: `Loading` is not a failure, and
     * re-entering while the first fix is still being taken would queue a second one behind it.
     */
    @Test
    fun `re-entering while a load is still in flight does not start another`() = runTest(dispatcher) {
        api.nearbyResponse = { listOf(storm) }
        val viewModel =
            HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)

        assertTrue(viewModel.shouldLoadOnEntry())
        viewModel.loadForCurrentPosition()

        assertFalse(viewModel.shouldLoadOnEntry())
    }

    /**
     * And the guard lives in that one-shot latch, *not* in `loadForCurrentPosition`. Short-circuiting
     * the load whenever the state is already `Success` would look equivalent and would quietly break
     * the retry button, which is the one control the user has when the list is stale or wrong.
     */
    @Test
    fun `an explicit reload still hits the api after the initial load was claimed`() = runTest(dispatcher) {
        api.nearbyResponse = { listOf(storm) }
        val viewModel =
            HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)

        viewModel.shouldLoadOnEntry()
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()
        assertEquals(UiState.Success(HomeData(coordinates, listOf(storm))), viewModel.state.value)

        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        assertEquals(2, api.nearbyCalls.size)
    }

    /** A retry after an error must show the spinner again rather than the stale error. */
    @Test
    fun `loadForCurrentPosition returns to Loading while it is in flight`() = runTest(dispatcher) {
        api.nearbyResponse = { throw IOException("offline") }
        val viewModel = HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        api.nearbyResponse = { listOf(storm) }
        viewModel.loadForCurrentPosition()

        assertEquals(UiState.Loading, viewModel.state.value)
    }
}

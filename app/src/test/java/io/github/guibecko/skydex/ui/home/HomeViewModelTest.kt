package io.github.guibecko.skydex.ui.home

import io.github.guibecko.skydex.data.remote.FakeSkyDexApi
import io.github.guibecko.skydex.data.remote.dto.NearbyPhenomenonResponse
import io.github.guibecko.skydex.data.repository.CaptureRepository
import io.github.guibecko.skydex.ui.common.RecordingLogWarning
import io.github.guibecko.skydex.ui.common.UiState
import io.github.guibecko.skydex.ui.common.noLogging
import io.github.guibecko.skydex.util.Coordinates
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
        phenomenon = "THUNDERSTORM",
        phenomenonName = "Tempestade",
        rarity = "RARE",
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
            "Não achamos onde você está",
            (viewModel.state.value as UiState.Error).message.title
        )
        assertEquals(emptyList<Pair<Double, Double>>(), api.nearbyCalls)
    }

    @Test
    fun `a nearby lookup failure becomes a generic error message`() = runTest(dispatcher) {
        api.nearbyResponse = { throw IOException("offline") }
        val viewModel = HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)

        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        // Offline, not a blanket "não foi possível": the presenter separates transport from
        // everything else, so the user is told the one thing they can actually act on.
        assertEquals(
            "Sem conexão",
            (viewModel.state.value as UiState.Error).message.title
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
        assertEquals("Sem conexão", (viewModel.state.value as UiState.Error).message.title)

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

    /**
     * ...and the exact opposite once a list exists, which is finding A4. Falling back to `Loading`
     * over content already on screen destroys it just as thoroughly as an error state does — only
     * with a spinner instead of a sentence. The phenomena stay put while the reload runs.
     */
    @Test
    fun `a reload over a loaded list does not fall back to Loading`() = runTest(dispatcher) {
        api.nearbyResponse = { listOf(storm) }
        val viewModel =
            HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        viewModel.loadForCurrentPosition()

        assertEquals(UiState.Success(HomeData(coordinates, listOf(storm))), viewModel.state.value)
    }

    /** And when that reload fails, the list survives and the failure arrives as a banner (A4). */
    @Test
    fun `a failed reload keeps the phenomena already on screen`() = runTest(dispatcher) {
        api.nearbyResponse = { listOf(storm) }
        val viewModel =
            HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        api.nearbyResponse = { throw IOException("offline") }
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Success)
        assertEquals(listOf(storm), (state as UiState.Success).data.phenomena)
        assertEquals("Sem conexão", state.staleMessage?.title)
    }

    /** Losing the GPS fix on a reload is the same story: keep the list, hang the instruction over it. */
    @Test
    fun `losing the fix on a reload keeps the phenomena already on screen`() = runTest(dispatcher) {
        api.nearbyResponse = { listOf(storm) }
        var fix: Coordinates? = coordinates
        val viewModel = HomeViewModel(repository, locationProvider = { fix }, logWarning = noLogging)
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        fix = null
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Success)
        assertEquals(listOf(storm), (state as UiState.Success).data.phenomena)
        assertEquals("Não achamos onde você está", state.staleMessage?.title)
    }

    /** A successful reload clears the banner without the user having to close it. */
    @Test
    fun `a successful reload clears the message`() = runTest(dispatcher) {
        api.nearbyResponse = { listOf(storm) }
        val viewModel =
            HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        api.nearbyResponse = { throw IOException("offline") }
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        api.nearbyResponse = { listOf(storm) }
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        assertEquals(UiState.Success(HomeData(coordinates, listOf(storm))), viewModel.state.value)
    }

    /** Dismissing drops the banner and leaves the list exactly where it was. */
    @Test
    fun `dismissing the message keeps the phenomena`() = runTest(dispatcher) {
        api.nearbyResponse = { listOf(storm) }
        val viewModel =
            HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        api.nearbyResponse = { throw IOException("offline") }
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        viewModel.dismissMessage()

        assertEquals(UiState.Success(HomeData(coordinates, listOf(storm))), viewModel.state.value)
    }

    /**
     * The latch re-arms for a dead end, not for a stale banner. A user holding a list *and* a
     * visible notice with its own retry has somewhere to go, so re-entering the tab must not throw
     * that list away and re-run the permission request behind their back.
     */
    @Test
    fun `re-entering after a failed reload over a loaded list does not reload`() = runTest(dispatcher) {
        api.nearbyResponse = { listOf(storm) }
        val viewModel =
            HomeViewModel(repository, locationProvider = { coordinates }, logWarning = noLogging)
        assertTrue(viewModel.shouldLoadOnEntry())
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        api.nearbyResponse = { throw IOException("offline") }
        viewModel.loadForCurrentPosition()
        advanceUntilIdle()

        assertFalse(viewModel.shouldLoadOnEntry())
    }
}

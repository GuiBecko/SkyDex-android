package com.example.skydex.ui.feed

import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.social.FakeSocialGateway
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

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val capture = WeatherEventResponse(
        id = "e1",
        title = "Tempestade",
        description = "Raios sobre o lago",
        photoUrl = "http://localhost:8080/api/photos/a.jpg",
        capturedAt = "2026-08-05T10:00:00Z",
        latitude = -30.0346,
        longitude = -51.2177,
        userId = "u1",
        authorName = "Amiga",
        phenomenon = "THUNDERSTORM",
        phenomenonName = "Tempestade com Trovões",
        rarity = "RARE",
        validationStatus = "CONFIRMED",
        xpAwarded = 60
    )

    @Test
    fun `loads the feed on construction`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(feedResult = Result.success(listOf(capture)))
        val viewModel = FeedViewModel(gateway)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Success)
        assertEquals(listOf(capture), (state as UiState.Success).data)
        assertEquals(listOf(0 to 20), gateway.feedCalls)
    }

    /**
     * The FIRST load. Nothing is on screen, so there is nothing to protect and the failure takes the
     * whole area — `UiState.Error`, which carries no data and can therefore only be drawn as the
     * full-area `SkyDexNoticeState`. This is the behaviour finding A4 does **not** change.
     */
    @Test
    fun `surfaces a message when the feed cannot be loaded`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(feedResult = Result.failure(IOException("offline")))
        val viewModel = FeedViewModel(gateway)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Error)
        assertEquals("Sem conexão", (state as UiState.Error).message.title)
    }

    /**
     * Finding A4 itself. A feed the user has already read is not deleted because the *second* load
     * failed: the posts stay in `Success` and the failure rides along as `staleMessage`, which the
     * screen draws as an inline banner above them.
     */
    @Test
    fun `a failed refresh keeps the feed already on screen`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(feedResult = Result.success(listOf(capture)))
        val viewModel = FeedViewModel(gateway)
        advanceUntilIdle()

        gateway.feedResult = Result.failure(IOException("offline"))
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Success)
        assertEquals(listOf(capture), (state as UiState.Success).data)
        assertEquals("Sem conexão", state.staleMessage?.title)
    }

    /** And a retry that works takes the banner away — nobody should have to dismiss a fixed problem. */
    @Test
    fun `a successful refresh clears the message`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(feedResult = Result.success(listOf(capture)))
        val viewModel = FeedViewModel(gateway)
        advanceUntilIdle()

        gateway.feedResult = Result.failure(IOException("offline"))
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals("Sem conexão", (viewModel.state.value as UiState.Success).staleMessage?.title)

        gateway.feedResult = Result.success(listOf(capture))
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(UiState.Success(listOf(capture)), viewModel.state.value)
    }

    /** Dismissing is the user saying "I know — let me keep reading". The feed must not move. */
    @Test
    fun `dismissing the message keeps the feed`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(feedResult = Result.success(listOf(capture)))
        val viewModel = FeedViewModel(gateway)
        advanceUntilIdle()

        gateway.feedResult = Result.failure(IOException("offline"))
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.dismissMessage()

        assertEquals(UiState.Success(listOf(capture)), viewModel.state.value)
    }

    /**
     * The half of A4 a spinner hides just as effectively as an error does: entering a reload must
     * not blank a feed that is already on screen. If this ever reports `Loading`, the refresh has
     * gone back to throwing the posts away before it even knows whether it will fail.
     */
    @Test
    fun `a refresh over a loaded feed does not fall back to Loading`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(feedResult = Result.success(listOf(capture)))
        val viewModel = FeedViewModel(gateway)
        advanceUntilIdle()

        viewModel.refresh()

        assertEquals(UiState.Success(listOf(capture)), viewModel.state.value)
    }

    // -----------------------------------------------------------------------------------------
    // Pull to refresh
    //
    // `isRefreshing` exists to drive one thing — the indicator that comes down from the top of the
    // Feed — so these tests are about exactly two questions: does it go up only for the gesture,
    // and does it always come back down.
    // -----------------------------------------------------------------------------------------

    /**
     * The gesture's own signal, up while the request is in flight and down when it lands.
     *
     * The assertion between the two `advanceUntilIdle`s is the load-bearing one: `StandardTestDispatcher`
     * has not run the coroutine yet at that point, so this is the state the user actually sees mid-pull.
     */
    @Test
    fun `the pull gesture raises isRefreshing and lowers it on success`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(feedResult = Result.success(listOf(capture)))
        val viewModel = FeedViewModel(gateway)
        advanceUntilIdle()

        viewModel.refreshFromPull()
        assertTrue(viewModel.isRefreshing.value)

        advanceUntilIdle()
        assertFalse(viewModel.isRefreshing.value)
        assertEquals(UiState.Success(listOf(capture)), viewModel.state.value)
    }

    /**
     * The classic bug of this component: the pull fails and the wheel keeps turning forever, so the
     * screen looks permanently busy and the user cannot even tell that anything went wrong.
     */
    @Test
    fun `the pull indicator stops even when the refresh fails`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(feedResult = Result.success(listOf(capture)))
        val viewModel = FeedViewModel(gateway)
        advanceUntilIdle()

        gateway.feedResult = Result.failure(IOException("offline"))
        viewModel.refreshFromPull()
        assertTrue(viewModel.isRefreshing.value)

        advanceUntilIdle()
        assertFalse(viewModel.isRefreshing.value)
    }

    /**
     * Pulling does not opt out of finding A4. A pull that fails over a feed the user is reading
     * behaves exactly like a failed retry: the posts stay, the failure becomes the inline banner.
     */
    @Test
    fun `a failed pull keeps the feed and reports the failure as a banner`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(feedResult = Result.success(listOf(capture)))
        val viewModel = FeedViewModel(gateway)
        advanceUntilIdle()

        gateway.feedResult = Result.failure(IOException("offline"))
        viewModel.refreshFromPull()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Success)
        assertEquals(listOf(capture), (state as UiState.Success).data)
        assertEquals("Sem conexão", state.staleMessage?.title)
        assertFalse(viewModel.isRefreshing.value)
    }

    /**
     * And the other half of the contract: with nothing ever loaded there is no content to protect,
     * so a failed pull still lands on the full-area `UiState.Error` — not a banner over a blank
     * screen. The indicator stops all the same.
     */
    @Test
    fun `a failed pull with nothing loaded stays a full-area error`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(feedResult = Result.failure(IOException("offline")))
        val viewModel = FeedViewModel(gateway)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is UiState.Error)

        viewModel.refreshFromPull()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Error)
        assertFalse(viewModel.isRefreshing.value)
    }

    /**
     * Why `isRefreshing` is not derived from `UiState.Loading`. The first load has its own centred
     * spinner; if the gesture's flag rose here too, opening the Feed would show two spinners at once.
     */
    @Test
    fun `the initial load never raises isRefreshing`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(feedResult = Result.success(listOf(capture)))
        val viewModel = FeedViewModel(gateway)

        assertTrue(viewModel.state.value is UiState.Loading)
        assertFalse(viewModel.isRefreshing.value)

        advanceUntilIdle()
        assertFalse(viewModel.isRefreshing.value)
    }

    /** Nor does the retry button — it is served by the full-area notice, which has its own affordance. */
    @Test
    fun `a plain refresh never raises isRefreshing`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(feedResult = Result.success(listOf(capture)))
        val viewModel = FeedViewModel(gateway)
        advanceUntilIdle()

        viewModel.refresh()

        assertFalse(viewModel.isRefreshing.value)
    }

    /**
     * The gesture can fire again while the first request is still open. Two overlapping loads would
     * race to publish into the same state, so the second one is dropped rather than queued.
     */
    @Test
    fun `pulling again while a pull is in flight does not start a second request`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(feedResult = Result.success(listOf(capture)))
        val viewModel = FeedViewModel(gateway)
        advanceUntilIdle()

        viewModel.refreshFromPull()
        viewModel.refreshFromPull()
        advanceUntilIdle()

        assertEquals(listOf(0 to 20, 0 to 20), gateway.feedCalls)
    }
}

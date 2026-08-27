package io.github.guibecko.skydex.ui.skydex

import io.github.guibecko.skydex.data.remote.dto.SkyDexEntryResponse
import io.github.guibecko.skydex.data.remote.dto.SkyDexResponse
import io.github.guibecko.skydex.ui.common.UiState
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
class SkyDexViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val sample = SkyDexResponse(
        level = 2,
        totalXp = 145,
        xpToNextLevel = 255,
        capturedSpecies = 2,
        totalSpecies = 9,
        entries = listOf(
            SkyDexEntryResponse("THUNDERSTORM", "Tempestade com Trovões", "RARE", 60, true, 2, "2026-08-01T10:00:00Z"),
            SkyDexEntryResponse("SNOW", "Neve", "EPIC", 150, false, 0, null)
        )
    )

    @Test
    fun `loads the collection on construction`() = runTest(dispatcher) {
        val viewModel = SkyDexViewModel(FakeSkyDexGateway(Result.success(sample)))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Success)
        assertEquals(2, (state as UiState.Success).data.level)
        assertEquals(9, state.data.totalSpecies)
    }

    /**
     * The FIRST load. Nothing is on screen, so the failure takes the whole area — `UiState.Error`
     * carries no data and can only be drawn as the full-area `SkyDexNoticeState`. Finding A4 leaves
     * this case exactly as it was.
     */
    @Test
    fun `surfaces a message when the collection cannot be loaded`() = runTest(dispatcher) {
        val viewModel = SkyDexViewModel(FakeSkyDexGateway(Result.failure(IOException("offline"))))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Error)
        assertEquals("Sem conexão", (state as UiState.Error).message.title)
    }

    /**
     * Finding A4. A collection the user is already browsing survives a refresh that fails: the
     * species stay in `Success` and the failure arrives as `staleMessage`, a banner above them.
     */
    @Test
    fun `a failed refresh keeps the collection already on screen`() = runTest(dispatcher) {
        val gateway = FakeSkyDexGateway(Result.success(sample))
        val viewModel = SkyDexViewModel(gateway)
        advanceUntilIdle()

        gateway.result = Result.failure(IOException("offline"))
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Success)
        assertEquals(sample, (state as UiState.Success).data)
        assertEquals("Sem conexão", state.staleMessage?.title)
    }

    /** A retry that works takes the banner away on its own. */
    @Test
    fun `a successful refresh clears the message`() = runTest(dispatcher) {
        val gateway = FakeSkyDexGateway(Result.success(sample))
        val viewModel = SkyDexViewModel(gateway)
        advanceUntilIdle()

        gateway.result = Result.failure(IOException("offline"))
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals("Sem conexão", (viewModel.state.value as UiState.Success).staleMessage?.title)

        gateway.result = Result.success(sample)
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(UiState.Success(sample), viewModel.state.value)
    }

    /** Dismissing drops the banner and nothing else. */
    @Test
    fun `dismissing the message keeps the collection`() = runTest(dispatcher) {
        val gateway = FakeSkyDexGateway(Result.success(sample))
        val viewModel = SkyDexViewModel(gateway)
        advanceUntilIdle()

        gateway.result = Result.failure(IOException("offline"))
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.dismissMessage()

        assertEquals(UiState.Success(sample), viewModel.state.value)
    }

    /** Entering a reload must not blank a collection already on screen — the other half of A4. */
    @Test
    fun `a refresh over a loaded collection does not fall back to Loading`() = runTest(dispatcher) {
        val gateway = FakeSkyDexGateway(Result.success(sample))
        val viewModel = SkyDexViewModel(gateway)
        advanceUntilIdle()

        viewModel.refresh()

        assertEquals(UiState.Success(sample), viewModel.state.value)
    }
}

/** `result` is a `var` so one ViewModel can be shown a successful load and then a failing reload. */
class FakeSkyDexGateway(var result: Result<SkyDexResponse>) : SkyDexGateway {
    override suspend fun collection(): Result<SkyDexResponse> = result
}

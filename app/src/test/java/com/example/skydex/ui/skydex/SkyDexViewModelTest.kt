package com.example.skydex.ui.skydex

import com.example.skydex.data.remote.dto.SkyDexEntryResponse
import com.example.skydex.data.remote.dto.SkyDexResponse
import com.example.skydex.ui.common.UiState
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

    @Test
    fun `surfaces a message when the collection cannot be loaded`() = runTest(dispatcher) {
        val viewModel = SkyDexViewModel(FakeSkyDexGateway(Result.failure(IOException("offline"))))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Error)
        assertEquals("Não foi possível carregar sua coleção.", (state as UiState.Error).message)
    }
}

class FakeSkyDexGateway(private val result: Result<SkyDexResponse>) : SkyDexGateway {
    override suspend fun collection(): Result<SkyDexResponse> = result
}

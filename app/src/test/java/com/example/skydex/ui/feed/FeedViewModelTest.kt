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

    @Test
    fun `surfaces a message when the feed cannot be loaded`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(feedResult = Result.failure(IOException("offline")))
        val viewModel = FeedViewModel(gateway)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Error)
        assertEquals("Não foi possível carregar o feed.", (state as UiState.Error).message)
    }
}

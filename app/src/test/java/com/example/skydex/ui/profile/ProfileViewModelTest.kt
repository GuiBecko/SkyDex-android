package com.example.skydex.ui.profile

import com.example.skydex.data.remote.dto.BadgeResponse
import com.example.skydex.data.remote.dto.ProfileResponse
import com.example.skydex.data.remote.dto.UserSummary
import com.example.skydex.ui.common.UiState
import kotlinx.coroutines.CompletableDeferred
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
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val sample = ProfileResponse(
        user = UserSummary("u1", "Becker", "becker@skydex.com", "2026-07-01T10:00:00Z"),
        level = 2,
        totalXp = 145,
        xpToNextLevel = 255,
        confirmedCaptures = 4,
        totalCaptures = 6,
        capturedSpecies = 2,
        totalSpecies = 9,
        friends = 1,
        unlockedBadges = 2,
        totalBadges = 13,
        badges = listOf(
            BadgeResponse("FIRST_CAPTURE", "Molhou o Dedo", "Uma vez.", true, "2026-08-01T10:00:00Z"),
            BadgeResponse("THREE_CAPTURES", "Caçador de Nuvem", "Três.", true, "2026-08-03T10:00:00Z"),
            BadgeResponse("TEN_CAPTURES", "Meteorologista de Varanda", "Dez.", false, null)
        )
    )

    @Test
    fun `loads the profile on construction`() = runTest(dispatcher) {
        val viewModel = ProfileViewModel(FakeProfileGateway(Result.success(sample))) {}
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Success)
        assertEquals(2, (state as UiState.Success).data.level)
        assertEquals(13, state.data.totalBadges)
        assertEquals(2, state.data.badges.count { it.unlocked })
    }

    @Test
    fun `surfaces a message when the profile cannot be loaded`() = runTest(dispatcher) {
        val viewModel = ProfileViewModel(FakeProfileGateway(Result.failure(IOException("offline")))) {}
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Error)
        assertEquals("Não foi possível carregar seu perfil.", (state as UiState.Error).message)
    }

    @Test
    fun `logging out invokes the logout action`() = runTest(dispatcher) {
        var loggedOut = false
        val viewModel = ProfileViewModel(FakeProfileGateway(Result.success(sample))) { loggedOut = true }
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertTrue(loggedOut)
    }

    @Test
    fun `does not signal completion until the pending session write finishes`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val viewModel = ProfileViewModel(FakeProfileGateway(Result.success(sample))) { gate.await() }
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        // The write is still pending: the screen must not have been told to navigate yet,
        // because navigating now would tear down the ViewModelStore hosting this very
        // coroutine and could cancel the write mid-flight, leaving a stale session on disk.
        assertEquals(false, viewModel.loggedOut.value)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(true, viewModel.loggedOut.value)
    }

    @Test
    fun `a failed session write surfaces an error instead of crashing`() = runTest(dispatcher) {
        val viewModel = ProfileViewModel(FakeProfileGateway(Result.success(sample))) {
            throw IOException("disk full")
        }
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertEquals(false, viewModel.loggedOut.value)
        val state = viewModel.state.value
        assertTrue(state is UiState.Error)
        assertEquals("Não foi possível sair da conta. Tente novamente.", (state as UiState.Error).message)
    }
}

class FakeProfileGateway(private val result: Result<ProfileResponse>) : ProfileGateway {
    override suspend fun profile(): Result<ProfileResponse> = result
}

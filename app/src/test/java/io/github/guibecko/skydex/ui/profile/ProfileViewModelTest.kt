package io.github.guibecko.skydex.ui.profile

import io.github.guibecko.skydex.data.remote.dto.BadgeResponse
import io.github.guibecko.skydex.data.remote.dto.ProfileResponse
import io.github.guibecko.skydex.data.remote.dto.UserSummary
import io.github.guibecko.skydex.ui.common.UiState
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
        assertEquals("Sem conexão", (state as UiState.Error).message.title)
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

    /**
     * The message still has to arrive — and the profile has to survive it (finding A4). Failing to
     * leave is no reason to take the screen away: before this, a failed session write replaced a
     * fully loaded profile with a full-area error, so the identity card, the stats and the badge
     * shelf all disappeared because a disk write did not land.
     */
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
        assertTrue(state is UiState.Success)
        assertEquals(sample, (state as UiState.Success).data)
        assertEquals("Não deu para sair da conta", state.staleMessage?.title)
    }

    /** A refresh that fails over a loaded profile keeps every part of it and adds a banner (A4). */
    @Test
    fun `a failed refresh keeps the profile and carries the message`() = runTest(dispatcher) {
        val gateway = SwitchableProfileGateway(Result.success(sample))
        val viewModel = ProfileViewModel(gateway) {}
        advanceUntilIdle()

        gateway.result = Result.failure(IOException("offline"))
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Success)
        assertEquals(sample, (state as UiState.Success).data)
        assertEquals("Sem conexão", state.staleMessage?.title)
    }
}

class FakeProfileGateway(private val result: Result<ProfileResponse>) : ProfileGateway {
    override suspend fun profile(): Result<ProfileResponse> = result
}

/** A gateway whose answer can change between calls, so one ViewModel can see a load and a reload. */
class SwitchableProfileGateway(var result: Result<ProfileResponse>) : ProfileGateway {
    override suspend fun profile(): Result<ProfileResponse> = result
}

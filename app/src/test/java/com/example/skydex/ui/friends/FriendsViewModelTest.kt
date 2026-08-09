package com.example.skydex.ui.friends

import com.example.skydex.data.remote.dto.FriendRequestResponse
import com.example.skydex.data.remote.dto.FriendResponse
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.ui.social.FakeSocialGateway
import com.example.skydex.ui.social.SocialGateway
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

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads friends and pending requests on construction`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(
            friends = listOf(FriendResponse("u1", "Alice", "alice@skydex.com", "2026-08-01T10:00:00Z")),
            requests = listOf(
                FriendRequestResponse("r1", "u2", "Bob", "bob@skydex.com", "2026-08-02T10:00:00Z")
            )
        )
        val viewModel = FriendsViewModel(gateway)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.friends.size)
        assertEquals("Bob", viewModel.state.value.requests.single().requesterName)
    }

    @Test
    fun `sending a request clears the field and reports success`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway()
        val viewModel = FriendsViewModel(gateway)
        advanceUntilIdle()

        viewModel.onEmailChanged("bob@skydex.com")
        viewModel.sendRequest()
        advanceUntilIdle()

        assertEquals(listOf("bob@skydex.com"), gateway.sentTo)
        assertEquals("", viewModel.state.value.email)
        assertEquals("Convite enviado!", viewModel.state.value.message)
    }

    @Test
    fun `refuses to send a request with a blank email`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway()
        val viewModel = FriendsViewModel(gateway)
        advanceUntilIdle()

        viewModel.sendRequest()
        advanceUntilIdle()

        assertEquals(0, gateway.sentTo.size)
        assertEquals("Digite o e-mail do seu amigo.", viewModel.state.value.message)
    }

    @Test
    fun `reports a failed request without clearing the field`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(sendResult = Result.failure(IOException("nope")))
        val viewModel = FriendsViewModel(gateway)
        advanceUntilIdle()

        viewModel.onEmailChanged("ghost@skydex.com")
        viewModel.sendRequest()
        advanceUntilIdle()

        assertEquals("ghost@skydex.com", viewModel.state.value.email)
        assertEquals("Não foi possível enviar o convite.", viewModel.state.value.message)
    }

    @Test
    fun `accepting a request reloads the lists`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(
            requests = listOf(
                FriendRequestResponse("r1", "u2", "Bob", "bob@skydex.com", "2026-08-02T10:00:00Z")
            )
        )
        val viewModel = FriendsViewModel(gateway)
        advanceUntilIdle()

        gateway.friends = listOf(FriendResponse("u2", "Bob", "bob@skydex.com", "2026-08-02T10:00:00Z"))
        gateway.requests = emptyList()
        viewModel.accept("r1")
        advanceUntilIdle()

        assertEquals(listOf("r1"), gateway.accepted)
        assertEquals(1, viewModel.state.value.friends.size)
        assertEquals(0, viewModel.state.value.requests.size)
    }
}

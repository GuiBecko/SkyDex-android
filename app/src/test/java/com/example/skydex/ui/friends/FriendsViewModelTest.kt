package com.example.skydex.ui.friends

import com.example.skydex.data.remote.dto.FriendRequestResponse
import com.example.skydex.data.remote.dto.FriendResponse
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.ui.common.Tone
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
            friends = listOf(FriendResponse("f1", "u1", "Alice", "alice@skydex.com", "2026-08-01T10:00:00Z")),
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

        // Audit finding A5. The screen used to pick the colour with
        // `message == "Convite enviado!"`, so this — the app's one social reward — rendered grey
        // and any copy edit would have flipped it to a red failure. The tone rides on the message
        // now, and this asserts it rather than the wording that used to stand in for it.
        val message = viewModel.state.value.message!!
        assertEquals(Tone.SUCCESS, message.tone)
        assertEquals("Convite enviado!", message.title)
    }

    /**
     * The other half of A5: a failure and a success must be distinguishable *structurally*. If
     * this ever holds, something is again inferring the kind of feedback from the copy.
     */
    @Test
    fun `a failed invite and a sent invite never share a tone`() = runTest(dispatcher) {
        val sent = FriendsViewModel(FakeSocialGateway())
        advanceUntilIdle()
        sent.onEmailChanged("bob@skydex.com")
        sent.sendRequest()
        advanceUntilIdle()

        val failed = FriendsViewModel(FakeSocialGateway(sendResult = Result.failure(IOException("nope"))))
        advanceUntilIdle()
        failed.onEmailChanged("bob@skydex.com")
        failed.sendRequest()
        advanceUntilIdle()

        assertEquals(Tone.SUCCESS, sent.state.value.message!!.tone)
        assertNotEquals(Tone.SUCCESS, failed.state.value.message!!.tone)
    }

    @Test
    fun `refuses to send a request with a blank email`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway()
        val viewModel = FriendsViewModel(gateway)
        advanceUntilIdle()

        viewModel.sendRequest()
        advanceUntilIdle()

        assertEquals(0, gateway.sentTo.size)
        assertEquals("Digite o e-mail do seu amigo", viewModel.state.value.message?.title)
        assertEquals(Tone.NOTICE, viewModel.state.value.message?.tone)
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
        assertEquals("Sem conexão", viewModel.state.value.message?.title)
        assertEquals(Tone.NOTICE, viewModel.state.value.message?.tone)
    }

    @Test
    fun `declining a request reloads the lists`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(
            requests = listOf(
                FriendRequestResponse("r1", "u2", "Bob", "bob@skydex.com", "2026-08-02T10:00:00Z")
            )
        )
        val viewModel = FriendsViewModel(gateway)
        advanceUntilIdle()

        gateway.requests = emptyList()
        viewModel.decline("r1")
        advanceUntilIdle()

        assertEquals(listOf("r1"), gateway.declined)
        assertEquals(0, viewModel.state.value.requests.size)
        assertNull(viewModel.state.value.message)
    }

    /**
     * A failed decline must still reload, and this is not a stylistic preference — it is what
     * stops the screen lying about what the server did.
     *
     * The failure this covers is a real one that shipped: `declineFriendRequest` was declared to
     * return `Unit`, and Retrofit 2.9.0 cannot map the backend's empty 204 onto a non-null type, so
     * every *successful* decline arrived here as `Result.failure`. Refreshing only on success meant
     * the request the user had just deleted stayed on screen under an error message, and tapping
     * again deleted nothing because it was already gone.
     *
     * The wire-level cause is fixed, but the ViewModel should not depend on the network layer being
     * perfect to show the truth: refreshing on both branches means the list always reflects the
     * server, whatever the client concluded about the call. The message still reports the failure.
     */
    @Test
    fun `a failed decline still reloads the lists so the screen matches the server`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(
            requests = listOf(
                FriendRequestResponse("r1", "u2", "Bob", "bob@skydex.com", "2026-08-02T10:00:00Z")
            )
        )
        gateway.declineResult = Result.failure(IOException("nope"))
        val viewModel = FriendsViewModel(gateway)
        advanceUntilIdle()

        // The server did delete it, whatever the client concluded about the response.
        gateway.requests = emptyList()
        viewModel.decline("r1")
        advanceUntilIdle()

        assertEquals(
            "a failed decline must still refresh, or a request that is already gone stays on screen",
            0,
            viewModel.state.value.requests.size
        )
        assertEquals("Sem conexão", viewModel.state.value.message?.title)
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

        gateway.friends = listOf(FriendResponse("f2", "u2", "Bob", "bob@skydex.com", "2026-08-02T10:00:00Z"))
        gateway.requests = emptyList()
        viewModel.accept("r1")
        advanceUntilIdle()

        assertEquals(listOf("r1"), gateway.accepted)
        assertEquals(1, viewModel.state.value.friends.size)
        assertEquals(0, viewModel.state.value.requests.size)
    }

    @Test
    fun `unfriending sends the friendship id, not the user id, and reloads`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(
            friends = listOf(FriendResponse("f9", "u9", "Bob", "bob@skydex.com", "2026-08-02T10:00:00Z"))
        )
        val viewModel = FriendsViewModel(gateway)
        advanceUntilIdle()

        gateway.friends = emptyList()
        viewModel.unfriend(viewModel.state.value.friends.single())
        advanceUntilIdle()

        // "f9", never "u9". The delete route addresses the relationship; handing it the friend's own
        // id 404s, and the screen would report a failure for a friendship that is still there.
        assertEquals(listOf("f9"), gateway.declined)
        assertEquals(0, viewModel.state.value.friends.size)
        assertNull(viewModel.state.value.message)
    }

    /** Same reasoning as the failed decline above: the list must match the server either way. */
    @Test
    fun `a failed unfriend still reloads the lists`() = runTest(dispatcher) {
        val gateway = FakeSocialGateway(
            friends = listOf(FriendResponse("f9", "u9", "Bob", "bob@skydex.com", "2026-08-02T10:00:00Z"))
        )
        gateway.declineResult = Result.failure(IOException("nope"))
        val viewModel = FriendsViewModel(gateway)
        advanceUntilIdle()

        gateway.friends = emptyList()
        viewModel.unfriend(FriendResponse("f9", "u9", "Bob", "bob@skydex.com", "2026-08-02T10:00:00Z"))
        advanceUntilIdle()

        assertEquals(
            "a failed unfriend must still refresh, or a friend who is already gone stays on screen",
            0,
            viewModel.state.value.friends.size
        )
        assertEquals("Sem conexão", viewModel.state.value.message?.title)
    }
}

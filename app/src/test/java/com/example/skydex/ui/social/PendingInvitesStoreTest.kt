package com.example.skydex.ui.social

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class PendingInvitesStoreTest {

    @Test
    fun `starts at zero and takes the server's number`() = runBlocking {
        val gateway = FakeSocialGateway(pendingCountResult = Result.success(3))
        val store = PendingInvitesStore(gateway)

        // Zero before anything is asked: the badge must not flash a stale number on a cold start.
        assertEquals(0, store.count.value)

        store.refresh()

        assertEquals(3, store.count.value)
    }

    /**
     * The behaviour this pins is the whole reason the store exists rather than the bar reading the
     * gateway directly: a dropped request is not evidence that the invites went away.
     *
     * Falling back to zero would blink the badge off every time the network hiccuped mid-navigation,
     * and the count is re-read on *every* navigation — so on a bad connection the dot would spend
     * most of its life missing.
     */
    @Test
    fun `a failed refresh keeps the last known count`() = runBlocking {
        val gateway = FakeSocialGateway(pendingCountResult = Result.success(2))
        val store = PendingInvitesStore(gateway)
        store.refresh()

        gateway.pendingCountResult = Result.failure(IOException("offline"))
        store.refresh()

        assertEquals(
            "a dropped request must not read as 'no invites'",
            2,
            store.count.value
        )
    }

    @Test
    fun `clear drops the count without asking the server`() = runBlocking {
        val gateway = FakeSocialGateway(pendingCountResult = Result.success(5))
        val store = PendingInvitesStore(gateway)
        store.refresh()
        val callsBefore = gateway.pendingCountCalls

        store.clear()

        // Sign-out path: this object outlives the session, so the next account to log in on the
        // device must not inherit the previous user's badge. And it cannot ask the server — there is
        // no token any more.
        assertEquals(0, store.count.value)
        assertEquals(callsBefore, gateway.pendingCountCalls)
    }
}

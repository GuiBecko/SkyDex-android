package com.example.skydex.ui.detail

import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.ui.common.CaptureUnavailable
import com.example.skydex.ui.common.Tone
import com.example.skydex.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The capture-detail screen has no network call — there is no `GET api/events/{id}` — so its entire
 * behaviour is the resolution of one id against the in-memory [CaptureRegistry]. That gives exactly
 * two branches, and the second one (the cold start) is the one that would otherwise ship as a blank
 * screen nobody notices until a user's phone kills the app in the background.
 *
 * No coroutine machinery here on purpose: the lookup is synchronous, so there is nothing to advance
 * and the state is final the moment the ViewModel exists. A test that had to `advanceUntilIdle`
 * would be describing a ViewModel this is not.
 */
class CaptureDetailViewModelTest {

    private val capture = WeatherEventResponse(
        id = "cap-1",
        title = "Relâmpago sobre a represa",
        description = "Peguei a descarga bem no meio do enquadramento.",
        photoUrl = "https://example.test/lightning.jpg",
        capturedAt = "2026-08-13T21:40:00Z",
        latitude = -23.55,
        longitude = -46.63,
        userId = "u2",
        authorName = "Alice",
        phenomenon = "THUNDERSTORM",
        phenomenonName = "Tempestade com Trovões",
        rarity = "LEGENDARY",
        validationStatus = "CONFIRMED",
        xpAwarded = 400
    )

    private val unconfirmed = capture.copy(
        id = "cap-2",
        validationStatus = "UNCONFIRMED",
        // Zero on every unconfirmed path — `CaptureValidationService` returns
        // `ValidationResult(UNCONFIRMED, observedCode, 0)`, and the commit re-zeroes it.
        xpAwarded = 0
    )

    // ---------------------------------------------------------------------------------------------
    // Resolve hit — the ordinary path: the user tapped a card in this process
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a capture the list handed over resolves immediately`() {
        val registry = CaptureRegistry()
        registry.remember(capture)

        val viewModel = CaptureDetailViewModel(capture.id, registry)

        // Not Loading, not even for one emission: nothing here can block, so a spinner would be a
        // lie about how the screen works.
        assertEquals(UiState.Success(capture), viewModel.state.value)
    }

    @Test
    fun `the resolved capture is the same object, not a copy`() {
        val registry = CaptureRegistry()
        registry.remember(capture)

        val state = CaptureDetailViewModel(capture.id, registry).state.value

        assertSame(capture, (state as UiState.Success).data)
    }

    @Test
    fun `the right capture comes back when several are remembered`() {
        val registry = CaptureRegistry()
        registry.remember(capture)
        registry.remember(unconfirmed)

        assertEquals(
            UiState.Success(unconfirmed),
            CaptureDetailViewModel(unconfirmed.id, registry).state.value
        )
    }

    @Test
    fun `a resolved capture never carries a stale banner`() {
        val registry = CaptureRegistry()
        registry.remember(capture)

        // Nothing on this screen refreshes, so nothing can go stale. A non-null staleMessage would
        // mean someone wired a reload that cannot exist.
        assertNull((CaptureDetailViewModel(capture.id, registry).state.value as UiState.Success).staleMessage)
    }

    // ---------------------------------------------------------------------------------------------
    // The unconfirmed branch — the capture resolves, it just did not earn anything
    // ---------------------------------------------------------------------------------------------

    /**
     * "Not confirmed" is **not** a failure state: the row, the photo and the location are all real
     * and the screen shows every one of them. Only the XP is absent. If this ever starts resolving
     * to `UiState.Error`, the app has begun treating an Open-Meteo outage as the user's mistake.
     */
    @Test
    fun `an unconfirmed capture resolves as success, not as an error`() {
        val registry = CaptureRegistry()
        registry.remember(unconfirmed)

        val state = CaptureDetailViewModel(unconfirmed.id, registry).state.value

        assertTrue("an unconfirmed capture is still a capture", state is UiState.Success)
        assertEquals("UNCONFIRMED", (state as UiState.Success).data.validationStatus)
        assertEquals(0, state.data.xpAwarded)
    }

    // ---------------------------------------------------------------------------------------------
    // Resolve miss — the cold start after process death
    // ---------------------------------------------------------------------------------------------

    /**
     * Android restored the back stack from the saved route, so the id is valid; the registry went
     * with the process. There is no endpoint to fall back to, so the only honest outcome is the
     * error state — and it has to be the *full-area* one, which `UiState.Error` is the only shape
     * that can be drawn as.
     */
    @Test
    fun `an id this process never saw resolves to the unavailable message`() {
        val state = CaptureDetailViewModel("cap-1", CaptureRegistry()).state.value

        assertEquals(UiState.Error(CaptureUnavailable), state)
    }

    @Test
    fun `the unavailable message offers a way back rather than a retry`() {
        val state = CaptureDetailViewModel("gone", CaptureRegistry()).state.value as UiState.Error

        // "Tentar de novo" would be a button that cannot work: retrying resolves against the same
        // empty registry.
        assertEquals("Voltar", state.message.actionLabel)
        assertEquals(Tone.NOTICE, state.message.tone)
        assertNotNull(state.message.body)
    }

    @Test
    fun `a blank id is a miss and not a crash`() {
        val registry = CaptureRegistry()
        registry.remember(capture)

        assertTrue(CaptureDetailViewModel("", registry).state.value is UiState.Error)
    }

    // ---------------------------------------------------------------------------------------------
    // The registry itself
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `remembering the same capture twice keeps one entry`() {
        val registry = CaptureRegistry()
        registry.remember(capture)
        registry.remember(capture.copy(title = "Título corrigido"))

        assertEquals(1, registry.size())
        assertEquals("Título corrigido", registry.find(capture.id)?.title)
    }

    /**
     * A user scrolling a long feed taps many captures over a session. The registry has to stay
     * bounded, and it has to drop the *oldest* rather than refusing new entries — a full cache that
     * stops accepting would break the detail screen for everything the user opens from then on.
     */
    @Test
    fun `the registry evicts the least recently used entry past its limit`() {
        val registry = CaptureRegistry(maxEntries = 2)
        registry.remember(capture.copy(id = "a"))
        registry.remember(capture.copy(id = "b"))
        // Touch "a" so "b" becomes the least recently *used*, not merely the least recently added.
        registry.find("a")
        registry.remember(capture.copy(id = "c"))

        assertEquals(2, registry.size())
        assertNotNull("the recently used entry survives", registry.find("a"))
        assertNotNull("the newest entry is kept", registry.find("c"))
        assertNull("the least recently used entry is dropped", registry.find("b"))
    }

    // ---------------------------------------------------------------------------------------------
    // Origin
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `the origin survives a round trip through the route`() {
        assertEquals(CaptureOrigin.FEED, CaptureOrigin.parse(CaptureOrigin.FEED.name))
        assertEquals(CaptureOrigin.MINE, CaptureOrigin.parse(CaptureOrigin.MINE.name))
    }

    /**
     * A hand-typed deep link or a renamed constant costs the author block, not the screen. Falling
     * back to MINE is the conservative half: it shows *less*, and it never claims someone else's
     * name over the user's own capture.
     */
    @Test
    fun `an unknown origin degrades to mine instead of throwing`() {
        assertEquals(CaptureOrigin.MINE, CaptureOrigin.parse("SOMETHING_ELSE"))
        assertEquals(CaptureOrigin.MINE, CaptureOrigin.parse(null))
        assertEquals(CaptureOrigin.MINE, CaptureOrigin.parse("feed"))
    }
}

package com.example.skydex.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The state machine behind audit finding A4, tested once here instead of five times over.
 *
 * Every screen's ViewModel now reports failures through [failWith] rather than writing
 * `UiState.Error` itself, so these three helpers are the single place where "replace the screen" and
 * "hang a banner over it" are decided. The ViewModel suites prove each screen is wired to them; this
 * proves the rule they are wired to.
 */
class UiStateTest {

    private val failure = UiMessage(
        title = "Sem conexão",
        body = "Verifique sua internet e tente de novo.",
        tone = Tone.NOTICE,
        actionLabel = "Tentar de novo"
    )

    private val other = UiMessage(
        title = "Servidor fora do ar",
        body = "Tente de novo em alguns minutos.",
        tone = Tone.NOTICE
    )

    // -----------------------------------------------------------------------------------------
    // failWith — nothing on screen becomes the full area, content on screen becomes a banner
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a failure with nothing loaded takes the whole area`() {
        assertEquals(UiState.Error(failure), UiState.Loading.failWith(failure))
    }

    @Test
    fun `a failure after an earlier failure stays a full-area error`() {
        assertEquals(UiState.Error(failure), UiState.Error(other).failWith(failure))
    }

    @Test
    fun `a failure with content loaded keeps the content and carries the message`() {
        assertEquals(
            UiState.Success("feed", staleMessage = failure),
            UiState.Success("feed").failWith(failure)
        )
    }

    /** A second failure replaces the first rather than stacking: one banner, always the latest. */
    @Test
    fun `a second failure replaces the message it found`() {
        assertEquals(
            UiState.Success("feed", staleMessage = failure),
            UiState.Success("feed", staleMessage = other).failWith(failure)
        )
    }

    // -----------------------------------------------------------------------------------------
    // startLoad — the spinner is for an empty screen only
    // -----------------------------------------------------------------------------------------

    @Test
    fun `starting a load with nothing on screen shows the spinner`() {
        assertEquals(UiState.Loading, UiState.Error(failure).startLoad())
    }

    /**
     * The half of A4 that is easy to miss: blanking the screen behind a spinner on every refresh
     * destroys the content just as surely as an error state does. A reload over existing data keeps
     * the data and only clears the banner the previous attempt left behind.
     */
    @Test
    fun `starting a load over content keeps the content and clears the banner`() {
        assertEquals(
            UiState.Success("feed"),
            UiState.Success("feed", staleMessage = failure).startLoad()
        )
    }

    // -----------------------------------------------------------------------------------------
    // dismissMessage
    // -----------------------------------------------------------------------------------------

    @Test
    fun `dismissing drops the message and keeps the content`() {
        assertEquals(
            UiState.Success("feed"),
            UiState.Success("feed", staleMessage = failure).dismissMessage()
        )
    }

    /**
     * A full-area error is not dismissible: closing it would leave a blank screen with no control on
     * it, which is precisely the dead end finding B3 was about. `dismissMessage` therefore has to be
     * a no-op there rather than "clear whatever is showing".
     */
    @Test
    fun `dismissing a full-area error does nothing`() {
        val state: UiState<String> = UiState.Error(failure)
        assertSame(state, state.dismissMessage())
    }

    @Test
    fun `dismissing when there is nothing to dismiss does nothing`() {
        val state: UiState<String> = UiState.Success("feed")
        assertSame(state, state.dismissMessage())
    }
}

package com.example.skydex.ui.common

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The audit's finding A11 in test form: no output of [CaptureDate.format] may ever contain an ISO
 * timestamp, and no input may make it throw.
 *
 * Both `now` and the zone are injected so the assertions do not depend on the machine's clock or
 * time zone — the whole suite reads as if it ran in São Paulo at 2026-08-14 12:00 local.
 */
class CaptureDateTest {

    private val zone = ZoneId.of("America/Sao_Paulo")

    /** 2026-08-14T12:00 local = 15:00Z (UTC-3). */
    private val now = Instant.parse("2026-08-14T15:00:00Z")

    private fun format(raw: String?) = CaptureDate.format(raw, now = now, zone = zone)

    @Test
    fun `a capture from seconds ago reads as agora mesmo`() {
        assertEquals("agora mesmo", format("2026-08-14T14:59:30Z"))
    }

    @Test
    fun `a capture minutes old counts the minutes`() {
        assertEquals("há 12 min", format("2026-08-14T14:48:00Z"))
    }

    @Test
    fun `a capture earlier today counts the hours`() {
        assertEquals("há 5 h", format("2026-08-14T10:00:00Z"))
    }

    @Test
    fun `a capture from yesterday says ontem and the local time`() {
        // 2026-08-13T21:40Z is 18:40 in São Paulo.
        assertEquals("ontem, 18:40", format("2026-08-13T21:40:00Z"))
    }

    @Test
    fun `a capture from this week counts the days`() {
        assertEquals("há 3 dias", format("2026-08-11T15:00:00Z"))
    }

    @Test
    fun `a capture older than a week falls back to a pt-BR date`() {
        assertEquals("7 de ago", format("2026-08-07T18:20:00Z"))
    }

    @Test
    fun `a capture from another year keeps the year`() {
        assertEquals("25 de dez de 2025", format("2025-12-25T13:00:00Z"))
    }

    /** Server clocks run ahead of phone clocks. A negative duration must not reach the screen. */
    @Test
    fun `a timestamp in the future does not render a negative duration`() {
        assertEquals("agora mesmo", format("2026-08-14T18:00:00Z"))
    }

    @Test
    fun `an offset timestamp is accepted`() {
        assertEquals("há 12 min", format("2026-08-14T11:48:00-03:00"))
    }

    @Test
    fun `a zoneless timestamp is read in the local zone`() {
        assertEquals("há 12 min", format("2026-08-14T11:48:00"))
    }

    @Test
    fun `a bare date is accepted`() {
        assertEquals("7 de ago", format("2026-08-07"))
    }

    @Test
    fun `garbage yields null instead of throwing or leaking the raw text`() {
        assertNull(format("not a date"))
        assertNull(format(""))
        assertNull(format("   "))
        assertNull(format(null))
    }
}

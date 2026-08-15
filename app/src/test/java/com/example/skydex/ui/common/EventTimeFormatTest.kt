package com.example.skydex.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * Covers [formatEventTime] (audit finding A11). It lives in `ui/common` alongside
 * [CaptureDate], the other half of the A11 fix.
 *
 * The function replaced `time.substringAfter("T")`, which leaked raw ISO fragments (`14:30:00Z`)
 * whenever the payload carried seconds or an offset. These tests pin the three shapes the backend
 * can send and, more importantly, pin that malformed input degrades to a placeholder instead of
 * throwing on a card inside a list.
 */
class EventTimeFormatTest {

    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    @Test
    fun `formats a local date-time as 24-hour clock`() {
        assertEquals("14:30", formatEventTime("2026-08-07T14:30", saoPaulo))
    }

    @Test
    fun `drops the seconds a local date-time may carry`() {
        assertEquals("09:05", formatEventTime("2026-08-07T09:05:42", saoPaulo))
    }

    @Test
    fun `converts a UTC instant to the target zone instead of showing the Z`() {
        // 17:30Z is 14:30 in Sao Paulo (UTC-3). The old substring would have shown "17:30:00Z".
        assertEquals("14:30", formatEventTime("2026-08-07T17:30:00Z", saoPaulo))
    }

    @Test
    fun `converts an explicit offset to the target zone`() {
        assertEquals("15:30", formatEventTime("2026-08-07T14:30:00-04:00", saoPaulo))
    }

    @Test
    fun `accepts a bare clock time`() {
        assertEquals("06:00", formatEventTime("06:00", saoPaulo))
    }

    @Test
    fun `falls back to a placeholder on a date with no time`() {
        assertEquals("--:--", formatEventTime("2026-08-07", saoPaulo))
    }

    @Test
    fun `falls back to a placeholder on unparseable text`() {
        assertEquals("--:--", formatEventTime("amanhã de manhã", saoPaulo))
    }

    @Test
    fun `falls back to a placeholder on an empty field`() {
        assertEquals("--:--", formatEventTime("", saoPaulo))
        assertEquals("--:--", formatEventTime("   ", saoPaulo))
    }

    @Test
    fun `falls back to a placeholder on an out-of-range clock`() {
        assertEquals("--:--", formatEventTime("2026-08-07T25:61", saoPaulo))
    }
}

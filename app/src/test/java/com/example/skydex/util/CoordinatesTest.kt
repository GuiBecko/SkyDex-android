package com.example.skydex.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinatesTest {

    @Test
    fun `accepts a real position`() {
        assertTrue(Coordinates(-30.0346, -51.2177).isPlausible())
    }

    @Test
    fun `rejects null island, which is what a failed fix looks like`() {
        assertFalse(Coordinates(0.0, 0.0).isPlausible())
    }

    @Test
    fun `rejects out-of-range values`() {
        assertFalse(Coordinates(91.0, 0.0).isPlausible())
        assertFalse(Coordinates(0.0, 181.0).isPlausible())
    }
}

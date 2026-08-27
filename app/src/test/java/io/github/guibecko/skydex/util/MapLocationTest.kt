package io.github.guibecko.skydex.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The slippy-tile projection is the one part of the location feature that can be *quietly* wrong: a
 * tile computed with the wrong sign, the wrong zoom or a degrees/radians slip still renders a
 * perfectly pretty map — of somewhere else. Nobody reviewing a screenshot would catch it.
 *
 * So the reference values below are computed independently (the OSM wiki's published formula) rather
 * than read off this implementation, and they pin all four quadrants plus every edge the projection
 * has: the equator, the antimeridian and the two poles Web Mercator cannot represent.
 */
class MapLocationTest {

    // -----------------------------------------------------------------------------------------
    // Tile math — known coordinates, known tiles
    // -----------------------------------------------------------------------------------------

    @Test
    fun `null island sits exactly on the corner of the four middle tiles at z15`() {
        // 2^15 = 32768 tiles per axis; (0, 0) is the top-left corner of tile (16384, 16384).
        assertEquals(MapTile(15, 16384, 16384), mapTileFor(0.0, 0.0, zoom = 15))
    }

    @Test
    fun `the whole world is one tile at z0`() {
        assertEquals(MapTile(0, 0, 0), mapTileFor(-30.0346, -51.2177, zoom = 0))
        assertEquals(MapTile(0, 0, 0), mapTileFor(51.5074, -0.1278, zoom = 0))
    }

    /** Porto Alegre — southern and western hemispheres, both coordinates negative. */
    @Test
    fun `a southwestern position lands on its published tile`() {
        assertEquals(MapTile(15, 11722, 19252), mapTileFor(-30.0346, -51.2177, zoom = 15))
    }

    /** São Paulo — the coordinates the previews and the ViewModel tests use. */
    @Test
    fun `sao paulo lands on its published tile`() {
        assertEquals(MapTile(15, 12139, 18590), mapTileFor(-23.55, -46.63, zoom = 15))
    }

    /** London — northern hemisphere, and just west of the prime meridian. */
    @Test
    fun `a northwestern position lands on its published tile`() {
        assertEquals(MapTile(15, 16372, 10896), mapTileFor(51.5074, -0.1278, zoom = 15))
    }

    @Test
    fun `the default zoom is the neighbourhood-level preview zoom`() {
        assertEquals(LOCATION_PREVIEW_ZOOM, mapTileFor(-23.55, -46.63).zoom)
        assertEquals(mapTileFor(-23.55, -46.63, LOCATION_PREVIEW_ZOOM), mapTileFor(-23.55, -46.63))
    }

    // -----------------------------------------------------------------------------------------
    // Tile math — the edges, where a total function earns its keep
    // -----------------------------------------------------------------------------------------

    /**
     * `tan(90°)` is infinite, so an unclamped implementation returns a `NaN` tile here and the
     * capture-detail screen renders a broken URL — or throws, on the way to `Int`.
     */
    @Test
    fun `the poles clamp to the top and bottom rows instead of producing NaN`() {
        val north = mapTileFor(90.0, 10.0, zoom = 3)
        val south = mapTileFor(-90.0, 10.0, zoom = 3)

        assertEquals(0, north.y)
        assertEquals(7, south.y)
    }

    /**
     * Exactly +180° is the seam: `(180 + 180) / 360 * 2^z` is `2^z`, one column past the east edge.
     * That URL 404s, which the user would see as a permanently broken map for anyone standing on
     * the antimeridian.
     */
    @Test
    fun `the antimeridian stays inside the tile grid on both sides`() {
        assertEquals(3, mapTileFor(0.0, 180.0, zoom = 2).x)
        assertEquals(0, mapTileFor(0.0, -180.0, zoom = 2).x)
    }

    @Test
    fun `out-of-range coordinates are clamped rather than thrown on`() {
        // A corrupt payload must cost a wrong-looking map, never the screen.
        val tile = mapTileFor(latitude = 1_000.0, longitude = -5_000.0, zoom = 4)

        assertTrue(tile.x in 0..15)
        assertTrue(tile.y in 0..15)
    }

    @Test
    fun `zoom is clamped to what the tile server actually serves`() {
        assertEquals(0, mapTileFor(0.0, 0.0, zoom = -3).zoom)
        assertEquals(19, mapTileFor(0.0, 0.0, zoom = 42).zoom)
    }

    // -----------------------------------------------------------------------------------------
    // URLs
    // -----------------------------------------------------------------------------------------

    @Test
    fun `the tile url is the standard osm z x y png address`() {
        assertEquals(
            "https://tile.openstreetmap.org/15/12139/18590.png",
            mapTileFor(-23.55, -46.63, zoom = 15).osmTileUrl()
        )
    }

    // -----------------------------------------------------------------------------------------
    // Human-readable coordinates
    // -----------------------------------------------------------------------------------------

    @Test
    fun `southwestern coordinates read with hemisphere letters, not minus signs`() {
        assertEquals("23,5500° S · 46,6333° O", formatCoordinates(-23.55, -46.6333))
    }

    @Test
    fun `northeastern coordinates read with the other two letters`() {
        assertEquals("35,6762° N · 139,6503° L", formatCoordinates(35.6762, 139.6503))
    }

    @Test
    fun `the equator and the prime meridian are not negative`() {
        assertEquals("0,0000° N · 0,0000° L", formatCoordinates(0.0, 0.0))
    }

    /** Finding A11's rule, one level down: no transport-shaped value ever reaches the user. */
    @Test
    fun `the readable form never contains a raw signed decimal`() {
        val text = formatCoordinates(-30.0346, -51.2177)

        assertTrue("should not print a minus sign: $text", !text.contains("-"))
        assertTrue("should use the pt-BR decimal comma: $text", text.contains("30,0346"))
    }

    // -----------------------------------------------------------------------------------------
    // geo: URIs — where the decimal separator has to go the other way
    // -----------------------------------------------------------------------------------------

    /**
     * The bug this pins: formatting the URI with the device locale produces `geo:-23,550000,...`
     * on a Brazilian phone, and the map app either opens the wrong place or nothing at all. The
     * display string uses commas *on purpose*; the URI must not.
     */
    @Test
    fun `the geo uri uses dots regardless of the pt-BR display format`() {
        val uri = geoUri(-23.55, -46.6333)

        assertEquals("geo:-23.550000,-46.633300?q=-23.550000,-46.633300", uri)
        // The only commas allowed are the two that separate latitude from longitude. A decimal
        // comma would leave four.
        assertEquals(2, uri.count { it == ',' })
        assertTrue("the display form must not leak into the URI", !uri.contains("23,55"))
    }

    @Test
    fun `a label becomes a percent-encoded pin name`() {
        val uri = geoUri(-23.55, -46.6333, label = "Relâmpago sobre a represa")

        assertTrue("the label should be appended in parentheses: $uri", uri.endsWith(")"))
        assertTrue("spaces must be %20, not +: $uri", !uri.contains("+"))
        assertTrue(uri.startsWith("geo:-23.550000,-46.633300?q=-23.550000,-46.633300("))
    }

    @Test
    fun `a blank label is dropped rather than becoming empty parentheses`() {
        assertEquals(geoUri(1.0, 2.0), geoUri(1.0, 2.0, label = "   "))
        assertEquals(geoUri(1.0, 2.0), geoUri(1.0, 2.0, label = null))
    }
}

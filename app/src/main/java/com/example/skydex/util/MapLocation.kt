package com.example.skydex.util

import java.net.URLEncoder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.tan

/**
 * # Showing *where* a capture happened, without a maps SDK
 *
 * SkyDex has no Google Maps dependency and no Maps API key, and provisioning one is the product
 * owner's decision, not the app's. So the detail screen does not embed an interactive map: it draws
 * a **single static raster tile** from OpenStreetMap, which needs no key and no SDK, and hands the
 * real navigation off to whatever map app the phone already has (see [geoUri]).
 *
 * Everything in this file is a **pure function over numbers and strings** — no Android types, no
 * Compose, no I/O. That is deliberate: the slippy-tile projection is the one part of the location
 * feature that can be wrong in a way nobody notices (a tile 200m off still looks like a map), so it
 * has to be assertable in a plain JUnit test. See `MapLocationTest`.
 *
 * ## OpenStreetMap tile usage policy
 *
 * `tile.openstreetmap.org` is a volunteer-funded service. Its usage policy requires that clients
 * identify themselves with a real `User-Agent`, do not bulk-download, and cache. SkyDex requests
 * **one tile per capture detail view**, Coil caches it, and the identifying header is set once for
 * the whole app in `SkyDexApplication.newImageLoader`. If this file ever grows a loop over tiles,
 * that policy has been broken and a proper tile provider is needed instead.
 */

// ---------------------------------------------------------------------------------------------
// Tile math
// ---------------------------------------------------------------------------------------------

/**
 * One tile in the standard "slippy map" scheme used by OpenStreetMap and almost everything else:
 * the world is a square divided into `2^zoom` columns and rows, in Web Mercator projection.
 *
 * @param zoom 0 (whole world in one tile) to 19 (building level).
 * @param x column, `0..2^zoom - 1`, counted from the antimeridian eastwards.
 * @param y row, `0..2^zoom - 1`, counted from the north edge southwards.
 */
data class MapTile(val zoom: Int, val x: Int, val y: Int)

/**
 * Default zoom for the detail screen's location preview.
 *
 * 15 shows a neighbourhood: streets are named and the surroundings are recognisable, without the
 * tile becoming a privacy problem by pinpointing a doorway. A capture is a place someone stood, and
 * the feed shows other people's captures.
 */
const val LOCATION_PREVIEW_ZOOM = 15

/** Web Mercator cannot express the poles; it is cut at this latitude, which is where the square ends. */
private const val MERCATOR_LATITUDE_LIMIT = 85.05112878

/** OSM serves up to z19. Beyond that the request 404s, which would show as a broken tile. */
private const val MAX_ZOOM = 19

/**
 * The tile containing ([latitude], [longitude]) at [zoom].
 *
 * Total function: every input produces a tile that actually exists on the server.
 * - [zoom] is clamped to `0..`[MAX_ZOOM].
 * - Latitude is clamped to the Mercator limit, so a polar fix yields the top or bottom row instead
 *   of `NaN` from `tan(±90°)`.
 * - Longitude is clamped to `-180..180`, and the resulting column is clamped into range — exactly
 *   `+180` would otherwise land one column past the east edge (`x == 2^zoom`), a URL that 404s.
 *
 * This matters more than it looks: the caller is a Compose screen rendering live backend data, and
 * a thrown exception or a `NaN` here would take the whole capture-detail screen down over a bad
 * coordinate pair, when the honest degradation is "the map looks wrong but the capture still opens".
 */
fun mapTileFor(latitude: Double, longitude: Double, zoom: Int = LOCATION_PREVIEW_ZOOM): MapTile {
    val safeZoom = zoom.coerceIn(0, MAX_ZOOM)
    val tiles = 1 shl safeZoom
    val lastTile = tiles - 1

    val safeLatitude = latitude.coerceIn(-MERCATOR_LATITUDE_LIMIT, MERCATOR_LATITUDE_LIMIT)
    val safeLongitude = longitude.coerceIn(-180.0, 180.0)

    val x = floor((safeLongitude + 180.0) / 360.0 * tiles).toInt().coerceIn(0, lastTile)

    // The Mercator y: `asinh(tan(lat))` is the numerically stable spelling of the textbook
    // `ln(tan(lat) + sec(lat))`, which loses precision near the equator.
    val latitudeRadians = safeLatitude * PI / 180.0
    val y = floor((1.0 - asinh(tan(latitudeRadians)) / PI) / 2.0 * tiles)
        .toInt()
        .coerceIn(0, lastTile)

    return MapTile(safeZoom, x, y)
}

/**
 * The public URL of this tile.
 *
 * `tile.openstreetmap.org` (the load-balanced hostname) rather than one of the `a/b/c.` subdomains:
 * those are deprecated and HTTP/2 makes them pointless anyway.
 */
fun MapTile.osmTileUrl(): String = "https://tile.openstreetmap.org/$zoom/$x/$y.png"

// ---------------------------------------------------------------------------------------------
// Human-readable coordinates
// ---------------------------------------------------------------------------------------------

/** Four decimals is ~11m — enough to identify the spot, short enough to read at caption size. */
private const val COORDINATE_DECIMALS = 4

private val PT_BR = Locale.forLanguageTag("pt-BR")

/**
 * The coordinates as a person reads them, in pt-BR: `23,5500° S · 46,6300° O`.
 *
 * Two decisions worth keeping:
 *
 * 1. **Hemisphere letters instead of signs.** `-23,55, -46,63` is ambiguous in a locale whose
 *    decimal separator *is* the comma — the string contains three commas and two of them mean
 *    different things. N/S and L/O (Leste/Oeste) remove the ambiguity entirely.
 * 2. **pt-BR number formatting here, `Locale.ROOT` in [geoUri].** The user reads `23,5500`; the
 *    map app must receive `-23.5500`. Formatting both with the device locale is the classic bug
 *    that makes `geo:` links silently open the wrong place (or nothing) on a Brazilian phone.
 */
fun formatCoordinates(latitude: Double, longitude: Double): String {
    val northSouth = if (latitude < 0) "S" else "N"
    val eastWest = if (longitude < 0) "O" else "L"
    val lat = String.format(PT_BR, "%.${COORDINATE_DECIMALS}f", kotlin.math.abs(latitude))
    val lon = String.format(PT_BR, "%.${COORDINATE_DECIMALS}f", kotlin.math.abs(longitude))
    return "$lat° $northSouth · $lon° $eastWest"
}

/**
 * A `geo:` URI for the phone's own map application.
 *
 * The shape is `geo:<lat>,<lon>?q=<lat>,<lon>(<label>)`. The bare `geo:lat,lon` prefix is what
 * every handler understands; the `q=` parameter is what makes a pin appear with a name rather than
 * just centring the camera, and handlers that do not support it ignore it.
 *
 * Numbers are formatted with [Locale.ROOT] — see [formatCoordinates] for why that is not an
 * oversight. The label is percent-encoded because capture titles are free text from users.
 */
fun geoUri(latitude: Double, longitude: Double, label: String? = null): String {
    val lat = String.format(Locale.ROOT, "%.${GEO_DECIMALS}f", latitude)
    val lon = String.format(Locale.ROOT, "%.${GEO_DECIMALS}f", longitude)
    val point = "$lat,$lon"

    val trimmedLabel = label?.trim().orEmpty()
    if (trimmedLabel.isEmpty()) return "geo:$point?q=$point"

    // `URLEncoder` is form encoding, so it turns a space into `+`. In a URI query a literal `+`
    // reads as a space to some parsers and as a plus to others; `%20` reads as a space to all.
    val encoded = URLEncoder.encode(trimmedLabel, "UTF-8").replace("+", "%20")
    return "geo:$point?q=$point($encoded)"
}

/** Six decimals is ~0.1m — more than the fix is worth, but it is what map apps expect to parse. */
private const val GEO_DECIMALS = 6

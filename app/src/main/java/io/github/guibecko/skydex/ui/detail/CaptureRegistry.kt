package io.github.guibecko.skydex.ui.detail

import io.github.guibecko.skydex.data.remote.dto.WeatherEventResponse

/**
 * # How the detail screen gets its capture, and why it is not a network call
 *
 * `SkyDexApi` has **no single-capture endpoint**. It exposes `GET api/events/mine` and the paged
 * `GET api/feed`, both of which return full [WeatherEventResponse] objects — id, title, description,
 * photo, timestamp, coordinates, author, phenomenon, rarity, validation status and XP. There is no
 * `GET api/events/{id}` to add a call to, and inventing one on the client (or asking for it on the
 * server) would buy nothing: **the list item the user tapped already holds every field the detail
 * screen renders.**
 *
 * So the detail route does not load anything. The list hands the object it already has to this
 * registry on the way out, and the detail screen reads it back by id.
 *
 * ## Why a registry rather than a nav argument
 *
 * The alternative is serialising the whole [WeatherEventResponse] into the route. That was rejected:
 * a capture's `description` is free text and its `photoUrl` is a URL, both of which have to survive
 * two rounds of URI encoding inside a route string, and the resulting route is long enough to be
 * awkward in logs and in the back stack. Passing an id and resolving it keeps the route readable and
 * keeps exactly one copy of the data.
 *
 * ## The tradeoff this makes, stated plainly
 *
 * This is **in-memory only**. It does not survive process death, and it is not supposed to:
 *
 * - **Config change (rotation), backgrounding, navigating back and forth** — survives. The registry
 *   lives in `ServiceLocator` for the life of the process, and none of those recreate it.
 * - **Process death with the detail screen on top, then restore** — Android rebuilds the back stack
 *   from the saved route, so the detail screen is recomposed with a valid id and an **empty**
 *   registry. It cannot resolve, and there is no endpoint to fall back to.
 *
 * That second case is real and the screen handles it explicitly: [CaptureDetailViewModel] publishes
 * `UiState.Error` and `CaptureDetailScreen` draws a full-area `SkyDexNoticeState` whose action is
 * "Voltar". The user lands on a screen that says what happened and gets them back to the list in one
 * tap — never a blank screen, never a crash, and never an empty detail page pretending to be loading
 * something that will never arrive.
 *
 * **If a `GET api/events/{id}` is ever added to the backend, this is the seam to replace**: give
 * `CaptureDetailViewModel` a gateway, keep the registry as the fast path, and fall back to the call
 * on a miss instead of to the error state.
 *
 * ## Bounded on purpose
 *
 * A user scrolling a long feed would otherwise pin every capture they ever saw — photos are URLs, so
 * the objects are small, but "small × unbounded" is still a leak. The registry keeps the
 * [MAX_ENTRIES] most recently touched captures and drops the oldest, which is far more than the one
 * entry the detail screen ever needs.
 */
class CaptureRegistry(private val maxEntries: Int = MAX_ENTRIES) {

    /**
     * Access-ordered so a `get` counts as a use: `accessOrder = true` moves the read entry to the
     * end, and [removeEldestEntry] then evicts the genuinely least recently *used* one rather than
     * the least recently inserted.
     */
    private val entries = object : LinkedHashMap<String, WeatherEventResponse>(
        INITIAL_CAPACITY,
        LOAD_FACTOR,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, WeatherEventResponse>?
        ): Boolean = size > maxEntries
    }

    /**
     * Keep [capture] resolvable by its id.
     *
     * Called by the navigation graph at the moment a card is tapped, not by the list ViewModels: the
     * lists have no business knowing a detail screen exists, and stashing only the tapped capture
     * keeps the registry to the handful of entries that can actually be asked for.
     */
    @Synchronized
    fun remember(capture: WeatherEventResponse) {
        entries[capture.id] = capture
    }

    /** The capture, or `null` when this process has never seen it — see the class KDoc. */
    @Synchronized
    fun find(id: String): WeatherEventResponse? = entries[id]

    /** Visible for tests. Nothing in the app clears the registry; the process ending does that. */
    @Synchronized
    fun size(): Int = entries.size

    private companion object {
        /**
         * Comfortably more than the back stack can hold detail screens, so a "back, tap another,
         * back again" loop never evicts something still reachable.
         */
        const val MAX_ENTRIES = 64
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}

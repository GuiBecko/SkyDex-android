package io.github.guibecko.skydex.ui.navigation

import io.github.guibecko.skydex.ui.detail.CaptureOrigin

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val MY_CAPTURES = "my_captures"
    const val CAPTURE = "capture"
    const val SKYDEX = "skydex"
    const val FEED = "feed"
    const val FRIENDS = "friends"
    const val PROFILE = "profile"

    /** Nav argument names for [CAPTURE_DETAIL]. Declared once so the pattern and the reads agree. */
    const val ARG_CAPTURE_ID = "captureId"
    const val ARG_ORIGIN = "origin"

    /**
     * The capture-detail **pattern**, with its placeholders.
     *
     * This is the string `NavHost` registers and the string `NavDestination.route` reports back, so
     * it — not a filled-in address — is what keys the top-bar map in `SkyDexNavHost`. Build a
     * navigable address with [captureDetail].
     *
     * The route carries the capture's **id**, not the capture. `SkyDexApi` has no
     * `GET api/events/{id}`, so the object itself is handed over in memory through `CaptureRegistry`
     * — see its KDoc for what that costs after process death and why serialising the whole
     * `WeatherEventResponse` into the route was rejected.
     */
    const val CAPTURE_DETAIL = "capture_detail/{$ARG_CAPTURE_ID}/{$ARG_ORIGIN}"

    /**
     * The navigable address of one capture's detail page.
     *
     * @param captureId the backend id. A UUID, so it needs no escaping; if ids ever become free
     *   text, this is the one place that would have to start encoding them.
     * @param origin whether the user came from Meus Registros or the Feed. Part of the route rather
     *   than of the registry because it belongs to the *navigation*, not to the capture: the same
     *   capture opened from two places is legitimately two different pages.
     */
    fun captureDetail(captureId: String, origin: CaptureOrigin): String =
        "capture_detail/$captureId/${origin.name}"
}

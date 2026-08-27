package io.github.guibecko.skydex

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

/**
 * Application entry point: wires the dependency container, and supplies the app-wide Coil
 * [ImageLoader].
 */
class SkyDexApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }

    /**
     * The image loader every `CaptureImage` in the app goes through.
     *
     * It exists for one reason: **identifying the client to OpenStreetMap.** The capture-detail
     * screen draws its location preview as a raster tile from `tile.openstreetmap.org` (see
     * `ui/detail/CaptureLocationCard`), and OSM's tile usage policy requires a real, identifying
     * `User-Agent` — a generic HTTP-library default is explicitly listed as grounds for being
     * blocked, and that block would land on every SkyDex user at once.
     *
     * Setting it here rather than per request is deliberate: a header attached to one `ImageRequest`
     * is a header the next person to add a tile call will forget. This is a property of how the app
     * talks to the network, so it lives with the client.
     *
     * The `User-Agent` goes on every image request, including capture photos from our own backend,
     * which is harmless and mildly useful in server logs. The `Referer` is added **only** for OSM,
     * because it is their attribution/abuse signal and means nothing to anyone else.
     *
     * Note this is a **separate** OkHttp client from `ApiFactory`'s. That one carries the bearer
     * token through `AuthInterceptor`; sharing it would attach the user's credential to third-party
     * tile requests, which is exactly the kind of leak nobody notices until it is in someone else's
     * access log.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val identified = request.newBuilder()
                        .header("User-Agent", TILE_USER_AGENT)
                        .apply {
                            if (request.url.host == OSM_TILE_HOST) {
                                header("Referer", TILE_REFERER)
                            }
                        }
                        .build()
                    chain.proceed(identified)
                }
                .build()
        }
        .build()

    private companion object {
        const val OSM_TILE_HOST = "tile.openstreetmap.org"

        /**
         * Application name, version and the application id — enough for OSM to identify and, if it
         * ever comes to it, contact the client rather than silently blocking it.
         */
        const val TILE_USER_AGENT = "SkyDex/1.0 (Android; ${BuildConfig.APPLICATION_ID})"

        /**
         * OSM's tile usage policy wants a requester it can identify and, if it ever needs to,
         * contact. This used to be `https://skydex.app/`, a domain nobody involved owns, which
         * identified nothing. The repository URL is a real page describing a real project with a
         * real way to reach its author.
         */
        const val TILE_REFERER = "https://github.com/GuiBecko/SkyDex-android"
    }
}

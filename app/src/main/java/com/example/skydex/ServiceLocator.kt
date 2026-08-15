package com.example.skydex

import android.content.Context
import com.example.skydex.data.remote.ApiFactory
import com.example.skydex.data.remote.AuthInterceptor
import com.example.skydex.data.remote.SkyDexApi
import com.example.skydex.data.repository.AuthRepository
import com.example.skydex.data.repository.CaptureRepository
import com.example.skydex.data.repository.ProfileRepository
import com.example.skydex.data.repository.SkyDexRepository
import com.example.skydex.data.repository.SocialRepository
import com.example.skydex.data.session.SessionStore
import com.example.skydex.ui.detail.CaptureRegistry
import com.example.skydex.util.DeviceLocation

/**
 * Hand-rolled dependency graph. The app is small enough that a DI framework would cost more
 * than it saves; if this grows past a dozen entries, replace it with Hilt.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * A bare `UninitializedPropertyAccessException` from a `lateinit` gives no hint about what
     * went wrong. `SkyDexApplication.onCreate` covers every UI path, but a ContentProvider —
     * `androidx.startup`, or any library that installs one — runs *before* it, so this is
     * reachable. Say so plainly rather than leaving a mystery crash.
     */
    private fun requireContext(): Context {
        check(::appContext.isInitialized) {
            "ServiceLocator.init() was never called — is SkyDexApplication registered in the manifest?"
        }
        return appContext
    }

    val sessionStore: SessionStore by lazy { SessionStore(requireContext()) }

    val api: SkyDexApi by lazy {
        ApiFactory.create(BuildConfig.BASE_URL, AuthInterceptor { sessionStore.blockingToken() })
    }

    val authRepository: AuthRepository by lazy { AuthRepository(api, sessionStore) }

    val captureRepository: CaptureRepository by lazy { CaptureRepository(api) }

    val skyDexRepository: SkyDexRepository by lazy { SkyDexRepository(api) }

    val socialRepository: SocialRepository by lazy { SocialRepository(api) }

    val profileRepository: ProfileRepository by lazy { ProfileRepository(api) }

    val deviceLocation: DeviceLocation by lazy { DeviceLocation(requireContext()) }

    /**
     * The in-memory handoff between a capture list and the capture-detail screen.
     *
     * The odd one out in this container: it is not a repository and it talks to nothing. It exists
     * because `SkyDexApi` has no single-capture endpoint, so the detail screen resolves the object
     * the list already loaded instead of fetching it — see [CaptureRegistry] for the full reasoning
     * and for what happens after process death. It lives here for exactly one property: **process
     * lifetime**, which is the same scope the registry's contract is written against.
     */
    val captureRegistry: CaptureRegistry by lazy { CaptureRegistry() }
}

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
}

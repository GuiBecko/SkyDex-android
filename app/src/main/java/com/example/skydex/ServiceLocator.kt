package com.example.skydex

import android.content.Context
import com.example.skydex.data.remote.ApiFactory
import com.example.skydex.data.remote.AuthInterceptor
import com.example.skydex.data.remote.SkyDexApi
import com.example.skydex.data.repository.AuthRepository
import com.example.skydex.data.session.SessionStore

/**
 * Hand-rolled dependency graph. The app is small enough that a DI framework would cost more
 * than it saves; if this grows past a dozen entries, replace it with Hilt.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val sessionStore: SessionStore by lazy { SessionStore(appContext) }

    val api: SkyDexApi by lazy {
        ApiFactory.create(AuthInterceptor { sessionStore.blockingToken() })
    }

    val authRepository: AuthRepository by lazy { AuthRepository(api, sessionStore) }
}

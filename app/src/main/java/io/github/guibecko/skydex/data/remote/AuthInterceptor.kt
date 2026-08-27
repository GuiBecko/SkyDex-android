package io.github.guibecko.skydex.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the stored JWT to every outgoing request. The token is read lazily through
 * [tokenProvider] so a login or logout takes effect on the very next call.
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return chain.proceed(chain.request())

        val value = if (token.startsWith("Bearer ")) token else "Bearer $token"
        val authorized = chain.request().newBuilder()
            .header("Authorization", value)
            .build()
        return chain.proceed(authorized)
    }
}

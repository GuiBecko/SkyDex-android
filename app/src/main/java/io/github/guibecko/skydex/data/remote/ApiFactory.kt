package io.github.guibecko.skydex.data.remote

import io.github.guibecko.skydex.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {

    /**
     * Takes the base URL instead of reading [BuildConfig] internally so a test can point it at a
     * fake host and actually execute this function. Reading the constant here would make the
     * whole factory — including the trailing-slash fix-up below, without which Retrofit throws
     * `IllegalArgumentException: baseUrl must end in /` — unreachable from any JVM test.
     */
    fun create(baseUrl: String, interceptor: AuthInterceptor): SkyDexApi {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
            // BODY logging prints request headers, and the bearer token is a live credential:
            // without this it lands in `adb logcat` and in every captured bug report. Redaction
            // still shows the header was present, which is what device debugging actually needs.
            redactHeader("Authorization")
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // connect/read timeouts do NOT cover time spent inside an application interceptor,
            // and AuthInterceptor blocks on a DataStore read. Without a call timeout, a wedged
            // disk read hangs the request forever.
            .callTimeout(45, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SkyDexApi::class.java)
    }
}

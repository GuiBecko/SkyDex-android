package com.example.skydex

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object RetrofitClient {

    // Configura o interceptor para mostrar o corpo e os cabeçalhos no Logcat
    private val clienteOkHttp: OkHttpClient by lazy {
        val interceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    val api: SkyDexApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(clienteOkHttp) // Adiciona o cliente ao Retrofit
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SkyDexApi::class.java)
    }
}
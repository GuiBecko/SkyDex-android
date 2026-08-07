package com.example.skydex.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Drives [SkyDexApi] through a real OkHttp/Retrofit stack whose last interceptor answers with a
 * canned response instead of opening a socket. This covers the declaration of the interface
 * itself — the part that only fails at runtime — without needing a server.
 */
class SkyDexApiTest {

    private val sentRequests = mutableListOf<Request>()

    private fun apiAnswering(code: Int, body: String, token: String?): SkyDexApi {
        val canned = Interceptor { chain ->
            sentRequests += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("canned")
                .body(body.toResponseBody(null))
                .build()
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { token })
            .addInterceptor(canned)
            .build()
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SkyDexApi::class.java)
    }

    @Test
    fun `deleting a capture succeeds on the backend's empty 204 response`() {
        val api = apiAnswering(code = 204, body = "", token = "abc123")

        val response = runBlocking { api.deleteCapture("11111111-2222-3333-4444-555555555555") }

        assertTrue(response.isSuccessful)
        assertEquals(
            "http://localhost/api/events/11111111-2222-3333-4444-555555555555",
            sentRequests.single().url.toString()
        )
    }

    @Test
    fun `authenticated calls carry the bearer token all the way to the wire`() {
        val api = apiAnswering(code = 200, body = "[]", token = "abc123")

        runBlocking { api.myCaptures() }

        assertEquals("Bearer abc123", sentRequests.single().header("Authorization"))
        assertEquals("http://localhost/api/events/mine", sentRequests.single().url.toString())
    }

    @Test
    fun `login is sent unauthenticated to the auth route`() {
        val api = apiAnswering(
            code = 200,
            body = """{"token":"t","userId":"u","name":"Ana"}""",
            token = null
        )

        val body = runBlocking { api.login(com.example.skydex.data.remote.dto.LoginRequest("a@b.com", "secret12")) }

        assertEquals("t", body.token)
        assertEquals("http://localhost/auth/login", sentRequests.single().url.toString())
    }
}

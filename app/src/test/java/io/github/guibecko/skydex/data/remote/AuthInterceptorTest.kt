package io.github.guibecko.skydex.data.remote

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthInterceptorTest {

    private fun runWith(token: String?): Request {
        var seen: Request? = null
        val interceptor = AuthInterceptor { token }
        val chain = object : Interceptor.Chain {
            private val original = Request.Builder().url("http://localhost/api/events/mine").build()
            override fun request(): Request = original
            override fun proceed(request: Request): Response {
                seen = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody(null))
                    .build()
            }
            override fun connection() = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 0
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 0
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }
        interceptor.intercept(chain)
        return seen!!
    }

    @Test
    fun `attaches a bearer header when a token is available`() {
        assertEquals("Bearer abc123", runWith("abc123").header("Authorization"))
    }

    @Test
    fun `sends no authorization header when logged out`() {
        assertNull(runWith(null).header("Authorization"))
    }

    @Test
    fun `does not double-prefix a token that already says Bearer`() {
        assertEquals("Bearer abc123", runWith("Bearer abc123").header("Authorization"))
    }
}

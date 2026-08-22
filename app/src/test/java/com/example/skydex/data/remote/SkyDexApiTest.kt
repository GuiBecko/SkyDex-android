package com.example.skydex.data.remote

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Drives [SkyDexApi] through the real [ApiFactory] against a throwaway loopback HTTP server from
 * the JDK. Going through the factory rather than hand-rolling a Retrofit instance is deliberate:
 * the factory's trailing-slash fix-up is load-bearing (Retrofit throws
 * `IllegalArgumentException: baseUrl must end in /` without it) and the configured fallback base
 * URL has no trailing slash, so it must actually be executed somewhere. Every test here points the
 * factory at a base URL *without* a trailing slash.
 */
class SkyDexApiTest {

    data class Recorded(val method: String, val target: String, val authorization: String?)

    private lateinit var server: HttpServer
    private val received = mutableListOf<Recorded>()

    /** Set per test before the call is made. */
    private var status = 200
    private var responseBody = ""

    /** Base URL with **no** trailing slash — exactly the shape `BuildConfig.BASE_URL` has. */
    private lateinit var baseUrl: String

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            received += Recorded(
                method = exchange.requestMethod,
                target = exchange.requestURI.toString(),
                authorization = exchange.requestHeaders.getFirst("Authorization")
            )
            val bytes = responseBody.toByteArray()
            if (bytes.isEmpty()) {
                exchange.sendResponseHeaders(status, -1)
            } else {
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        baseUrl = "http://${server.address.hostString}:${server.address.port}"
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    private fun apiAnswering(
        code: Int,
        body: String,
        token: String?,
        base: String = baseUrl
    ): SkyDexApi {
        status = code
        responseBody = body
        return ApiFactory.create(base, AuthInterceptor { token })
    }

    /**
     * Pins `ApiFactory`'s trailing-slash fix-up. Retrofit rejects a base URL whose last path
     * segment is non-empty (`IllegalArgumentException: baseUrl must end in /`), so without the
     * fix-up an `API_BASE_URL` of `http://host:8080/skydex` — an ordinary value for a backend
     * behind a path prefix — blows up inside `ApiFactory.create`, on the first call the app ever
     * makes. The base URL has to carry a path segment for this to bite: a bare `http://host:port`
     * is normalised by OkHttp to a single empty path segment and passes Retrofit's check either
     * way, which is why the bare-host case below does *not* pin the fix-up.
     */
    @Test
    fun `a base url with a path prefix and no trailing slash still resolves relative paths`() {
        val base = "$baseUrl/skydex"
        assertFalse("the fixture must exercise the trailing-slash fix-up", base.endsWith("/"))

        val api = apiAnswering(code = 200, body = "[]", token = null, base = base)
        runBlocking { api.myCaptures() }

        assertEquals("/skydex/api/events/mine", received.single().target)
    }

    @Test
    fun `a base url that already ends in a slash is not given a second one`() {
        val api = apiAnswering(code = 200, body = "[]", token = null, base = "$baseUrl/skydex/")

        runBlocking { api.myCaptures() }

        assertEquals("/skydex/api/events/mine", received.single().target)
    }

    /** The shape `BuildConfig.BASE_URL` actually has today: bare host and port, no trailing slash. */
    @Test
    fun `a bare host base url resolves relative paths`() {
        assertFalse(baseUrl.endsWith("/"))
        val api = apiAnswering(code = 200, body = "[]", token = null)

        runBlocking { api.myCaptures() }

        assertEquals("/api/events/mine", received.single().target)
    }

    @Test
    fun `deleting a capture succeeds on the backend's empty 204 response`() {
        val api = apiAnswering(code = 204, body = "", token = "abc123")

        val response = runBlocking { api.deleteCapture("11111111-2222-3333-4444-555555555555") }

        assertTrue(response.isSuccessful)
        assertEquals(204, response.code())
        assertEquals("DELETE", received.single().method)
        assertEquals("/api/events/11111111-2222-3333-4444-555555555555", received.single().target)
    }

    /**
     * `deleteCapture` is the one method on [SkyDexApi] that returns the raw [retrofit2.Response]
     * instead of a body, so — unlike every other method — a non-2xx status is an ordinary value
     * rather than an exception. Callers must branch on `isSuccessful`; nothing else pins that.
     */
    @Test
    fun `deleting someone else's capture returns 403 as a value instead of throwing`() {
        val api = apiAnswering(
            code = 403,
            body = """{"error":"you can only delete your own captures"}""",
            token = "abc123"
        )

        val response = runBlocking { api.deleteCapture("11111111-2222-3333-4444-555555555555") }

        assertFalse(response.isSuccessful)
        assertEquals(403, response.code())
        assertNull(response.body())
    }

    /**
     * The second endpoint that answers 204, and the one where the empty-body trap actually shipped.
     *
     * `declineFriendRequest` was declared as returning `Unit`, which reads like "there is no body
     * to parse" but does not behave like it: Retrofit 2.9.0 short-circuits a 204 to a null body and
     * then rejects that null against a non-null return type. The call therefore threw
     * KotlinNullPointerException on the **success** path — the request really had been deleted —
     * and the UI told the user it had failed.
     */
    @Test
    fun `declining a friend request succeeds on the backend's empty 204 response`() {
        val api = apiAnswering(code = 204, body = "", token = "abc123")

        val response = runBlocking {
            api.declineFriendRequest("11111111-2222-3333-4444-555555555555")
        }

        assertTrue(response.isSuccessful)
        assertEquals(204, response.code())
        assertEquals("DELETE", received.single().method)
        assertEquals(
            "/api/friends/requests/11111111-2222-3333-4444-555555555555",
            received.single().target
        )
    }

    @Test
    fun `the pending count is asked at its own route and unwrapped`() {
        val api = apiAnswering(code = 200, body = """{"count":7}""", token = "abc123")

        val body = runBlocking { api.pendingFriendRequestCount() }

        assertEquals(7, body.count)
        // Pinned because the badge asks for this on every navigation and a wrong path fails
        // silently: the store swallows the failure and keeps its last count, so the dot would just
        // stop updating with nothing in the logs.
        assertEquals("/api/friends/requests/count", received.single().target)
        assertEquals("GET", received.single().method)
    }

    /**
     * `friendshipId` and `userId` are both UUIDs in the same object, and only one of them names a
     * row the delete route can find. Nothing downstream can tell them apart, so this pins the
     * mapping at the wire: swap the two names in the backend and this fails instead of the app
     * quietly 404ing every unfriend.
     */
    @Test
    fun `the friends list keeps the friendship id and the user id apart`() {
        val api = apiAnswering(
            code = 200,
            body = """[{"friendshipId":"f-1","userId":"u-1","name":"Alice",""" +
                """"email":"alice@skydex.com","friendsSince":"2026-08-01T10:00:00Z"}]""",
            token = "abc123"
        )

        val friend = runBlocking { api.friends() }.single()

        assertEquals("f-1", friend.friendshipId)
        assertEquals("u-1", friend.userId)
    }

    @Test
    fun `authenticated calls carry the bearer token all the way to the wire`() {
        val api = apiAnswering(code = 200, body = "[]", token = "abc123")

        runBlocking { api.myCaptures() }

        assertEquals("Bearer abc123", received.single().authorization)
        assertEquals("/api/events/mine", received.single().target)
    }

    @Test
    fun `login is sent unauthenticated to the auth route`() {
        val api = apiAnswering(
            code = 200,
            body = """{"token":"t","userId":"u","name":"Ana"}""",
            token = null
        )

        val body = runBlocking {
            api.login(com.example.skydex.data.remote.dto.LoginRequest("a@b.com", "secret12"))
        }

        assertEquals("t", body.token)
        assertEquals("POST", received.single().method)
        assertEquals("/auth/login", received.single().target)
        assertNull(received.single().authorization)
    }

    /**
     * BODY-level logging prints request headers, and the bearer token is a live credential: without
     * `redactHeader("Authorization")` it lands in `adb logcat` and in every captured bug report.
     * `HttpLoggingInterceptor`'s default logger goes through OkHttp's platform log, which on the
     * JVM is `java.util.logging` under the `okhttp3.OkHttpClient` name — so the log lines the app
     * would emit on a device can be captured here.
     */
    @Test
    fun `the bearer token is redacted out of the http log`() {
        assumeTrue("logging is only at BODY level in debug builds", com.example.skydex.BuildConfig.DEBUG)

        val logger = Logger.getLogger("okhttp3.OkHttpClient")
        val lines = mutableListOf<String>()
        val handler = object : Handler() {
            override fun publish(record: LogRecord) { lines += record.message }
            override fun flush() = Unit
            override fun close() = Unit
        }
        val previousLevel = logger.level
        logger.level = Level.ALL
        logger.addHandler(handler)
        try {
            val api = apiAnswering(code = 200, body = "[]", token = "super-secret-jwt")
            runBlocking { api.myCaptures() }
        } finally {
            logger.removeHandler(handler)
            logger.level = previousLevel
        }

        val log = lines.joinToString("\n")
        assertFalse("the raw token must never reach the log", log.contains("super-secret-jwt"))
        assertTrue(
            "the header should still be visible as present, just redacted:\n$log",
            log.contains("Authorization: ██")
        )
        // Guards the premise: if logging were off entirely the assertion above would pass vacuously.
        assertTrue("expected BODY-level logging to have produced output", lines.isNotEmpty())
    }

    /**
     * Retrofit only validates a method's annotations when that method is first called, so nothing
     * else pins that `uploadPhoto` is a well-formed `@Multipart` POST: a mismatch between
     * `@Multipart` and `@Part` throws at call time, not at interface-creation time.
     */
    @Test
    fun `uploading a photo posts to the photos route and reads back the stored url`() {
        val api = apiAnswering(
            code = 201,
            body = """{"photoUrl":"/api/photos/abc.jpg"}""",
            token = "abc123"
        )
        val part = MultipartBody.Part.createFormData(
            "file", "storm.jpg", "bytes".toRequestBody("image/jpeg".toMediaType())
        )

        val body = runBlocking { api.uploadPhoto(part) }

        // The backend returns this relative, deliberately: the app persists exactly what it is
        // given, so no host ever gets frozen into a stored capture.
        assertEquals("/api/photos/abc.jpg", body.photoUrl)
        assertEquals("POST", received.single().method)
        assertEquals("/api/photos", received.single().target)
        assertEquals("Bearer abc123", received.single().authorization)
    }

    @Test
    fun `nearby phenomena are requested with lat and lon query parameters`() {
        val api = apiAnswering(code = 200, body = "[]", token = "abc123")

        runBlocking { api.nearbyPhenomena(-23.5, -46.6) }

        assertEquals("/api/weather/nearby?lat=-23.5&lon=-46.6", received.single().target)
    }
}

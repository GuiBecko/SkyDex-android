package com.example.skydex.ui.common

import com.example.skydex.data.repository.backendErrorMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The guarantees `ErrorPresenter` exists to hold.
 *
 * These are not copy tests. Wording is allowed to change; what is asserted here is *behaviour*:
 * transport is separated from rejection, every screen gets an actionable next step, and no string
 * the backend wrote is ever handed to the UI.
 */
class ErrorPresenterTest {

    // ------------------------------------------------------------------------------------------
    // Transport is its own case
    // ------------------------------------------------------------------------------------------

    /**
     * Audit finding A6, in one assertion. "Credenciais inválidas ou servidor indisponível." asked
     * the user to do two contradictory things at once — retype, or wait. Offline and a 401 must
     * never produce the same message again.
     */
    @Test
    fun `offline and rejected credentials are different messages`() {
        val offline = UnknownHostException("api.skydex.com").toUiMessage(ErrorContext.LOGIN)
        val rejected = httpError(401, """{"error":"Invalid email or password"}""")
            .toUiMessage(ErrorContext.LOGIN)

        assertEquals("Sem conexão", offline.title)
        assertEquals("Verifique sua internet e tente de novo.", offline.body)

        assertEquals("E-mail ou senha não conferem", rejected.title)
        assertEquals("Confira os dados e tente de novo.", rejected.body)

        assertFalse("offline must not read as a credential failure", offline.title == rejected.title)
    }

    /** A plain I/O fault with no HTTP response behind it is the offline case, on every screen. */
    @Test
    fun `a transport failure is offline on every screen`() {
        ErrorContext.entries.forEach { context ->
            val message = IOException("socket closed").toUiMessage(context)
            assertEquals("$context should read as offline", "Sem conexão", message.title)
        }
    }

    /**
     * A timeout is neither offline nor a rejection: the connection worked, the answer did not
     * arrive. Waiting is the correct advice, and it is the wrong advice for the other two.
     */
    @Test
    fun `a timeout is its own case`() {
        val message = SocketTimeoutException("read timed out").toUiMessage(ErrorContext.FEED)

        assertEquals("A conexão demorou demais", message.title)
        assertEquals("Tente de novo em instantes.", message.body)
    }

    /** A timeout nested inside another throwable still classifies as a timeout. */
    @Test
    fun `a wrapped timeout is still a timeout`() {
        val message = IllegalStateException("call failed", SocketTimeoutException("timeout"))
            .toUiMessage(ErrorContext.FEED)

        assertEquals("A conexão demorou demais", message.title)
    }

    /**
     * A 5xx has to say it is ours. Without that line the user reads a server fault as their own
     * mistake and starts retyping a password that was always correct.
     */
    @Test
    fun `a 5xx says the fault is not the user's`() {
        val message = httpError(503, """{"error":"Service unavailable"}""")
            .toUiMessage(ErrorContext.LOGIN)

        assertEquals("O SkyDex está fora do ar", message.title)
        assertEquals("Não é você — estamos com problema. Tente de novo em instantes.", message.body)
        assertTrue("a 500 must not read as a bad password", message.body.contains("Não é você"))
    }

    // ------------------------------------------------------------------------------------------
    // Status + context
    // ------------------------------------------------------------------------------------------

    /**
     * The register 409. The backend answers `"Email already registered"`, so the app has no reason
     * to ask *"O e-mail já existe?"* — which is exactly what it used to do (finding A6).
     */
    @Test
    fun `a 409 on register states that the e-mail is taken`() {
        val message = httpError(409, """{"error":"Email already registered"}""")
            .toUiMessage(ErrorContext.REGISTER)

        assertEquals("Este e-mail já tem uma conta", message.title)
        assertEquals("Faça login ou use outro e-mail.", message.body)
        assertFalse("the copy states, it does not ask", message.body.contains("?"))
    }

    /** The same 409 on Amigos means something else entirely. */
    @Test
    fun `a 409 on friends reads as already connected`() {
        val message =
            httpError(409, """{"error":"You already have a pending or accepted request with this user"}""")
                .toUiMessage(ErrorContext.FRIENDS)

        assertEquals("Vocês já estão conectados", message.title)
    }

    /** One status, two screens, two different next steps. */
    @Test
    fun `a 404 reads differently on friends and on my captures`() {
        val onFriends = httpError(404, """{"error":"No user with that email"}""")
            .toUiMessage(ErrorContext.FRIENDS)
        val onCaptures = httpError(404, """{"error":"Capture not found"}""")
            .toUiMessage(ErrorContext.MY_CAPTURES)

        assertEquals("Não encontramos esse e-mail", onFriends.title)
        assertEquals("Esse registro não existe mais", onCaptures.title)
    }

    @Test
    fun `a 413 names the photo`() {
        val message = httpError(413, """{"error":"Photo is too large"}""")
            .toUiMessage(ErrorContext.PHOTO_UPLOAD)

        assertEquals("Essa foto é pesada demais", message.title)
    }

    @Test
    fun `a 403 on friends explains you cannot invite yourself`() {
        val message = httpError(403, """{"error":"You cannot add yourself"}""")
            .toUiMessage(ErrorContext.FRIENDS)

        assertEquals("Esse e-mail é o seu", message.title)
    }

    // ------------------------------------------------------------------------------------------
    // The capture 400s — one per backend string, because each implies a different action
    // ------------------------------------------------------------------------------------------

    @Test
    fun `each known capture 400 gets its own message`() {
        val cases = mapOf(
            "This photo has already been used for a capture" to "Essa foto já virou um registro",
            "Photo has expired; take a new one" to "Essa foto expirou",
            "Unknown phenomenon: THUNDERSTORM" to "Não reconhecemos esse fenômeno",
            "File is not a JPEG image" to "Essa imagem não serve",
            "File is not a PNG image" to "Essa imagem não serve",
            "Only JPEG and PNG images are accepted" to "Essa imagem não serve",
            "Photo is not available for this capture" to "A foto desse registro não está disponível"
        )

        cases.forEach { (backend, expectedTitle) ->
            val message = httpError(400, """{"error":"$backend"}""")
                .toUiMessage(ErrorContext.CAPTURE_SAVE)
            assertEquals("for backend message: $backend", expectedTitle, message.title)
        }

        // And they are genuinely distinct, not four aliases of one string — collapsing them is the
        // regression this guards, since each one has a different way out.
        val titles = cases.values.toSet()
        assertEquals("the distinct 400s must stay distinct", 5, titles.size)
    }

    /**
     * The one the audit called out by name: the server appends the enum to the message, so
     * forwarding it printed `THUNDERSTORM` — an internal domain constant — at the user.
     */
    @Test
    fun `the unknown-phenomenon enum never reaches the message`() {
        val message = httpError(400, """{"error":"Unknown phenomenon: HAILSTORM"}""")
            .toUiMessage(ErrorContext.CAPTURE_SAVE)

        assertFalse(shown(message).contains("HAILSTORM"))
        assertFalse(shown(message).contains("Unknown"))
    }

    // ------------------------------------------------------------------------------------------
    // The rule that outranks all the others
    // ------------------------------------------------------------------------------------------

    /**
     * **No backend string is ever echoed.** Every observed backend message, on every context: the
     * English must not appear in what the user reads.
     *
     * This is the test that fails first if someone ever "helpfully" falls back to
     * `backendErrorMessage()` again — which is the shape audit finding B1 took.
     */
    @Test
    fun `no backend string is ever echoed to the user`() {
        BACKEND_MESSAGES.forEach { (status, backend) ->
            ErrorContext.entries.forEach { context ->
                val message = httpError(status, """{"error":"$backend"}""").toUiMessage(context)
                val shown = shown(message)

                assertFalse(
                    "$context echoed the backend message for $status: $backend",
                    shown.contains(backend)
                )
                // Not just the whole string — no meaningful English fragment of it either. Short
                // words are skipped because "a", "is" and "for" collide with nothing meaningful.
                backend.split(' ', ';', ':').filter { it.length > 5 }.forEach { word ->
                    assertFalse(
                        "$context leaked \"$word\" from the backend message for $status",
                        shown.contains(word, ignoreCase = false)
                    )
                }
            }
        }
    }

    /**
     * Every message is actionable and none is the old "erro desconhecido". Run across every status
     * the app can see — known, unknown and absent — on every screen.
     */
    @Test
    fun `every message has a title, a next step and no dead-end wording`() {
        val throwables = buildList<Throwable> {
            add(IOException("offline"))
            add(SocketTimeoutException("timeout"))
            add(IllegalStateException("parse bug"))
            listOf(400, 401, 403, 404, 409, 413, 418, 422, 500, 503).forEach {
                add(httpError(it, """{"error":"something"}"""))
            }
            // Bodies that are not the envelope at all: an HTML page from a proxy, and nothing.
            add(httpError(400, "<html><body>502 Bad Gateway</body></html>"))
            add(httpError(500, ""))
        }

        throwables.forEach { throwable ->
            ErrorContext.entries.forEach { context ->
                val message = throwable.toUiMessage(context)

                assertTrue("empty title for $context", message.title.isNotBlank())
                assertTrue("empty body for $context", message.body.isNotBlank())
                assertFalse(
                    "no message may be a dead end",
                    shown(message).lowercase().contains("desconhecid")
                )
                assertFalse(
                    "an HTTP status is an implementation detail",
                    Regex("\\b[45]\\d\\d\\b").containsMatchIn(shown(message))
                )
                assertFalse("no message shouts", shown(message).contains("Erro:"))
            }
        }
    }

    /**
     * There is no red in the error system. `Tone` has no `ERROR` member by construction, and every
     * failure resolves to `NOTICE` — never `SUCCESS`, which would make a failure look like a win.
     */
    @Test
    fun `every failure is a notice, never a success`() {
        listOf(
            IOException("offline"),
            SocketTimeoutException("timeout"),
            httpError(401, """{"error":"Invalid email or password"}"""),
            httpError(500, "")
        ).forEach { throwable ->
            ErrorContext.entries.forEach { context ->
                assertEquals(Tone.NOTICE, throwable.toUiMessage(context).tone)
            }
        }
    }

    /**
     * The read-once constraint from `ApiError.kt`: `ResponseBody.string()` closes the source, so a
     * second read yields nothing. `toUiMessage` therefore reads it exactly once and carries the
     * value — a version that read it per-branch would come up empty on the second read and quietly
     * degrade every specific message to the generic one.
     *
     * The second half of this test is the proof that the hazard is real rather than theoretical:
     * consume the body first, and the presenter *does* fall through to the generic message.
     */
    @Test
    fun `the error body is read at most once`() {
        val fresh = httpError(400, """{"error":"Photo has expired; take a new one"}""")
        assertEquals("Essa foto expirou", fresh.toUiMessage(ErrorContext.CAPTURE_SAVE).title)

        val consumed = httpError(400, """{"error":"Photo has expired; take a new one"}""")
        assertEquals("Photo has expired; take a new one", consumed.backendErrorMessage())
        assertEquals(
            "a consumed body cannot be read again — hence the single read inside the presenter",
            "Não deu para salvar esse registro",
            consumed.toUiMessage(ErrorContext.CAPTURE_SAVE).title
        )
    }

    // ------------------------------------------------------------------------------------------

    private fun shown(message: UiMessage): String =
        listOfNotNull(message.title, message.body, message.actionLabel).joinToString(" ")

    /** An [HttpException] shaped like a real one, carrying [body] as the error payload. */
    private fun httpError(code: Int, body: String): HttpException = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType()))
    )

    private companion object {
        /** Every failure string the backend is known to send, with the status it sends it on. */
        val BACKEND_MESSAGES = listOf(
            401 to "Invalid email or password",
            409 to "Email already registered",
            409 to "You already have a pending or accepted request with this user",
            409 to "That conflicts with a record that already exists",
            404 to "Capture not found",
            404 to "No user with that email",
            404 to "Friend request not found",
            403 to "This request is not yours",
            403 to "This request was not sent to you",
            403 to "You can only modify your own captures",
            403 to "You cannot add yourself",
            400 to "This photo has already been used for a capture",
            400 to "Photo has expired; take a new one",
            400 to "Unknown phenomenon: THUNDERSTORM",
            400 to "File is not a JPEG image",
            400 to "File is not a PNG image",
            400 to "Only JPEG and PNG images are accepted",
            400 to "Photo is not available for this capture",
            413 to "Photo is too large"
        )
    }
}

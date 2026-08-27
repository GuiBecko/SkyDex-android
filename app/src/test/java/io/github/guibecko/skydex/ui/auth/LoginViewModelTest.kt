package io.github.guibecko.skydex.ui.auth

import io.github.guibecko.skydex.ui.common.RecordingLogWarning
import io.github.guibecko.skydex.ui.common.Tone
import io.github.guibecko.skydex.ui.common.UiMessage
import io.github.guibecko.skydex.ui.common.noLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `successful login flips the state to LoggedIn`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(result = Result.success(Unit))
        val viewModel = LoginViewModel(repository, noLogging)

        viewModel.onEmailChanged("pilot@skydex.com")
        viewModel.onPasswordChanged("super-safe-password")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loggedIn)
        assertEquals(null, viewModel.state.value.errorMessage)
    }

    /**
     * A dropped connection now gets the *connection* message rather than a credentials one — the
     * two used to share a sentence, and they ask the user for opposite things (audit finding A6).
     * What has to stay true either way: the exact cause reaches logcat, and the message carrying
     * it must not include the e-mail, which would travel into every captured bug report.
     */
    @Test
    fun `a failed login surfaces a message and stays logged out`() = runTest(dispatcher) {
        val cause = IOException("boom")
        val repository = FakeAuthRepository(result = Result.failure(cause))
        val logWarning = RecordingLogWarning()
        val viewModel = LoginViewModel(repository, logWarning)

        viewModel.onEmailChanged("pilot@skydex.com")
        viewModel.onPasswordChanged("wrong-but-long-enough")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.loggedIn)
        assertEquals(
            UiMessage(
                title = "Sem conexão",
                body = "Verifique sua internet e tente de novo.",
                tone = Tone.NOTICE,
                actionLabel = "Tentar de novo"
            ),
            viewModel.state.value.errorMessage
        )

        val warning = logWarning.warnings.single()
        assertEquals(cause, warning.cause)
        assertFalse(
            "the e-mail is PII and must not reach logcat",
            warning.message.contains("pilot@skydex.com")
        )
    }

    /**
     * The 401 path, and the property about it that must survive every future copy edit: the
     * message says the *pair* does not match and never which half. Anything more specific turns
     * this form into an oracle for enumerating registered e-mail addresses — which is exactly why
     * the backend answers the same 401 for an unknown account and for a wrong password.
     */
    @Test
    fun `rejected credentials never reveal which half was wrong`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(
            result = Result.failure(
                HttpException(
                    Response.error<Any>(
                        401,
                        """{"error":"Invalid email or password"}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                )
            )
        )
        val viewModel = LoginViewModel(repository, noLogging)

        viewModel.onEmailChanged("pilot@skydex.com")
        viewModel.onPasswordChanged("wrong-but-long-enough")
        viewModel.submit()
        advanceUntilIdle()

        val message = viewModel.state.value.errorMessage!!
        assertEquals("E-mail ou senha não conferem", message.title)
        assertEquals("Confira os dados e tente de novo.", message.body)
        assertEquals(Tone.NOTICE, message.tone)

        val shown = "${message.title} ${message.body}".lowercase()
        assertFalse("must not blame the password alone", shown.contains("senha incorreta"))
        assertFalse("must not say the account is unknown", shown.contains("não existe"))
        assertFalse("must not echo the backend's English", shown.contains("invalid"))
    }

    @Test
    fun `submitting with a blank field does not call the repository`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(result = Result.success(Unit))
        val viewModel = LoginViewModel(repository, noLogging)

        viewModel.onEmailChanged("pilot@skydex.com")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.loginCalls)
        assertEquals("Preencha e-mail e senha", viewModel.state.value.errorMessage?.title)
    }

    /**
     * The button is disabled while `submitting` is true, so a stuck flag would lock the user out
     * of retrying after a failure.
     */
    @Test
    fun `the submitting flag is cleared once the call finishes`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(result = Result.failure(IOException("boom")))
        val viewModel = LoginViewModel(repository, noLogging)

        viewModel.onEmailChanged("pilot@skydex.com")
        viewModel.onPasswordChanged("wrong-but-long-enough")
        viewModel.submit()
        assertTrue("submit() should mark the form busy right away", viewModel.state.value.submitting)

        advanceUntilIdle()
        assertFalse(viewModel.state.value.submitting)
    }

    /**
     * Two taps land in the same frame more often than anyone expects. `enabled = !submitting` on
     * the button is a UI-level guard on a ViewModel-level invariant, and it does not recompose
     * fast enough to stop the second tap — so `submit()` has to be idempotent while in flight.
     */
    @Test
    fun `a second submit while one is in flight is ignored`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(result = Result.success(Unit))
        val viewModel = LoginViewModel(repository, noLogging)

        viewModel.onEmailChanged("pilot@skydex.com")
        viewModel.onPasswordChanged("super-safe-password")
        viewModel.submit()
        viewModel.submit()
        advanceUntilIdle()

        assertEquals("the second tap must not launch a second login", 1, repository.loginCalls)
        assertTrue(viewModel.state.value.loggedIn)
    }

    /** Typing again after a rejected attempt must clear the stale message. */
    @Test
    fun `editing a field clears the previous error`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(result = Result.success(Unit))
        val viewModel = LoginViewModel(repository, noLogging)

        viewModel.submit()
        advanceUntilIdle()
        assertEquals("Preencha e-mail e senha", viewModel.state.value.errorMessage?.title)

        viewModel.onEmailChanged("pilot@skydex.com")
        assertEquals(null, viewModel.state.value.errorMessage)
    }
}

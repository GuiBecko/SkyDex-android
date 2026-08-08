package com.example.skydex.ui.auth

import com.example.skydex.ui.common.RecordingLogWarning
import com.example.skydex.ui.common.noLogging
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun RegisterViewModel.fillIn(
        name: String = "Pilot",
        email: String = "pilot@skydex.com",
        password: String = "super-safe-password"
    ) {
        onNameChanged(name)
        onEmailChanged(email)
        onPasswordChanged(password)
    }

    @Test
    fun `a successful registration flips the state to registered`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(result = Result.success(Unit))
        val viewModel = RegisterViewModel(repository, noLogging)

        viewModel.fillIn()
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.registered)
        assertEquals(null, viewModel.state.value.errorMessage)
        assertEquals(
            Triple("Pilot", "pilot@skydex.com", "super-safe-password"),
            repository.lastRegistration
        )
    }

    @Test
    fun `a rejected registration surfaces a message and logs the cause without the e-mail`() = runTest(dispatcher) {
        val cause = IOException("boom")
        val repository = FakeAuthRepository(result = Result.failure(cause))
        val logWarning = RecordingLogWarning()
        val viewModel = RegisterViewModel(repository, logWarning)

        viewModel.fillIn()
        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.registered)
        assertEquals(
            "Não foi possível registrar. O e-mail já existe?",
            viewModel.state.value.errorMessage
        )

        val warning = logWarning.warnings.single()
        assertEquals(cause, warning.cause)
        assertFalse(
            "the e-mail is PII and must not reach logcat",
            warning.message.contains("pilot@skydex.com")
        )
    }

    @Test
    fun `submitting with a blank field does not call the repository`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(result = Result.success(Unit))
        val viewModel = RegisterViewModel(repository, noLogging)

        viewModel.fillIn(name = "")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.registerCalls)
        assertEquals("Preencha todos os campos.", viewModel.state.value.errorMessage)
    }

    /**
     * The backend rejects anything under 8 characters with a 400. Letting that round trip happen
     * used to render the generic "e-mail já existe?" copy, which points the user at the wrong
     * field entirely — so the rule is enforced here, with copy that names the real problem.
     */
    @Test
    fun `a password below the backend minimum is rejected without a round trip`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(result = Result.success(Unit))
        val viewModel = RegisterViewModel(repository, noLogging)

        viewModel.fillIn(password = "short")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.registerCalls)
        assertFalse(viewModel.state.value.registered)
        assertEquals(
            "A senha deve ter no mínimo 8 caracteres.",
            viewModel.state.value.errorMessage
        )
    }

    @Test
    fun `a password of exactly the minimum length is accepted`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(result = Result.success(Unit))
        val viewModel = RegisterViewModel(repository, noLogging)

        viewModel.fillIn(password = "8charact")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, repository.registerCalls)
        assertTrue(viewModel.state.value.registered)
    }

    /**
     * A double tap here would try to create the account twice — the second attempt coming back as
     * "e-mail já existe?" against the account the first one just made. The button being disabled
     * while `submitting` is a UI-level guard; the invariant belongs in the ViewModel.
     */
    @Test
    fun `a second submit while one is in flight is ignored`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(result = Result.success(Unit))
        val viewModel = RegisterViewModel(repository, noLogging)

        viewModel.fillIn()
        viewModel.submit()
        viewModel.submit()
        advanceUntilIdle()

        assertEquals("the second tap must not create a second account", 1, repository.registerCalls)
        assertTrue(viewModel.state.value.registered)
    }

    @Test
    fun `the submitting flag is cleared once the call finishes`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(result = Result.failure(IOException("boom")))
        val viewModel = RegisterViewModel(repository, noLogging)

        viewModel.fillIn()
        viewModel.submit()
        assertTrue(viewModel.state.value.submitting)

        advanceUntilIdle()
        assertFalse(viewModel.state.value.submitting)
    }
}

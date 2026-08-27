package io.github.guibecko.skydex.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.guibecko.skydex.data.repository.AuthGateway
import io.github.guibecko.skydex.ui.common.ErrorContext
import io.github.guibecko.skydex.ui.common.LogWarning
import io.github.guibecko.skydex.ui.common.Tone
import io.github.guibecko.skydex.ui.common.UiMessage
import io.github.guibecko.skydex.ui.common.androidLogWarning
import io.github.guibecko.skydex.ui.common.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val loggedIn: Boolean = false,
    val errorMessage: UiMessage? = null
)

/** Local validation: nothing was sent, so there is no throwable for `ErrorPresenter` to read. */
private val MissingCredentials = UiMessage(
    title = "Preencha e-mail e senha",
    body = "Os dois campos são necessários para entrar.",
    tone = Tone.NOTICE
)

/**
 * Takes its dependencies as constructor parameters rather than reaching for `ServiceLocator`, which
 * is what lets it be exercised on the JVM. Only the navigation graph knows about the container, and
 * it passes only the gateway — `logWarning` defaults to the real logcat call on device and is
 * replaced by tests so no Android stub is ever hit.
 */
class LoginViewModel(
    private val auth: AuthGateway,
    private val logWarning: LogWarning = androidLogWarning
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChanged(value: String) = _state.update { it.copy(email = value, errorMessage = null) }

    fun onPasswordChanged(value: String) = _state.update { it.copy(password = value, errorMessage = null) }

    fun submit() {
        val current = _state.value
        if (current.email.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(errorMessage = MissingCredentials) }
            return
        }

        // Re-entrancy is a ViewModel invariant, so it is guarded here and not only by the screen
        // disabling its button: two taps in the same frame would otherwise launch two logins.
        if (current.submitting) return

        _state.update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            val result = auth.login(current.email, current.password)
            val failure = result.exceptionOrNull()
            // The e-mail is deliberately left out of the message: it is the user's PII and it ends
            // up in any captured bug report, while adding nothing the throwable does not already say.
            failure?.let { logWarning(TAG, "login failed", it) }
            _state.update {
                if (failure == null) {
                    it.copy(submitting = false, loggedIn = true)
                } else {
                    // Still deliberately vague about *which* credential is wrong — copy that
                    // distinguished "wrong password" from "no such account" would let anyone
                    // enumerate registered e-mails through this form. The backend takes the same
                    // position, answering 401 "Invalid email or password" for both cases on
                    // purpose. That reasoning is unchanged and lives on in `ErrorPresenter`.
                    //
                    // What the presenter fixes is the *other* half of the old sentence: it folded
                    // a bad password together with the server being down ("Credenciais inválidas
                    // ou servidor indisponível.", audit finding A6), and those two ask the user for
                    // opposite things — retype vs wait. They are separate messages now. The real
                    // cause still goes to logcat above.
                    it.copy(
                        submitting = false,
                        errorMessage = failure.toUiMessage(ErrorContext.LOGIN)
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "LoginViewModel"
    }
}

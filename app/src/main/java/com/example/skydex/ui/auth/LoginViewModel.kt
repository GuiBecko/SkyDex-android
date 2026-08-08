package com.example.skydex.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.repository.AuthGateway
import com.example.skydex.ui.common.LogWarning
import com.example.skydex.ui.common.androidLogWarning
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
    val errorMessage: String? = null
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
            _state.update { it.copy(errorMessage = "Preencha e-mail e senha.") }
            return
        }

        // Re-entrancy is a ViewModel invariant, so it is guarded here and not only by the screen
        // disabling its button: two taps in the same frame would otherwise launch two logins.
        if (current.submitting) return

        _state.update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            val result = auth.login(current.email, current.password)
            // The e-mail is deliberately left out of the message: it is the user's PII and it ends
            // up in any captured bug report, while adding nothing the throwable does not already say.
            result.exceptionOrNull()?.let { logWarning(TAG, "login failed", it) }
            _state.update {
                if (result.isSuccess) {
                    it.copy(submitting = false, loggedIn = true)
                } else {
                    // Deliberately generic for the user — a message that distinguished "wrong
                    // password" from "no such account" would enumerate registered e-mails. The
                    // real cause goes to logcat above.
                    it.copy(
                        submitting = false,
                        errorMessage = "Credenciais inválidas ou servidor indisponível."
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "LoginViewModel"
    }
}

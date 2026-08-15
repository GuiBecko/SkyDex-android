package com.example.skydex.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.repository.AuthGateway
import com.example.skydex.ui.common.ErrorContext
import com.example.skydex.ui.common.LogWarning
import com.example.skydex.ui.common.Tone
import com.example.skydex.ui.common.UiMessage
import com.example.skydex.ui.common.androidLogWarning
import com.example.skydex.ui.common.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val registered: Boolean = false,
    val errorMessage: UiMessage? = null
)

class RegisterViewModel(
    private val auth: AuthGateway,
    private val logWarning: LogWarning = androidLogWarning
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onNameChanged(value: String) = _state.update { it.copy(name = value, errorMessage = null) }

    fun onEmailChanged(value: String) = _state.update { it.copy(email = value, errorMessage = null) }

    fun onPasswordChanged(value: String) = _state.update { it.copy(password = value, errorMessage = null) }

    fun submit() {
        val current = _state.value
        val validationError = validate(current)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError) }
            return
        }

        // Re-entrancy is a ViewModel invariant, so it is guarded here and not only by the screen
        // disabling its button: two taps in the same frame would otherwise create two accounts.
        if (current.submitting) return

        _state.update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            val result = auth.register(current.name, current.email, current.password)
            val failure = result.exceptionOrNull()
            // The e-mail stays out of the message for the same reason it stays out of the login
            // one: it is PII, it travels into bug reports, and the throwable is the diagnostic part.
            failure?.let { logWarning(TAG, "registration failed", it) }
            _state.update {
                if (failure == null) {
                    it.copy(submitting = false, registered = true)
                } else {
                    // The old copy asked the user a question the app already knew the answer to —
                    // "Não foi possível registrar. O e-mail já existe?" while the server was
                    // replying 409 "Email already registered" in the same breath (finding A6).
                    // The presenter reads the status and states it.
                    it.copy(
                        submitting = false,
                        errorMessage = failure.toUiMessage(ErrorContext.REGISTER)
                    )
                }
            }
        }
    }

    /**
     * The password rule mirrors the backend's `@Size(min = 8)` on `RegisterRequest`. Without it the
     * server's 400 came back through the generic failure branch and told the user their e-mail was
     * taken — pointing at the wrong field entirely.
     */
    private fun validate(state: RegisterUiState): UiMessage? = when {
        state.name.isBlank() || state.email.isBlank() || state.password.isBlank() -> UiMessage(
            title = "Preencha todos os campos",
            body = "Nome, e-mail e senha são necessários para criar sua conta.",
            tone = Tone.NOTICE
        )

        state.password.length < MIN_PASSWORD_LENGTH -> UiMessage(
            title = "A senha está curta",
            body = "Use no mínimo $MIN_PASSWORD_LENGTH caracteres.",
            tone = Tone.NOTICE
        )

        else -> null
    }

    companion object {
        /** Kept in step with `@field:Size(min = 8)` on the backend's `RegisterRequest`. */
        const val MIN_PASSWORD_LENGTH = 8

        private const val TAG = "RegisterViewModel"
    }
}

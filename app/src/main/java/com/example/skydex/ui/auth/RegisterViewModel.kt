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

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val registered: Boolean = false,
    val errorMessage: String? = null
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
            // The e-mail stays out of the message for the same reason it stays out of the login
            // one: it is PII, it travels into bug reports, and the throwable is the diagnostic part.
            result.exceptionOrNull()?.let { logWarning(TAG, "registration failed", it) }
            _state.update {
                if (result.isSuccess) {
                    it.copy(submitting = false, registered = true)
                } else {
                    it.copy(
                        submitting = false,
                        errorMessage = "Não foi possível registrar. O e-mail já existe?"
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
    private fun validate(state: RegisterUiState): String? = when {
        state.name.isBlank() || state.email.isBlank() || state.password.isBlank() ->
            "Preencha todos os campos."

        state.password.length < MIN_PASSWORD_LENGTH ->
            "A senha deve ter no mínimo $MIN_PASSWORD_LENGTH caracteres."

        else -> null
    }

    companion object {
        /** Kept in step with `@field:Size(min = 8)` on the backend's `RegisterRequest`. */
        const val MIN_PASSWORD_LENGTH = 8

        private const val TAG = "RegisterViewModel"
    }
}

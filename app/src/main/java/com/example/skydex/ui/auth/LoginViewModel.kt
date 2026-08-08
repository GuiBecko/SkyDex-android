package com.example.skydex.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * Takes its dependency as a constructor parameter rather than reaching for `ServiceLocator`, which
 * is what lets it be exercised on the JVM. Only the navigation graph knows about the container.
 */
class LoginViewModel(private val auth: AuthGateway) : ViewModel() {

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

        _state.update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            val result = auth.login(current.email, current.password)
            _state.update {
                if (result.isSuccess) {
                    it.copy(submitting = false, loggedIn = true)
                } else {
                    it.copy(
                        submitting = false,
                        errorMessage = "Credenciais inválidas ou servidor indisponível."
                    )
                }
            }
        }
    }
}

package com.example.skydex.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.FriendRequestResponse
import com.example.skydex.data.remote.dto.FriendResponse
import com.example.skydex.ui.social.SocialGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val email: String = "",
    val friends: List<FriendResponse> = emptyList(),
    val requests: List<FriendRequestResponse> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null
)

class FriendsViewModel(private val social: SocialGateway) : ViewModel() {

    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    init { refresh() }

    fun onEmailChanged(value: String) = _state.update { it.copy(email = value, message = null) }

    fun refresh() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val friends = social.friends().getOrDefault(emptyList())
            val requests = social.incomingRequests().getOrDefault(emptyList())
            _state.update { it.copy(loading = false, friends = friends, requests = requests) }
        }
    }

    fun sendRequest() {
        val email = _state.value.email.trim()
        if (email.isBlank()) {
            _state.update { it.copy(message = "Digite o e-mail do seu amigo.") }
            return
        }

        viewModelScope.launch {
            social.sendRequest(email)
                .onSuccess {
                    _state.update { it.copy(email = "", message = "Convite enviado!") }
                    refresh()
                }
                .onFailure {
                    _state.update { it.copy(message = "Não foi possível enviar o convite.") }
                }
        }
    }

    fun accept(requestId: String) {
        viewModelScope.launch {
            social.accept(requestId)
                .onSuccess { refresh() }
                .onFailure { _state.update { it.copy(message = "Não foi possível aceitar o convite.") } }
        }
    }

    fun decline(requestId: String) {
        viewModelScope.launch {
            social.decline(requestId)
                .onSuccess { refresh() }
                .onFailure { _state.update { it.copy(message = "Não foi possível recusar o convite.") } }
        }
    }
}

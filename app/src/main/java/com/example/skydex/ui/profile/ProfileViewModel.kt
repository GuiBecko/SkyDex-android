package com.example.skydex.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.ProfileResponse
import com.example.skydex.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val gateway: ProfileGateway,
    private val onLogout: suspend () -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ProfileResponse>>(UiState.Loading)
    val state: StateFlow<UiState<ProfileResponse>> = _state.asStateFlow()

    /**
     * Flips to true only once [onLogout] has actually finished — e.g. the session has been
     * cleared from disk. The screen must wait for this before navigating away and tearing the
     * ViewModelStore down: [onLogout] backs onto a suspending disk write, and popping the back
     * stack that hosts this coroutine while the write is still in flight can cancel it, leaving
     * a stale session token behind despite the UI looking signed out.
     */
    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            gateway.profile()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure { _state.value = UiState.Error("Não foi possível carregar seu perfil.") }
        }
    }

    /**
     * [onLogout] is wrapped in `runCatching` rather than left to `viewModelScope.launch`'s bare
     * propagation: an uncaught throw there has no handler and crashes the app, unlike every other
     * failure path in this ViewModel, which resolves into [UiState.Error]. On failure the screen
     * falls back to that same error state — the user is still looking at their profile, so an
     * error here reads the same as any other load failure and offers the same "Tentar de novo"
     * recovery, rather than introducing a second, parallel error channel just for this one path.
     */
    fun logout() {
        viewModelScope.launch {
            runCatching { onLogout() }
                .onSuccess { _loggedOut.value = true }
                .onFailure { _state.value = UiState.Error("Não foi possível sair da conta. Tente novamente.") }
        }
    }
}

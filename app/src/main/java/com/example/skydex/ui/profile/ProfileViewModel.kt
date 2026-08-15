package com.example.skydex.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.ProfileResponse
import com.example.skydex.ui.common.ErrorContext
import com.example.skydex.ui.common.Tone
import com.example.skydex.ui.common.UiMessage
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.common.dismissMessage
import com.example.skydex.ui.common.failWith
import com.example.skydex.ui.common.startLoad
import com.example.skydex.ui.common.toUiMessage
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

    /**
     * The first load and every retry. `UiState`'s helpers separate them (finding A4): a profile
     * already on screen is not blanked by a reload, and a reload that fails becomes a banner over
     * the identity card, the stats and the badge shelf rather than erasing them.
     */
    fun refresh() {
        _state.value = _state.value.startLoad()
        viewModelScope.launch {
            gateway.profile()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure {
                    _state.value = _state.value.failWith(it.toUiMessage(ErrorContext.PROFILE))
                }
        }
    }

    /** Closes the stale-refresh banner. The profile under it stays exactly where it was. */
    fun dismissMessage() {
        _state.value = _state.value.dismissMessage()
    }

    /**
     * [onLogout] is wrapped in `runCatching` rather than left to `viewModelScope.launch`'s bare
     * propagation: an uncaught throw there has no handler and crashes the app, unlike every other
     * failure path in this ViewModel, which resolves into a [UiMessage]. On failure the screen
     * reports it through the same channel as a failed load — the user is still looking at their
     * profile, so this reads like any other failure and offers the same recovery, rather than
     * introducing a second, parallel error channel just for this one path.
     *
     * "The same channel" now means `failWith`, so a logout that fails while the profile is loaded
     * hangs a banner over the profile instead of replacing it with a full-area error (finding A4).
     * Failing to leave is no reason to take the screen away.
     */
    fun logout() {
        viewModelScope.launch {
            runCatching { onLogout() }
                .onSuccess { _loggedOut.value = true }
                .onFailure { _state.value = _state.value.failWith(LogoutFailed) }
        }
    }
}

/**
 * The logout path is local — a suspending disk write, not a request — so there is no throwable
 * shape for `ErrorPresenter` to classify and the copy is written here.
 */
private val LogoutFailed = UiMessage(
    title = "Não deu para sair da conta",
    body = "Tente de novo em instantes.",
    tone = Tone.NOTICE,
    actionLabel = "Tentar de novo"
)

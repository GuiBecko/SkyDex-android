package com.example.skydex.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.FriendRequestResponse
import com.example.skydex.data.remote.dto.FriendResponse
import com.example.skydex.ui.common.ErrorContext
import com.example.skydex.ui.common.Tone
import com.example.skydex.ui.common.UiMessage
import com.example.skydex.ui.common.toUiMessage
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
    /**
     * Success **and** failure travel through this one field — but as a [UiMessage], which carries
     * its own [Tone].
     *
     * It used to be a `String?`, and `FriendsScreen` picked the colour by comparing it against the
     * literal `"Convite enviado!"` — grey if it matched, red otherwise (audit finding A5).
     * Two things were wrong with that. The kind of feedback was inferred by comparing copy, so
     * adding an accent or trimming the exclamation mark silently repainted a success as a red
     * failure. And the app's one moment of social reward was rendered in grey.
     *
     * Carrying the tone on the message makes both impossible: an editor can rewrite every string
     * here and a success stays green, because nothing downstream reads the text to decide.
     */
    val message: UiMessage? = null
)

/** Local validation — there is no request, so no throwable, so no `ErrorPresenter`. */
private val MissingEmail = UiMessage(
    title = "Digite o e-mail do seu amigo",
    body = "Precisamos do endereço da conta dele para enviar o convite.",
    tone = Tone.NOTICE
)

/**
 * The one reward moment in the social flow. [Tone.SUCCESS], so it renders green — see the note on
 * [FriendsUiState.message] for why it used to be grey.
 */
private val InviteSent = UiMessage(
    title = "Convite enviado!",
    body = "Avisamos você assim que ele aceitar.",
    tone = Tone.SUCCESS
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
            _state.update { it.copy(message = MissingEmail) }
            return
        }

        viewModelScope.launch {
            social.sendRequest(email)
                .onSuccess {
                    _state.update { it.copy(email = "", message = InviteSent) }
                    refresh()
                }
                .onFailure { failure ->
                    // The presenter separates the three answers this endpoint really gives —
                    // 404 (no account with that address), 409 (already invited or already
                    // friends), 403 (that address is your own) — which the single
                    // "Não foi possível enviar o convite." threw away.
                    _state.update { it.copy(message = failure.toUiMessage(ErrorContext.FRIENDS)) }
                }
        }
    }

    fun accept(requestId: String) {
        viewModelScope.launch {
            social.accept(requestId)
                .onSuccess { refresh() }
                .onFailure { failure ->
                    _state.update { it.copy(message = failure.toUiMessage(ErrorContext.FRIENDS)) }
                }
        }
    }

    /**
     * Refreshes on **both** branches, unlike [accept] and [sendRequest], and deliberately.
     *
     * Decline is a delete: if it failed the row may still be gone anyway, and if the client merely
     * *thinks* it failed the row is certainly gone. Refreshing only on success left the request the
     * user had just deleted sitting on screen under an error message — which is precisely what
     * happened while `declineFriendRequest` was declared to return `Unit` and Retrofit turned every
     * successful empty-204 into a failure.
     *
     * That cause is fixed at the API layer, but the screen should not need the network layer to be
     * right in order to show the truth. Re-reading the server's list costs one request and cannot
     * be wrong; the message still tells the user the call did not go through.
     */
    fun decline(requestId: String) {
        viewModelScope.launch {
            social.decline(requestId)
                .onFailure { failure ->
                    _state.update { it.copy(message = failure.toUiMessage(ErrorContext.FRIENDS)) }
                }
            refresh()
        }
    }
}

package com.example.skydex.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.ui.common.ErrorContext
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.common.dismissMessage
import com.example.skydex.ui.common.failWith
import com.example.skydex.ui.common.startLoad
import com.example.skydex.ui.common.toUiMessage
import com.example.skydex.ui.social.SocialGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedViewModel(private val social: SocialGateway) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<WeatherEventResponse>>>(UiState.Loading)
    val state: StateFlow<UiState<List<WeatherEventResponse>>> = _state.asStateFlow()

    /**
     * Whether the **pull gesture** is currently loading — and nothing else.
     *
     * This is deliberately a separate signal from [state] rather than something derived from it.
     * `UiState.Loading` already means "the screen has nothing yet", and the screen answers it with
     * its own centred spinner; if the pull indicator read the same flag it would spin during the
     * very first load too and the user would open the Feed to two spinners at once. So only
     * [refreshFromPull] ever raises this, which is what keeps the gesture's indicator tied to the
     * gesture.
     *
     * It comes back down on **both** outcomes — see the `finally` in [load]. An indicator that keeps
     * turning after a failed pull is the classic bug of this component, and it is a `finally` rather
     * than two assignments precisely so a future branch cannot forget one of them.
     */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init { refresh() }

    /**
     * Public because the error state has to offer a way out (audit finding B3). Nothing else
     * reloads the feed — not returning to the tab, not regaining connectivity — so without this a
     * momentary network blip left the screen dead for the life of the process.
     *
     * It serves both the first load and every retry, and it is `UiState`'s helpers — not this
     * function — that tell the two apart (finding A4). `startLoad` keeps a feed that is already on
     * screen instead of blanking it behind a spinner, and `failWith` turns the failure into a banner
     * over that feed rather than into the full-area error that would delete it.
     */
    fun refresh() = load(fromPull = false)

    /**
     * The same reload, asked for by dragging the feed down.
     *
     * It reuses [load] instead of adding a second path to the network: a pull is not a different
     * request, it is the same one with a different indicator. Re-entrant pulls are dropped — the
     * gesture can fire again while the first is still in flight, and two overlapping loads would
     * race to publish into [state].
     */
    fun refreshFromPull() {
        if (_isRefreshing.value) return
        load(fromPull = true)
    }

    private fun load(fromPull: Boolean) {
        if (fromPull) _isRefreshing.value = true
        _state.value = _state.value.startLoad()
        viewModelScope.launch {
            try {
                social.feed(page = 0, size = 20)
                    .onSuccess { _state.value = UiState.Success(it) }
                    .onFailure {
                        _state.value = _state.value.failWith(it.toUiMessage(ErrorContext.FEED))
                    }
            } finally {
                if (fromPull) _isRefreshing.value = false
            }
        }
    }

    /** Closes the stale-refresh banner. The feed under it stays exactly where it was. */
    fun dismissMessage() {
        _state.value = _state.value.dismissMessage()
    }
}

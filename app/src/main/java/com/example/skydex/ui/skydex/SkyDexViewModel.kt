package com.example.skydex.ui.skydex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.SkyDexResponse
import com.example.skydex.ui.common.ErrorContext
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.common.dismissMessage
import com.example.skydex.ui.common.failWith
import com.example.skydex.ui.common.startLoad
import com.example.skydex.ui.common.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SkyDexViewModel(private val gateway: SkyDexGateway) : ViewModel() {

    private val _state = MutableStateFlow<UiState<SkyDexResponse>>(UiState.Loading)
    val state: StateFlow<UiState<SkyDexResponse>> = _state.asStateFlow()

    init { refresh() }

    /**
     * Public because the error state has to offer a way out (audit finding B3). This screen never
     * reloads on its own — nothing re-enters `init` — so before the retry button existed a single
     * moment offline left Meu SkyDex broken until the process was killed.
     *
     * First load and retry share this one function; `UiState`'s helpers separate them (finding A4).
     * A collection already on screen is never blanked by a reload, and a reload that fails becomes a
     * banner above it rather than deleting it.
     */
    fun refresh() {
        _state.value = _state.value.startLoad()
        viewModelScope.launch {
            gateway.collection()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure {
                    _state.value = _state.value.failWith(it.toUiMessage(ErrorContext.SKYDEX))
                }
        }
    }

    /** Closes the stale-refresh banner. The collection under it stays exactly where it was. */
    fun dismissMessage() {
        _state.value = _state.value.dismissMessage()
    }
}

package com.example.skydex.ui.captures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.data.repository.CaptureRepository
import com.example.skydex.ui.common.ErrorContext
import com.example.skydex.ui.common.LogWarning
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.common.androidLogWarning
import com.example.skydex.ui.common.dismissMessage
import com.example.skydex.ui.common.failWith
import com.example.skydex.ui.common.startLoad
import com.example.skydex.ui.common.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MyCapturesViewModel(
    private val captures: CaptureRepository,
    private val logWarning: LogWarning = androidLogWarning
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<WeatherEventResponse>>>(UiState.Loading)
    val state: StateFlow<UiState<List<WeatherEventResponse>>> = _state.asStateFlow()

    init { refresh() }

    /**
     * The first load and every retry. `UiState`'s helpers tell them apart (finding A4): a list
     * already on screen survives the reload, and a reload that fails hangs a banner over it instead
     * of replacing the registers the user was reading.
     */
    fun refresh() {
        _state.value = _state.value.startLoad()
        viewModelScope.launch {
            captures.myCaptures()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure {
                    // The user gets the actionable shape of the failure (offline / timeout /
                    // outage); logcat keeps the exact cause, which is the only place an expired
                    // token and a parse failure are distinguishable.
                    logWarning(TAG, "my captures lookup failed", it)
                    _state.value = _state.value.failWith(it.toUiMessage(ErrorContext.MY_CAPTURES))
                }
        }
    }

    /** Closes the stale-refresh banner. The list under it stays exactly where it was. */
    fun dismissMessage() {
        _state.value = _state.value.dismissMessage()
    }

    private companion object {
        const val TAG = "MyCapturesViewModel"
    }
}

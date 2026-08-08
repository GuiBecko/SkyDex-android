package com.example.skydex.ui.captures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.data.repository.CaptureRepository
import com.example.skydex.ui.common.LogWarning
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.common.androidLogWarning
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

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            captures.myCaptures()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure {
                    // The user-facing copy stays generic on purpose; the cause — offline, an
                    // expired token, a parse failure — is only distinguishable in logcat.
                    logWarning(TAG, "my captures lookup failed", it)
                    _state.value = UiState.Error("Não foi possível carregar seus registros.")
                }
        }
    }

    private companion object {
        const val TAG = "MyCapturesViewModel"
    }
}

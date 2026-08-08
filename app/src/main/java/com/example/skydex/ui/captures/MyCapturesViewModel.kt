package com.example.skydex.ui.captures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.data.repository.CaptureRepository
import com.example.skydex.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MyCapturesViewModel(private val captures: CaptureRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<WeatherEventResponse>>>(UiState.Loading)
    val state: StateFlow<UiState<List<WeatherEventResponse>>> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            captures.myCaptures()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure { _state.value = UiState.Error("Não foi possível carregar seus registros.") }
        }
    }
}

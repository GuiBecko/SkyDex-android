package com.example.skydex.ui.skydex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.SkyDexResponse
import com.example.skydex.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SkyDexViewModel(private val gateway: SkyDexGateway) : ViewModel() {

    private val _state = MutableStateFlow<UiState<SkyDexResponse>>(UiState.Loading)
    val state: StateFlow<UiState<SkyDexResponse>> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            gateway.collection()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure { _state.value = UiState.Error("Não foi possível carregar sua coleção.") }
        }
    }
}

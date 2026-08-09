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

    init { refresh() }

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            gateway.profile()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure { _state.value = UiState.Error("Não foi possível carregar seu perfil.") }
        }
    }

    fun logout() {
        viewModelScope.launch { onLogout() }
    }
}

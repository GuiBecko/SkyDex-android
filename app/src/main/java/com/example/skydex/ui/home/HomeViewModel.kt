package com.example.skydex.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
import com.example.skydex.data.repository.CaptureRepository
import com.example.skydex.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val captures: CaptureRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<NearbyPhenomenonResponse>>>(UiState.Loading)
    val state: StateFlow<UiState<List<NearbyPhenomenonResponse>>> = _state.asStateFlow()

    /**
     * Loading from `init` rather than from a `LaunchedEffect` in the screen is deliberate: the
     * ViewModel outlives a rotation, the composition does not, so an effect would throw the list
     * away and re-fetch every time the phone turns.
     */
    init {
        load(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
    }

    fun load(latitude: Double, longitude: Double) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            captures.nearby(latitude, longitude)
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure { _state.value = UiState.Error("Não foi possível carregar os eventos próximos.") }
        }
    }

    companion object {
        // TODO(Task 9): replace the placeholder with the phone's real GPS position.
        const val DEFAULT_LATITUDE = -23.55
        const val DEFAULT_LONGITUDE = -46.63
    }
}

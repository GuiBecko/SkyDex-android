package com.example.skydex.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
import com.example.skydex.data.repository.CaptureRepository
import com.example.skydex.ui.common.UiState
import com.example.skydex.util.Coordinates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeData(
    val coordinates: Coordinates?,
    val phenomena: List<NearbyPhenomenonResponse>
)

class HomeViewModel(
    private val captures: CaptureRepository,
    private val locationProvider: suspend () -> Coordinates?
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val state: StateFlow<UiState<HomeData>> = _state.asStateFlow()

    fun loadForCurrentPosition() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val coordinates = locationProvider()
            if (coordinates == null) {
                _state.value = UiState.Error("Ative o GPS para ver os fenômenos da sua região.")
                return@launch
            }
            captures.nearby(coordinates.latitude, coordinates.longitude)
                .onSuccess { _state.value = UiState.Success(HomeData(coordinates, it)) }
                .onFailure {
                    _state.value = UiState.Error("Não foi possível carregar os eventos próximos.")
                }
        }
    }
}

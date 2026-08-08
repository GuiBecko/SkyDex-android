package com.example.skydex.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
import com.example.skydex.data.repository.CaptureRepository
import com.example.skydex.ui.common.LogWarning
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.common.androidLogWarning
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
    private val locationProvider: suspend () -> Coordinates?,
    private val logWarning: LogWarning = androidLogWarning
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val state: StateFlow<UiState<HomeData>> = _state.asStateFlow()

    private var initialLoadClaimed = false

    /**
     * Claims the one screen-driven initial load, returning `true` exactly once per ViewModel.
     *
     * `HomeScreen`'s `LaunchedEffect(Unit)` re-runs on every Activity recreation — the manifest
     * declares no `configChanges`, so a rotation is a recreation — while this ViewModel survives it.
     * Without the latch the screen would re-launch the permission request, take a fresh GPS fix and
     * re-hit the network on every rotation, discarding a list it already has.
     *
     * The latch lives here rather than inside [loadForCurrentPosition] deliberately: making the load
     * a no-op whenever the state is already `Success` would look equivalent and would silently break
     * the retry path, which has to force a real reload.
     */
    fun shouldStartInitialLoad(): Boolean {
        if (initialLoadClaimed) return false
        initialLoadClaimed = true
        return true
    }

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
                    // The user-facing copy stays generic on purpose; the cause — offline, an
                    // expired token, a parse failure — is only distinguishable in logcat. The
                    // coordinates stay out of the message: they are the user's real position, and
                    // `LogWarning`'s contract is that a message names the operation, never its
                    // subject.
                    logWarning(TAG, "nearby lookup failed", it)
                    _state.value = UiState.Error("Não foi possível carregar os eventos próximos.")
                }
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}

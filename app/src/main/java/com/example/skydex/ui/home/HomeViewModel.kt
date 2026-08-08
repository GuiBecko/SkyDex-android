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
     * Answers whether the screen entering composition should start a load.
     *
     * `true` on the first entry, and afterwards only when the last attempt left the screen in
     * [UiState.Error]. Two things are being balanced here:
     *
     * - `HomeScreen`'s `LaunchedEffect(Unit)` re-runs on every Activity recreation — the manifest
     *   declares no `configChanges`, so a rotation is a recreation — while this ViewModel survives
     *   it. Reloading unconditionally would re-launch the permission request, take a fresh GPS fix
     *   and re-hit the network on every rotation, throwing away a list already on screen.
     * - An error is not a result worth protecting. This ViewModel also outlives navigation: the
     *   bottom bar uses `popUpTo(HOME) { saveState = true }` + `restoreState`, so the `HOME` entry
     *   and everything in it come back exactly as they were. A latch that never re-armed would pin
     *   one second of being offline to the whole process lifetime — leaving the tab and returning
     *   would change nothing, and only killing the app would clear it.
     *
     * `Loading` deliberately answers `false`: a load already in flight must not be doubled by a
     * re-entry that happens while the fix is still being taken.
     *
     * This gate stays out of [loadForCurrentPosition] on purpose. Short-circuiting the load itself
     * whenever the state is already `Success` would look equivalent and would silently disable the
     * retry control on the error screen, which has to force a real reload.
     */
    fun shouldLoadOnEntry(): Boolean {
        if (!initialLoadClaimed) {
            initialLoadClaimed = true
            return true
        }
        return _state.value is UiState.Error
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

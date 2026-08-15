package com.example.skydex.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
import com.example.skydex.data.repository.CaptureRepository
import com.example.skydex.ui.common.ErrorContext
import com.example.skydex.ui.common.LogWarning
import com.example.skydex.ui.common.Tone
import com.example.skydex.ui.common.UiMessage
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.common.androidLogWarning
import com.example.skydex.ui.common.dismissMessage
import com.example.skydex.ui.common.failWith
import com.example.skydex.ui.common.startLoad
import com.example.skydex.ui.common.toUiMessage
import com.example.skydex.util.Coordinates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeData(
    val coordinates: Coordinates?,
    val phenomena: List<NearbyPhenomenonResponse>
)

/**
 * No fix could be taken. There is no throwable here — the location provider simply answered `null`
 * — so this is written by hand instead of coming from `ErrorPresenter`.
 *
 * `HomeScreen` overrides it when it knows the permission was actively *denied*, because that user
 * needs Settings rather than the GPS switch.
 */
private val NoPosition = UiMessage(
    title = "Não achamos onde você está",
    body = "Ative a localização do aparelho para ver os fenômenos da sua região.",
    tone = Tone.NOTICE,
    actionLabel = "Tentar de novo"
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
     * re-entry that happens while the fix is still being taken. So does a [UiState.Success] carrying
     * a stale-refresh message (finding A4): that user still has a list *and* a visible banner with
     * its own retry, which is not the dead end this latch re-arms for. Only [UiState.Error] — the
     * screen with nothing on it — is worth re-loading on re-entry.
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

    /**
     * The first load and every retry. `UiState`'s helpers tell them apart (finding A4): a list of
     * nearby phenomena already on screen survives the reload, and a reload that fails — for either
     * reason below — becomes a banner above that list instead of replacing it.
     */
    fun loadForCurrentPosition() {
        _state.value = _state.value.startLoad()
        viewModelScope.launch {
            val coordinates = locationProvider()
            if (coordinates == null) {
                // Not a failure of anything — an instruction. It gets the same calm notice
                // treatment as everything else rather than the red the audit found here (A3).
                _state.value = _state.value.failWith(NoPosition)
                return@launch
            }
            captures.nearby(coordinates.latitude, coordinates.longitude)
                .onSuccess { _state.value = UiState.Success(HomeData(coordinates, it)) }
                .onFailure {
                    // `ErrorPresenter` now separates offline from a timeout from a 5xx, so the
                    // user gets the right next step; logcat still gets the exact cause, which is
                    // the only place a parse failure is distinguishable from an expired token. The
                    // coordinates stay out of the log message: they are the user's real position,
                    // and `LogWarning`'s contract is that a message names the operation, never its
                    // subject.
                    logWarning(TAG, "nearby lookup failed", it)
                    _state.value = _state.value.failWith(it.toUiMessage(ErrorContext.NEARBY))
                }
        }
    }

    /** Closes the stale-refresh banner. The phenomena under it stay exactly where they were. */
    fun dismissMessage() {
        _state.value = _state.value.dismissMessage()
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}

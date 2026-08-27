package io.github.guibecko.skydex.ui.detail

import androidx.lifecycle.ViewModel
import io.github.guibecko.skydex.data.remote.dto.WeatherEventResponse
import io.github.guibecko.skydex.ui.common.CaptureUnavailable
import io.github.guibecko.skydex.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the user opened a capture from. Decides what the detail screen is allowed to say about it.
 *
 * Not a cosmetic flag: reached from **Meus Registros** the capture belongs to the signed-in user, so
 * an "por Fulano" block would be telling them their own name, and a "ver amigos" affordance next to
 * their own photo makes no sense. Reached from the **Feed** the author is the whole point — it is
 * someone else's capture, and whose it is is the first thing the screen has to answer.
 */
enum class CaptureOrigin {
    /** Meus Registros. The capture is the signed-in user's own; the author block is suppressed. */
    MINE,

    /** Feed. Someone else's capture; the author block is shown. */
    FEED;

    companion object {
        /**
         * Reads the value back out of the route.
         *
         * Falls back to [MINE] rather than throwing. A nav argument can only be wrong if someone
         * hand-typed a deep link or renamed an enum constant, and in both cases the honest failure
         * is "the screen shows a little less than it could", not a crash on a screen whose whole
         * job is to display a photo.
         */
        fun parse(raw: String?): CaptureOrigin =
            entries.firstOrNull { it.name == raw } ?: MINE
    }
}

/**
 * State holder for one capture's detail page.
 *
 * ## There is no loading here, and that is not an oversight
 *
 * The resolution is a synchronous map lookup against [CaptureRegistry] — see its KDoc for why the
 * capture is handed over in memory rather than fetched (short version: the API has no
 * `GET api/events/{id}`, and the list item the user tapped already carries every field this screen
 * renders). A lookup that cannot block cannot honestly publish `UiState.Loading`, so the initial
 * state is already the answer: [UiState.Success] on a hit, [UiState.Error] on a miss. A spinner that
 * is never on screen for a frame is a lie about how the screen works, and it would make the cold
 * start case flash before it explains itself.
 *
 * That leaves exactly two states, which is the whole test surface:
 *
 * - **Hit** — the user came from a list in this process. `Success(capture)`.
 * - **Miss** — the process was killed while the detail screen was on top and Android restored the
 *   route without the registry. `Error(CaptureUnavailable)`, whose action label is "Voltar", and the
 *   screen draws the full-area `SkyDexNoticeState`. No blank screen, no crash, no endless spinner.
 *
 * `UiState.Success.staleMessage` is never set: nothing here can refresh, so nothing can go stale.
 *
 * @param captureId the id carried by the route.
 * @param registry the process-lifetime handoff the list wrote into. Injected rather than reached for
 *   so this class stays a plain JUnit test subject with no Android, no network and no ServiceLocator
 *   behind it.
 */
class CaptureDetailViewModel(
    captureId: String,
    registry: CaptureRegistry
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<WeatherEventResponse>>(
        registry.find(captureId)
            ?.let { UiState.Success(it) }
            ?: UiState.Error(CaptureUnavailable)
    )

    val state: StateFlow<UiState<WeatherEventResponse>> = _state.asStateFlow()
}

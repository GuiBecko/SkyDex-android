package com.example.skydex.ui.common

/**
 * The states every screen that loads something can be in.
 *
 * # Why this is not three states any more
 *
 * It used to be exactly `Loading | Success | Error`, and every screen rendered a `when` over it. That
 * shape cannot express the one sentence the app needs most — *"I already have data **and** the last
 * refresh failed"* — so the only thing a screen could do with a second, failed load was replace the
 * content the user was already reading with an error. Audit finding A4: read the feed, let a refresh
 * fail, and the feed is gone.
 *
 * The fix is one nullable field, not a fourth case. [Success] carries the data it always carried plus
 * an optional [Success.staleMessage] — the failure of the *most recent* attempt, to be drawn as an
 * inline `SkyDexNotice` **above** content that stays exactly where it was.
 *
 * # The contract
 *
 * - **First load fails → [Error].** There is genuinely nothing to keep, so the screen draws the
 *   full-area `SkyDexNoticeState` with its retry. [Error] deliberately carries **no data**, which is
 *   what makes the illegal state — a banner floating over nothing — unrepresentable rather than
 *   merely discouraged. There is no way to build an `Error` a screen could be tempted to draw as an
 *   overlay, and no way to attach a `staleMessage` to a state with no content underneath it.
 * - **A refresh fails with data on screen → [Success] carrying a [Success.staleMessage].** The data
 *   is retained; the failure arrives as a dismissible banner offering the same recovery action.
 * - **A refresh starts → the banner clears** ([startLoad]). That is the acknowledgement that the
 *   retry was heard: the banner disappears, and comes back only if the new attempt fails too.
 * - **A refresh succeeds → the banner is gone**, because success publishes a fresh [Success] whose
 *   `staleMessage` defaults to `null`.
 * - **The user closes it → [dismissMessage]**, which drops the message and keeps the data.
 *
 * The three helpers below are the whole state machine. A ViewModel never decides between "full-area"
 * and "banner" itself — it reports what happened ([failWith]) and the type settles which of the two
 * is even representable.
 */
sealed interface UiState<out T> {

    /** Nothing to show yet, and nothing to keep. The first load only — see [startLoad]. */
    data object Loading : UiState<Nothing>

    /**
     * Content the screen can render.
     *
     * @param data what was loaded. Still present even when [staleMessage] is set — that is the point.
     * @param staleMessage the failure of the last refresh, or `null` when the content is current.
     *   Non-null means "draw the inline `SkyDexNotice` above [data]", never "replace [data]".
     */
    data class Success<T>(val data: T, val staleMessage: UiMessage? = null) : UiState<T>

    /**
     * The first load failed, so there is nothing on screen worth protecting. Renders full-area.
     *
     * Carries a [UiMessage] rather than a bare `String`.
     *
     * A sentence forced every screen to invent its own presentation for it — which is how the app
     * ended up with two shades of red, no retry on three screens, and one screen painting an
     * instruction as a failure. A [UiMessage] arrives already knowing its tone and its recovery
     * label, so `SkyDexNotice` can render any of them the same way.
     */
    data class Error(val message: UiMessage) : UiState<Nothing>
}

/**
 * Enter a load.
 *
 * Content already on screen **survives**, and that is the half of finding A4 which is easy to miss:
 * flipping to [UiState.Loading] on every refresh destroys the feed just as thoroughly as flipping to
 * [UiState.Error] does — only with a spinner instead of a sentence. So a reload over existing data
 * keeps the data and merely clears whatever banner the previous attempt left behind; only a screen
 * with nothing on it shows the spinner.
 */
fun <T> UiState<T>.startLoad(): UiState<T> = when (this) {
    is UiState.Success -> dismissMessage()
    else -> UiState.Loading
}

/**
 * Report that the attempt failed.
 *
 * The current state decides how loud that is: with data on screen the failure becomes a banner over
 * it, and with nothing on screen the full-area [UiState.Error]. Callers do not choose — which is why
 * no ViewModel can throw a loaded list away by writing `UiState.Error` on a whim.
 */
fun <T> UiState<T>.failWith(message: UiMessage): UiState<T> = when (this) {
    is UiState.Success -> copy(staleMessage = message)
    else -> UiState.Error(message)
}

/**
 * The user closed the banner. Only meaningful on [UiState.Success]: the full-area [UiState.Error] is
 * not dismissible, because dismissing it would leave a blank screen with no way back.
 */
fun <T> UiState<T>.dismissMessage(): UiState<T> = when (this) {
    is UiState.Success -> if (staleMessage == null) this else copy(staleMessage = null)
    else -> this
}

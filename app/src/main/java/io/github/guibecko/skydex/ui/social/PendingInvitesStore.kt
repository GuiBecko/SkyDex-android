package io.github.guibecko.skydex.ui.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How many friend invites are waiting, held for the whole process so the bottom bar can badge the
 * Perfil tab from any screen.
 *
 * It lives above the ViewModels rather than inside one because its reader is the navigation shell:
 * the bar is drawn by `SkyDexNavHost`, outside every screen's ViewModel, and the count has to
 * survive moving between tabs. `ServiceLocator` owns the single instance.
 *
 * **A failed refresh keeps the last known count** rather than falling back to zero. A dropped
 * request is not evidence that the invites went away, and blinking the badge off on a bad connection
 * is worse than showing a number a few seconds stale — the user taps through to a list that is
 * always read fresh from the server anyway.
 *
 * There is no push channel, so "notification" here means: the app asks on every navigation
 * (`SkyDexNavHost`) and right after the user answers an invite (`FriendsViewModel`). A closed app
 * announces nothing, by design — see the plan's note on FCM.
 */
class PendingInvitesStore(private val social: SocialGateway) {

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    suspend fun refresh() {
        social.pendingRequestCount().onSuccess { _count.value = it }
    }

    /**
     * Drops the badge to zero without asking the server. Called on sign-out: this object outlives
     * the session, so without it the next account to log in on the device would see the previous
     * user's badge until the first navigation refreshed it.
     *
     * Not called when the user merely *opens* the invite list. The badge counts invites left
     * **unanswered**, not unseen — it clears when the user accepts or declines, which is the state
     * worth surfacing.
     */
    fun clear() {
        _count.value = 0
    }
}

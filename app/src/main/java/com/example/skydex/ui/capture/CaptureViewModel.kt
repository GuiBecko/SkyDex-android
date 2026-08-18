package com.example.skydex.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.CreateWeatherEventRequest
import com.example.skydex.data.remote.dto.ProfileResponse
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.data.repository.httpStatusCode
import com.example.skydex.ui.common.ErrorContext
import com.example.skydex.ui.common.LogWarning
import com.example.skydex.ui.common.Tone
import com.example.skydex.ui.common.UiMessage
import com.example.skydex.ui.common.androidLogWarning
import com.example.skydex.ui.common.toUiMessage
import com.example.skydex.ui.components.CaptureReward
import com.example.skydex.ui.components.CaptureRewardBonus
import com.example.skydex.util.Coordinates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CaptureUiState(
    val title: String = "",
    val description: String = "",
    val photoFile: File? = null,
    /**
     * The path the server assigned to [photoFile], once it has been uploaded successfully.
     *
     * Kept so a retry after a failed `create` reuses the JPEG already on the server instead of
     * uploading a second copy. Written back only while [photoFile] is still the file that was
     * uploaded, since the write happens after a suspension the user can retake during.
     *
     * Cleared in two places, for two different reasons:
     * - [CaptureViewModel.onPhotoTaken], because the moment the user retakes the shot this path
     *   points at the picture they just replaced.
     * - a **400** from `create`, because since Task 12b photos are single-use and the server stamps
     *   `consumed_at` in the same transaction as the insert. Once the server has rejected a
     *   capture citing this path, it will reject every retry citing it too.
     */
    val uploadedPhotoUrl: String? = null,
    val coordinates: Coordinates? = null,
    val locating: Boolean = false,
    val submitting: Boolean = false,
    val saved: Boolean = false,
    /**
     * The reward moment for the capture that was just stored, or `null` when there is nothing to
     * celebrate yet.
     *
     * Set in the same update as [saved], from the `WeatherEventResponse` the create call returns —
     * `xpAwarded`, `rarity`, `phenomenonName` and `validationStatus` all come straight off it, so
     * the overlay never has to wait for a second request to have something true to show. This is
     * audit finding B6: the screen used to navigate away the instant [saved] flipped, and the XP
     * only surfaced later as a number in a list.
     *
     * [saved] deliberately stays a separate flag rather than being folded into
     * `reward != null`. It is the re-submit guard, it means "the server has this capture" and
     * nothing else, and every existing test that asserts a successful save reads it. Keeping the
     * two apart is also what lets [startNewCapture] clear both in one place without the guard's
     * meaning drifting.
     */
    val reward: CaptureReward? = null,
    val errorMessage: UiMessage? = null
)

// Local validation and client-side races: no request was ever made, so there is no throwable for
// `ErrorPresenter` to classify. Written here, in the same voice.

private val MissingText = UiMessage(
    title = "Falta o título e a descrição",
    body = "Conte o que você viu para registrar o fenômeno.",
    tone = Tone.NOTICE
)

private val MissingPhoto = UiMessage(
    title = "Falta a foto",
    body = "Tire uma foto do fenômeno antes de salvar.",
    tone = Tone.NOTICE
)

private val MissingPosition = UiMessage(
    title = "Não achamos onde você está",
    body = "Ative a localização do aparelho e tente de novo.",
    tone = Tone.NOTICE
)

/** The retake-during-upload race. The photo on screen is not the one the request was carrying. */
private val PhotoReplacedMidUpload = UiMessage(
    title = "A foto mudou durante o envio",
    body = "Toque em Salvar de novo para registrar a foto que está na tela.",
    tone = Tone.NOTICE,
    actionLabel = "Tentar de novo"
)

/**
 * @param captures the two-call slice of the capture API this screen needs.
 * @param profile an **optional** reader for the profile endpoint, used for nothing but the
 *   level-up / new-badge half of the reward moment. See [profileBefore] and [loadBonus] for the
 *   contract; `null` disables the feature entirely and every other behaviour of this ViewModel is
 *   identical with or without it.
 * @param logWarning where failures that are deliberately never shown to the user go instead. Used
 *   by [discardUnconfirmed] and nowhere else; defaults to the real logcat call, so only tests pass
 *   it.
 * @param locationProvider one GPS fix, or `null` if there is none to be had.
 *
 * The optional parameters sit in the **middle** on purpose, which is unusual enough to explain.
 * `locationProvider` is last because it is the one every caller passes as a trailing lambda —
 * `CaptureViewModel(repository) { deviceLocation.current() }`, the same shape `HomeViewModel` uses
 * and the shape every test in `CaptureViewModelTest` is written in. Kotlin binds a trailing lambda
 * to the last parameter, so moving [locationProvider] off the end to make room for a defaulted
 * argument would silently rebind twenty call sites onto the wrong one. Adding the new dependencies
 * where they do no damage is worth more than declaration order that reads tidily.
 */
class CaptureViewModel(
    private val captures: CaptureGateway,
    private val profile: (suspend () -> Result<ProfileResponse>)? = null,
    private val logWarning: LogWarning = androidLogWarning,
    private val locationProvider: suspend () -> Coordinates?
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private var initialLocationClaimed = false

    /**
     * The profile as it stood *before* this screen's capture, or `null` if it could not be read.
     *
     * This is the only honest way to report a level-up, because `POST /api/captures` returns
     * neither the new level nor a level-up flag nor the badge list — only the profile endpoint has
     * them, and a single after-the-fact read cannot tell "level 3" from "level 3 already". So the
     * baseline is taken on entry, long before the user has a photo to save, and diffed against a
     * second read afterwards.
     *
     * Left `null` on failure on purpose, and [loadBonus] then claims nothing at all. Showing an
     * unverified level-up would be worse than showing none: the number is checkable on the very
     * next screen, so a wrong one is a lie the user catches immediately.
     */
    private var profileBefore: ProfileResponse? = null

    /**
     * An upload started by [onPhotoTaken], paired with the file it is carrying.
     *
     * The file is held alongside the job and not inferred from state, because state moves: a
     * retake replaces [CaptureUiState.photoFile] while this job is still in flight, and matching
     * on identity is what stops [submit] adopting a path that belongs to a photo the user has
     * already replaced.
     */
    private class PendingUpload(val file: File, val job: Deferred<Result<String>>)

    private var pendingUpload: PendingUpload? = null

    init {
        // Fire-and-forget, and never surfaced. It runs while the user is still framing a photo, so
        // it has minutes of head start on the moment it feeds, and if it fails the capture flow
        // does not change in any way the user can see.
        val reader = profile
        if (reader != null) {
            viewModelScope.launch { profileBefore = reader().getOrNull() }
        }
    }

    fun onTitleChanged(value: String) = _state.update { it.copy(title = value, errorMessage = null) }

    fun onDescriptionChanged(value: String) =
        _state.update { it.copy(description = value, errorMessage = null) }

    /**
     * Records the new photo and starts uploading it immediately.
     *
     * ## Why the upload does not wait for Save
     *
     * The backend runs the vision model inside `POST /api/photos`, and a photo it does not believe
     * is the sky comes back 422. Uploading at Save time would put that rejection *after* the user
     * had written a title and a description — the moment they can least afford to be told to
     * start over. Firing here puts the round-trip inside the seconds they spend typing, so the
     * answer is usually already in by the time they reach the button.
     *
     * The failure is shown but nothing else happens: no navigation, no blocked form, no retry
     * loop. The recovery is "take another photo", which the screen already offers.
     */
    fun onPhotoTaken(file: File) {
        // The previous job is not cancelled. It is already in flight, cancelling buys nothing the
        // identity check below does not, and a cancelled `async` whose result is never awaited
        // reports as an unhandled failure in some coroutine configurations.
        _state.update { it.copy(photoFile = file, uploadedPhotoUrl = null, errorMessage = null) }

        val job = viewModelScope.async { captures.uploadPhoto(file) }
        pendingUpload = PendingUpload(file, job)

        viewModelScope.launch {
            val result = try {
                job.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }

            // Every write below is conditional on this still being the photo on screen. Between
            // the launch and here sits a whole network round-trip the user can retake during.
            result
                .onSuccess { url ->
                    _state.update { if (it.photoFile == file) it.copy(uploadedPhotoUrl = url) else it }
                }
                .onFailure { failure ->
                    logWarning(TAG, "Eager photo upload failed", failure)
                    _state.update {
                        if (it.photoFile == file) {
                            it.copy(errorMessage = failure.toUiMessage(ErrorContext.PHOTO_UPLOAD))
                        } else {
                            it
                        }
                    }
                }
        }
    }

    /**
     * Claims the one screen-driven initial location request, returning `true` exactly once per
     * ViewModel — same reasoning as `HomeViewModel.shouldLoadOnEntry`: `CaptureScreen`'s
     * `LaunchedEffect(Unit)` re-runs on every Activity recreation, so without the latch a rotation
     * re-launches the permission request and takes another GPS fix for a position already held.
     * [refreshLocation] itself stays unconditional, because "Tentar novamente" has to work.
     */
    fun shouldRequestInitialLocation(): Boolean {
        if (initialLocationClaimed) return false
        initialLocationClaimed = true
        return true
    }

    fun refreshLocation() {
        _state.update { it.copy(locating = true) }
        viewModelScope.launch {
            val coordinates = locationProvider()
            _state.update { it.copy(locating = false, coordinates = coordinates) }
        }
    }

    fun submit() {
        val current = _state.value

        // The Button's `enabled = !state.submitting` only takes effect at the next recomposition,
        // so two taps landing in the same frame are both dispatched against an enabled Button and
        // both reach here.
        //
        // `saved` covers the other window, and since the reward overlay it now covers a much wider
        // one. It used to be the gap between the capture landing and a `LaunchedEffect` navigating
        // away — a frame or two in which `submitting` was already false again. Now the screen
        // deliberately *stays* until the user dismisses the celebration, so the form and its Save
        // button live on underneath the overlay for as long as the user wants to look at it. The
        // overlay's scrim swallows taps aimed at them, but this is the guard: without it, one leak
        // through that scrim would file a second copy of the same storm.
        if (current.submitting || current.saved) return

        val error = when {
            current.title.isBlank() || current.description.isBlank() -> MissingText
            current.photoFile == null -> MissingPhoto
            current.coordinates == null -> MissingPosition
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(errorMessage = error) }
            return
        }

        val photoFile = current.photoFile!!
        val coordinates = current.coordinates!!

        _state.update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            // Reuse the JPEG already on the server if a previous attempt got that far. Without
            // this, every retry of a failed `create` leaves one more orphaned photo behind.
            //
            // The *first* orphan — upload succeeded, create failed, nothing ever references the
            // file — cannot be cleaned up from here: the API exposes no delete-photo endpoint, so
            // the client has no way to take it back. That is post-MVP backlog item #13 (orphaned
            // JPEG cleanup), which is a server-side sweep by design. All this cache can do, and
            // does, is stop one user tapping Save three times from becoming three orphans.
            // Three ways to have a photo path here, in order of preference: one the eager upload
            // already finished, one it is still working on, or — only if there is no job at all,
            // which a normal flow cannot produce — a fresh upload started right now.
            val photoUrl = current.uploadedPhotoUrl ?: run {
                val pending = pendingUpload?.takeIf { it.file == photoFile }
                val uploaded = try {
                    pending?.job?.await() ?: captures.uploadPhoto(photoFile)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }

                uploaded.exceptionOrNull()?.let { failure ->
                    _state.update {
                        it.copy(
                            submitting = false,
                            errorMessage = failure.toUiMessage(ErrorContext.PHOTO_UPLOAD)
                        )
                    }
                    return@launch
                }

                // Conditional, because this write lands *after* a suspension the user can act
                // during: "Tirar Outra Foto" stays tappable for the frame in which the submit
                // starts, and `onPhotoTaken` clearing the cache is worthless if the coroutine that
                // was already in flight resurrects it. Caching a path that belongs to a replaced
                // file would file the *next* attempt under a picture the user cannot see any more.
                uploaded.getOrThrow().also { url ->
                    _state.update { if (it.photoFile == photoFile) it.copy(uploadedPhotoUrl = url) else it }
                }
            }

            // Same window, one step further along: this attempt uploaded (or reused) a photo the
            // user has since replaced, so saving it would file the capture under an image the
            // preview no longer shows — and then navigate away, leaving nothing to notice. There
            // is no un-sending a `create`, so the check has to happen before it, not after.
            if (_state.value.photoFile != photoFile) {
                _state.update { it.copy(submitting = false, errorMessage = PhotoReplacedMidUpload) }
                return@launch
            }

            val request = CreateWeatherEventRequest(
                title = current.title,
                description = current.description,
                photoUrl = photoUrl,
                latitude = coordinates.latitude,
                longitude = coordinates.longitude,
                locationIsMock = coordinates.isMock
            )

            captures.create(request)
                .onSuccess { event ->
                    // The response used to be discarded. Everything the peak moment needs is on
                    // it, so the celebration is on screen the instant the create returns — no
                    // second round-trip stands between the user and their reward.
                    val reward = rewardFrom(event)
                    _state.update { s ->
                        s.copy(submitting = false, saved = true, reward = reward)
                    }
                    // Both of these are launched *after* the state update above, never before it,
                    // and neither is awaited: the overlay is already on screen by the time either
                    // request leaves the device.
                    if (!reward.confirmed) discardUnconfirmed(event.id)
                    loadBonus()
                }
                .onFailure { failure ->
                    // A 400 is the server saying this request will never be accepted as it stands.
                    // Anything else — a dropped connection, a 500 — says nothing about the photo,
                    // and most likely means the request never landed at all.
                    //
                    // Read from the status code, never from the body: `backendErrorMessage()` may
                    // only be called once, and `toUiMessage` below is the one caller that does.
                    val rejected = failure.httpStatusCode == 400

                    // A 400 invalidates the path, but not the `pendingUpload` job that produced
                    // it: that job already completed, and `Deferred.await()` on a completed job
                    // just replays its cached result. Left alone, the next submit's fallback would
                    // find this same (still-matching-by-file) pending job, "await" it again, and
                    // resurrect the very path the server just refused — forever. Dropping the
                    // holder here, guarded the same way as every other write in this class, forces
                    // the next attempt down the "no job at all" branch, which re-uploads for real.
                    if (rejected && pendingUpload?.file == photoFile) pendingUpload = null

                    _state.update { s ->
                        s.copy(
                            submitting = false,
                            // This used to forward the backend's raw English straight to a
                            // pt-BR screen — audit finding B1, and in the "Unknown phenomenon:
                            // <ENUM>" case it leaked a domain enum name at the user. The
                            // presenter keeps what was right about that decision (Tasks 12b and
                            // 12c wrote five *distinct* 400s and each implies a different next
                            // step, so collapsing them into one "tente de novo" misleads) while
                            // answering in our own words.
                            errorMessage = failure.toUiMessage(ErrorContext.CAPTURE_SAVE),
                            // Drop the cached upload on a 400, so the retry re-uploads.
                            //
                            // This is the seam three separately-correct decisions fell through.
                            // The cache above exists so a retry does not orphan a second JPEG
                            // (Task 10); Task 12b then made photos single-use, stamping
                            // `consumed_at` in the same transaction as the capture insert. So
                            // whenever the server commits and the client still sees a failure, the
                            // cached path names a spent photo and every retry is refused with
                            // "This photo has already been used for a capture" — permanently. The
                            // same applies once the photo passes the server's 30-minute MAX_AGE.
                            //
                            // One extra orphaned JPEG, which the server-side sweep already owns,
                            // is a much smaller cost than a capture the user can never complete.
                            uploadedPhotoUrl = if (rejected) null else s.uploadedPhotoUrl
                        )
                    }
                }
        }
    }

    /**
     * Clears the form so the user can shoot the next phenomenon without leaving the screen — the
     * "Registrar outro" way out of the reward overlay.
     *
     * Everything that describes the capture just stored goes, [saved] included: leaving it set
     * would leave the re-submit guard latched and the Save button permanently inert for the next
     * capture. [CaptureUiState.uploadedPhotoUrl] goes with the photo it names, for the same reason
     * [onPhotoTaken] clears it — the server has already consumed it, so citing it again is a
     * guaranteed 400.
     *
     * The position is deliberately kept: the user has not moved, the fix is still valid, and
     * re-acquiring it would put a spinner between them and their next shot for no gain.
     */
    fun startNewCapture() {
        pendingUpload = null
        _state.update {
            it.copy(
                title = "",
                description = "",
                photoFile = null,
                uploadedPhotoUrl = null,
                submitting = false,
                saved = false,
                reward = null,
                errorMessage = null
            )
        }
    }

    /**
     * Everything the peak moment can say from the capture response alone.
     *
     * `xpAwarded` is passed through rather than derived from the rarity: the backend awards
     * `rarity.xp` only on a CONFIRMED capture and zero on every unconfirmed path, and it can also
     * zero an already-confirmed award late, in `CaptureCommitService.commit`, when the travel
     * re-check under the row lock disagrees. Re-deriving it client-side from the rarity would
     * promise XP the server did not grant.
     */
    private fun rewardFrom(event: WeatherEventResponse) = CaptureReward(
        phenomenonName = event.phenomenonName,
        rarity = event.rarity,
        confirmed = event.validationStatus.equals(CONFIRMED_STATUS, ignoreCase = true),
        xpAwarded = event.xpAwarded
    )

    /**
     * Takes back a capture the backend stored but did not confirm.
     *
     * ## Why the record goes
     *
     * The collection is the product. A row the backend could not match against the region's real
     * weather is worth no XP, counts towards no species and cannot be un-rejected — the backend
     * never re-validates a stored capture — so leaving it in Meus Registros gives the user a
     * permanent, unexplainable entry among the ones they earned. `DELETE api/events/{id}` fires the
     * moment `validationStatus` comes back as anything other than `CONFIRMED`.
     *
     * ## Why it is silent
     *
     * There is nothing here the user can act on. A confirmation prompt would ask them to approve
     * the removal of something they never knew existed, and an error notice would report the
     * failure of a request they never asked for. So this reports **nothing**: no [UiMessage], no
     * state change, no effect on [CaptureUiState.reward]. `CaptureRewardOverlay`'s unconfirmed copy
     * carries the whole user-visible half of this — it says the capture was not confirmed, and it
     * deliberately does not claim the photo is stored anywhere.
     *
     * ## When it fails
     *
     * A failed DELETE — offline, a 500, a 403, a request that never left — is written to
     * [logWarning] and dropped. No retry, no queue, no error state, no blocked navigation. The
     * consequence is that the unconfirmed record **survives on the server** and shows up in Meus
     * Registros like any other. That is the accepted degradation: an orphan row costs the user a
     * confusing entry, whereas a retry loop or a surfaced failure would cost them the peak moment
     * of the app.
     *
     * ## Why it is [NonCancellable]
     *
     * This fires at the exact instant `CaptureScreen` becomes dismissable — the overlay is up, and
     * "Ver meus registros" or the back gesture pops the Capture destination and clears this
     * ViewModel, cancelling `viewModelScope` mid-flight. A plain `viewModelScope.launch` would
     * therefore drop the DELETE precisely in the case it is most likely to be needed, on a fast tap.
     *
     * Detaching the job from the scope's [kotlinx.coroutines.Job] is the deliberate choice: the
     * request outlives the screen and completes (or fails) on its own. The price is that this
     * coroutine holds a reference to the ViewModel for the length of one HTTP round-trip after
     * `onCleared`, bounded by OkHttp's timeouts — a few seconds of retained memory, no observable
     * behaviour, and nothing it can write to a screen that is gone, since it touches no state. It
     * still dies with the process; a DELETE interrupted by the app being killed leaves the record
     * behind, which is the same accepted degradation as a failed one above.
     *
     * ## Known limitation
     *
     * This is a forward-only rule. Unconfirmed captures already stored before it shipped stay in
     * Meus Registros untouched, and that is on purpose — a sweep of existing records on opening the
     * list was considered and rejected. There is no client-side backfill to write here.
     */
    private fun discardUnconfirmed(id: String) {
        viewModelScope.launch(NonCancellable) {
            // `runCatching` on top of the gateway's own `Result`: a gateway that throws instead of
            // returning a failure would otherwise escape a coroutine with no parent to hand it to
            // and take the process down — for a request the user never made. Catching
            // CancellationException is normally a bug; here there is nothing to cancel this job.
            val failure = runCatching { captures.delete(id) }
                .fold(onSuccess = { it.exceptionOrNull() }, onFailure = { it })

            // No id in the message, in line with `LogWarning`'s contract: the throwable is what
            // separates offline from a 403, and the identifier only leaks into bug reports.
            failure?.let { logWarning(TAG, "discarding an unconfirmed capture failed", it) }
        }
    }

    /**
     * Fills in the level-up and new-badge lines, if there are any and if we can prove it.
     *
     * Launched **after** the state update that puts the overlay on screen, in its own coroutine, so
     * the celebration never waits on it. Three things can make it a no-op — no profile reader
     * wired, no baseline from [profileBefore], or a failed read — and all three end the same way:
     * the user keeps the reward they already have, with the XP the capture response carried, and
     * simply never sees a bonus line. There is no error path here by design; a failed enrichment is
     * not something to interrupt a celebration with.
     *
     * The write is a `copy` on the *current* reward rather than on a captured one, and it bails if
     * the reward has gone. Between the create returning and this call answering, the user may have
     * tapped "Registrar outro" — writing a bonus into a state whose reward was cleared would
     * resurrect the overlay over a fresh, empty form.
     */
    private fun loadBonus() {
        val reader = profile ?: return
        val before = profileBefore ?: return

        viewModelScope.launch {
            val after = reader().getOrNull() ?: return@launch

            // Strictly greater, never "different": levels do not go down, but a re-read that
            // somehow reported a lower one must not be announced as a promotion.
            val newLevel = after.level.takeIf { it > before.level }

            // Compared by `achievement`, the stable enum name, not by `displayName`, which is copy
            // and may be reworded server-side without anything unlocking.
            val heldBefore = before.badges.filter { it.unlocked }.map { it.achievement }.toSet()
            val newBadges = after.badges
                .filter { it.unlocked && it.achievement !in heldBefore }
                .map { it.displayName }

            if (newLevel == null && newBadges.isEmpty()) return@launch

            _state.update { s ->
                val reward = s.reward ?: return@update s
                s.copy(reward = reward.copy(bonus = CaptureRewardBonus(newLevel, newBadges)))
            }
        }
    }

    private companion object {
        /** `ValidationStatus.CONFIRMED.name`, as it arrives on the wire. */
        const val CONFIRMED_STATUS = "CONFIRMED"

        const val TAG = "CaptureViewModel"
    }
}

package com.example.skydex.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.CreateWeatherEventRequest
import com.example.skydex.util.Coordinates
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
     * uploading a second copy. Cleared by [CaptureViewModel.onPhotoTaken], because the moment the
     * user retakes the shot this path points at the picture they just replaced — and written back
     * only while [photoFile] is still the file that was uploaded, since the write happens after a
     * suspension the user can retake during.
     */
    val uploadedPhotoUrl: String? = null,
    val coordinates: Coordinates? = null,
    val locating: Boolean = false,
    val submitting: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null
)

class CaptureViewModel(
    private val captures: CaptureGateway,
    private val locationProvider: suspend () -> Coordinates?
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private var initialLocationClaimed = false

    fun onTitleChanged(value: String) = _state.update { it.copy(title = value, errorMessage = null) }

    fun onDescriptionChanged(value: String) =
        _state.update { it.copy(description = value, errorMessage = null) }

    fun onPhotoTaken(file: File) =
        _state.update { it.copy(photoFile = file, uploadedPhotoUrl = null, errorMessage = null) }

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
        // both reach here. `saved` covers the other window: the screen navigates away from a
        // `LaunchedEffect` on it, and until that runs `submitting` is already false again.
        if (current.submitting || current.saved) return

        val error = when {
            current.title.isBlank() || current.description.isBlank() ->
                "Preencha o título e a descrição."
            current.photoFile == null ->
                "Tire uma foto do fenômeno antes de salvar."
            current.coordinates == null ->
                "Não foi possível obter sua localização. Ative o GPS e tente de novo."
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
            val photoUrl = current.uploadedPhotoUrl ?: run {
                val uploaded = captures.uploadPhoto(photoFile)
                if (uploaded.isFailure) {
                    _state.update {
                        it.copy(submitting = false, errorMessage = "Falha ao enviar a foto. Tente de novo.")
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
                _state.update {
                    it.copy(
                        submitting = false,
                        errorMessage = "A foto foi trocada durante o envio. Toque em Salvar de novo."
                    )
                }
                return@launch
            }

            val request = CreateWeatherEventRequest(
                title = current.title,
                description = current.description,
                photoUrl = photoUrl,
                latitude = coordinates.latitude,
                longitude = coordinates.longitude
            )

            captures.create(request)
                .onSuccess { _state.update { s -> s.copy(submitting = false, saved = true) } }
                .onFailure {
                    _state.update { s ->
                        s.copy(submitting = false, errorMessage = "Falha ao salvar o registro. Tente de novo.")
                    }
                }
        }
    }
}
